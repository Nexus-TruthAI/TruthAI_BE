package jpabasic.truthaiserver.service;

import org.springframework.transaction.annotation.Transactional;
import jpabasic.truthaiserver.domain.Answer;
import jpabasic.truthaiserver.domain.Claim;
import jpabasic.truthaiserver.domain.Source;
import jpabasic.truthaiserver.dto.CrossCheckResponseDto;
import jpabasic.truthaiserver.dto.LLMResultDto;
import jpabasic.truthaiserver.dto.CrossCheckListDto;
import jpabasic.truthaiserver.repository.AnswerRepository;
import jpabasic.truthaiserver.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.net.HttpURLConnection;
import java.net.URL;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrossCheckService {

    private final AnswerRepository answerRepository;
    private final ClaimRepository claimRepository;
    private final EmbeddingService embeddingService;

    // 임계값(필요하면 yml로 뺄 것)
    private static final double SENTENCE_SIM_THRESHOLD = 0.82;

    @Transactional
    public CrossCheckResponseDto crossCheckPrompt(Long promptId) {
        // 1) 프롬프트의 모델별 답변 수집
        List<Answer> answers = answerRepository.findByPromptId(promptId);
        Map<String, List<String>> modelToSentences = new HashMap<>();

        for (Answer answer : answers) {
//            log.info("answer: ", answer);
            modelToSentences.put(
                    answer.getModel().name(),
                    splitToSentences(answer.getContent())
            );
        }

        // 2) 전체 문장 유니크 셋
        Set<String> universe = getAllUniqueSentences(modelToSentences);

        // 3) 각 모델이 해당 문장을 '포함한다'고 볼지(임베딩 유사도로 판단)
        List<Claim> savedClaims = new ArrayList<>();
        for (String pivot : universe) {
            float[] pivotEmb = embeddingService.embed(pivot);
            for (Answer answer : answers) {
                boolean contains = modelToSentences.get(answer.getModel().name())
                        .stream()
                        .anyMatch(s -> {
                            double sim = EmbeddingService.cosine(pivotEmb, embeddingService.embed(s));
                            return sim >= SENTENCE_SIM_THRESHOLD;
                        });
                if (contains) {
                    Claim claim = new Claim(pivot, 1.0f, answer);
                    savedClaims.add(claimRepository.save(claim));
//                    log.info("claim: ", claim);
                }
            }
        }

        // 모델 -> score/opinion 임시 보관
        Map<String, LLMResultDto> byModel = new HashMap<>();

        // 4) 점수/의견 산출
        List<LLMResultDto> resultList = new ArrayList<>();
        for (String model : modelToSentences.keySet()) {
            double score = calculateScore(model, modelToSentences);
            double validUrlRatio = evalSource(savedClaims);
            String opinion = generateOpinion(score, validUrlRatio);

            LLMResultDto dto = new LLMResultDto(model, opinion, score);
            resultList.add(new LLMResultDto(model, opinion, score));
            byModel.put(model, dto);
        }

        // 계산 결과를 해당 프롬프트의 Answer 엔티티에 반영
        for (Answer a : answers) {
            LLMResultDto dto = byModel.get(a.getModel().name());
            if (dto != null) {
                a.updateOpinionAndScore(dto.getOpinion(), (float) dto.getScore());
            }
        }

        // JPA 더티체킹으로도 되지만, 확실히 하려면:
        answerRepository.saveAll(answers);
        answerRepository.flush();

        return new CrossCheckResponseDto(promptId, resultList);
    }

    /**
     * 한국어까지 고려한 간단한 문장 분리
     */
    private List<String> splitToSentences(String content) {
        if (content == null || content.isBlank()) return List.of();
        // 마침표/물음표/느낌표/줄바꿈/한중일 마침표 포함
        String[] arr = content.split("(?<=[.!?。！？…])\\s+|\\n+");
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            String t = s.trim();
            if (t.length() >= 2) out.add(t);
        }
        return out;
    }

    private Set<String> getAllUniqueSentences(Map<String, List<String>> map) {
        return map.values().stream().flatMap(List::stream).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * URL 유효성 평가 (중복 제거, HEAD 시도)
     */
    private double evalSource(List<Claim> claims) {
        Set<String> urls = new LinkedHashSet<>();
        for (Claim c : claims) {
            List<Source> sources = c.getSources();
            if (sources == null) continue;
            for (Source s : sources) {
                if (s.getSourceUrl() != null && !s.getSourceUrl().isBlank()) {
                    urls.add(s.getSourceUrl().trim());
                }
            }
        }
        if (urls.isEmpty()) return 0.0;

        long ok = 0;
        for (String u : urls) {
            if (isReachableURL(u)) ok++;
        }
        return ok / (double) urls.size();
    }

    private boolean isReachableURL(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            return (code >= 200 && code < 400);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 모델 점수 계산:
     * 1) target 모델의 각 문장 임베딩과
     * 2) 다른 모델들의 "문서 임베딩"(= 그 모델의 모든 문장 임베딩 평균)
     * 간 코사인 유사도를 구해 평균 → 그 문장의 점수
     * 최종 점수 = 해당 모델의 문장 점수들의 평균
     */
    private double calculateScore(String targetModel, Map<String, List<String>> modelToSentences) {
        List<String> targetSentences = modelToSentences.get(targetModel);
        if (targetSentences == null || targetSentences.isEmpty()) return 0.0;

        // 1) 다른 모델들의 문서 임베딩을 미리 계산 (한 번만)
        Map<String, float[]> otherModelDocEmb = new HashMap<>();
        for (Map.Entry<String, List<String>> e : modelToSentences.entrySet()) {
            String model = e.getKey();
            if (model.equals(targetModel)) continue;

            List<String> sents = e.getValue();
            if (sents == null || sents.isEmpty()) continue;

            // 문서 임베딩 = 문장 임베딩들의 평균
            List<float[]> sentEmbs = new ArrayList<>(sents.size());
            for (String s : sents) {
                sentEmbs.add(embeddingService.embed(s));
            }
            // 평균(차원 맞춤)
            int dim = embeddingService.getDim();
            float[] docEmb = new float[dim];
            if (!sentEmbs.isEmpty()) {
                for (float[] v : sentEmbs) {
                    for (int i = 0; i < dim; i++) docEmb[i] += v[i];
                }
                float inv = 1f / sentEmbs.size();
                for (int i = 0; i < dim; i++) docEmb[i] *= inv;
            }
            otherModelDocEmb.put(model, docEmb);
        }

        if (otherModelDocEmb.isEmpty()) return 0.0;

        // 2) 타겟 모델의 각 문장 점수 = (다른 모델 문서 임베딩들과의 유사도) 평균
        double totalSentenceScore = 0.0;
        int sentenceCount = 0;

        for (String sent : targetSentences) {
            float[] sentEmb = embeddingService.embed(sent);

            double sumSim = 0.0;
            int pairCnt = 0;
            for (Map.Entry<String, float[]> e : otherModelDocEmb.entrySet()) {
                double sim = EmbeddingService.cosine(sentEmb, e.getValue());
                sumSim += sim;
                pairCnt++;
            }
            double sentenceScore = (pairCnt > 0) ? (sumSim / pairCnt) : 0.0;

            // (옵션) 디버그 로그
            log.info("Score debug | model={} | sentence='{}' | pairs={} | avg={}",
                    targetModel, sent, pairCnt, sentenceScore);

            totalSentenceScore += sentenceScore;
            sentenceCount++;
        }

        return (sentenceCount > 0) ? (totalSentenceScore / sentenceCount) : 0.0;
    }

    private String generateOpinion(double score, double validUrlRatio) {
        StringBuilder sb = new StringBuilder();
        if (score >= 1.0) sb.append("완벽");
        else if (score >= 0.8) sb.append("환각 의심 낮음");
        else if (score >= 0.5) sb.append("환각 의심 높음, 정확도 낮음");
        else sb.append("환각 가능성 높음");

        if (validUrlRatio >= 1.0) sb.append(" | 모든 출처 유효");
        else if (validUrlRatio >= 0.7) sb.append(" | 다수 출처 유효");
        else if (validUrlRatio > 0.0) sb.append(" | 유효하지 않은 출처 포함");
        else sb.append(" | 출처 없음/전부 무효");

        return sb.toString();
    }


    @Transactional(readOnly = true)
    public List<CrossCheckListDto> getCrossChecklist(Long promptId) {
        List<Answer> answers;
//        }
        answers = answerRepository.findByPromptId(promptId);

        return answers.stream()
                .map(answer -> new CrossCheckListDto(
                        answer.getId(),
                        answer.getPrompt().getId(),
                        answer.getModel().name(),
                        answer.getContent(),
                        answer.getOpinion(),
                        answer.getScore()
                ))
                .collect(Collectors.toList());
    }
}
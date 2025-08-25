package jpabasic.truthaiserver.service;

import jpabasic.truthaiserver.domain.Prompt;
import jpabasic.truthaiserver.dto.prompt.sidebar.SideBarPromptListDto;
import org.springframework.transaction.annotation.Transactional;
import jpabasic.truthaiserver.domain.Answer;
import jpabasic.truthaiserver.domain.Claim;
import jpabasic.truthaiserver.domain.Source;
import jpabasic.truthaiserver.dto.crosscheck.CrossCheckListDto;
import jpabasic.truthaiserver.dto.crosscheck.CrossCheckModelDto;
import jpabasic.truthaiserver.dto.crosscheck.CrossCheckReferenceDto;
import jpabasic.truthaiserver.dto.crosscheck.CrossCheckResponseDto;
import jpabasic.truthaiserver.repository.AnswerRepository;
import jpabasic.truthaiserver.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jpabasic.truthaiserver.repository.PromptRepository;
import org.springframework.data.domain.PageRequest;

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
    private final PromptRepository promptRepository;
    private final EmbeddingService embeddingService;
    private final jpabasic.truthaiserver.repository.SourceRepository sourceRepository;

    // 임계값(필요하면 yml로 뺄 것)
    private static final double SENTENCE_SIM_THRESHOLD = 0.82;
    private static final int TOP_K = 3;

    // 간단한 한국어 불용어/조사 처리용 상수
    private static final java.util.Set<String> KOREAN_STOPWORDS = java.util.Set.of(
            "그리고", "그러나", "하지만", "또한", "또", "또는", "및", "등",
            "이", "그", "저", "것", "거", "수", "등등", "때문", "때문에",
            "에서", "대한", "관련", "통한", "위한", "하는", "하였다", "한다", "하다",
            "있다", "없다", "이다", "였다", "된다", "됐다", "같다"
    );

    private static final String[] KOREAN_PARTICLE_SUFFIXES = new String[]{
            "은", "는", "이", "가", "을", "를", "과", "와", "로", "으로", "에", "에서",
            "에게", "께", "보다", "부터", "까지", "만", "이나", "나", "라도", "조차", "마저",
            "만큼", "처럼", "으로서", "으로써", "마다"
    };

    @Transactional
    public CrossCheckResponseDto crossCheckPrompt(Long promptId) {
        // 1) 프롬프트의 모델별 답변 수집
        List<Answer> answers = answerRepository.findByPromptId(promptId);
        Map<String, List<String>> modelToSentences = new HashMap<>();

        for (Answer answer : answers) {
            modelToSentences.put(
                    answer.getModel().name(),
                    splitToSentences(answer.getContent())
            );
        }

        // 2) 전체 문장 유니크 셋
        Set<String> universe = getAllUniqueSentences(modelToSentences);

        // 공통 주장 후보 추출을 위한 최빈(가장 많은 모델이 동의) 문장 선택
        String coreStatement = null;
        int coreAgreeCount = -1;

        // 3) 각 모델이 해당 문장을 '포함한다'고 볼지(임베딩 유사도로 판단)
        List<Claim> savedClaims = new ArrayList<>();
        for (String pivot : universe) {
            float[] pivotEmb = embeddingService.embed(preprocessForEmbedding(pivot));
            int agreeCount = 0;
            for (Answer answer : answers) {
                boolean contains = modelToSentences.get(answer.getModel().name())
                        .stream()
                        .anyMatch(s -> {
                            double sim = EmbeddingService.cosine(pivotEmb, embeddingService.embed(preprocessForEmbedding(s)));
                            return sim >= SENTENCE_SIM_THRESHOLD;
                        });
                if (contains) {
                    agreeCount++;
                    Claim claim = new Claim(pivot, 1.0f, answer);
                    savedClaims.add(claimRepository.save(claim));
                }
            }
            if (agreeCount > coreAgreeCount) {
                coreAgreeCount = agreeCount;
                coreStatement = pivot;
            }
        }

        // 모델별 점수 계산 및 레퍼런스 수집
        Map<String, Double> modelToScore = new HashMap<>();
        Map<String, Double> modelToSourceQuality = new HashMap<>();
        
        for (String model : modelToSentences.keySet()) {
            double score = calculateScore(model, modelToSentences);
            double sourceQuality = calculateSourceQuality(model, answers);
            
            modelToScore.put(model, score);
            modelToSourceQuality.put(model, sourceQuality);
        }

        // 계산 결과를 해당 프롬프트의 Answer 엔티티에 반영
        for (Answer a : answers) {
            Double score = modelToScore.get(a.getModel().name());
            Double sourceQuality = modelToSourceQuality.get(a.getModel().name());
            
            if (score != null && sourceQuality != null) {
                int hallucinationLevel = calculateHallucinationLevel(score, sourceQuality);
                a.updateScoreAndLevel(score.floatValue(), hallucinationLevel);
            }
        }

        // JPA 더티체킹으로도 되지만, 확실히 하려면:
        answerRepository.saveAll(answers);
        answerRepository.flush();

        // 프롬프트 요약으로 coreTitle 설정 (없으면 원문 프롬프트 사용)
        String coreTitle = null;
        if (!answers.isEmpty() && answers.get(0).getPrompt() != null) {
            var prompt = answers.get(0).getPrompt();
            coreTitle = (prompt.getSummary() != null && !prompt.getSummary().isBlank())
                    ? prompt.getSummary() : prompt.getOriginalPrompt();
        }

        // 모델명 -> Answer 매핑 및 레퍼런스 구성
        Map<String, Answer> modelToAnswer = new HashMap<>();
        for (Answer a : answers) {
            modelToAnswer.put(a.getModel().name(), a);
        }

        CrossCheckModelDto gptDto = buildModelDto("GPT", modelToAnswer, modelToScore);
        CrossCheckModelDto claudeDto = buildModelDto("CLAUDE", modelToAnswer, modelToScore);
        CrossCheckModelDto geminiDto = buildModelDto("GEMINI", modelToAnswer, modelToScore);
        CrossCheckModelDto perplexityDto = buildModelDto("PERPLEXITY", modelToAnswer, modelToScore);

        return new CrossCheckResponseDto(
                coreTitle,
                coreStatement,
                gptDto,
                claudeDto,
                geminiDto,
                perplexityDto
        );
    }

    /**
     * 환각 레벨 계산 (별도 함수로 분리)
     * 점수와 출처 품질을 종합하여 환각 레벨 결정
     */
    private int calculateHallucinationLevel(double score, double sourceQuality) {
        // 점수 기반 기본 레벨
        int baseLevel;
        if (score >= 0.7) baseLevel = 0;        // 낮음
        else if (score >= 0.4) baseLevel = 1;   // 중간
        else baseLevel = 2;                      // 높음

        // 출처 품질에 따른 보정
        if (sourceQuality >= 0.5) {
            // 출처가 품질이 좋으면 레벨을 1단계 낮춤 (최소 0)
            return Math.max(0, baseLevel - 1);
        } else if (sourceQuality <= 0.3) {
            // 출처 품질이 낮으면 레벨을 1단계 높임 (최대 2)
            return Math.min(2, baseLevel + 1);
        }

        return baseLevel;
    }

    /**
     * 출처 품질 계산 (URL 유효성 및 출처 다양성 고려)
     */
    private double calculateSourceQuality(String modelName, List<Answer> answers) {
        Answer modelAnswer = answers.stream()
                .filter(a -> a.getModel().name().equals(modelName))
                .findFirst()
                .orElse(null);

        if (modelAnswer == null) return 0.0;

        // 해당 모델의 출처들 조회
        List<Source> sources = sourceRepository.findByAnswerId(modelAnswer.getId());
        
        if (sources.isEmpty()) return 0.5;

        // 1) URL 유효성 점수 (기존 evalSource 로직 활용)
        double urlValidityScore = evalSourceValidity(sources);
        
        // 2) 출처 다양성 점수 (중복 도메인 제거)
        double diversityScore = calculateSourceDiversity(sources);
        
        // 3) 출처 신뢰도 점수 (도메인 기반)
        double credibilityScore = calculateSourceCredibility(sources);
        
        // 종합 점수 (가중 평균)
        return urlValidityScore * 0.4 + diversityScore * 0.3 + credibilityScore * 0.3;
    }

    /**
     * URL 유효성 평가
     */
    private double evalSourceValidity(List<Source> sources) {
        Set<String> urls = sources.stream()
                .map(Source::getSourceUrl)
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toSet());

        if (urls.isEmpty()) return 0.0;

        long validUrls = urls.stream()
                .filter(this::isReachableURL)
                .count();

        return (double) validUrls / urls.size();
    }

    /**
     * 출처 다양성 점수 (중복 도메인 제거)
     */
    private double calculateSourceDiversity(List<Source> sources) {
        Set<String> domains = sources.stream()
                .map(Source::getSourceUrl)
                .filter(url -> url != null && !url.isBlank())
                .map(this::extractDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 도메인 수가 많을수록 높은 점수 (최대 1.0)
        return Math.min(1.0, domains.size() / 5.0);
    }

    /**
     * 출처 신뢰도 점수 (도메인 기반)
     */
    private double calculateSourceCredibility(List<Source> sources) {
        double totalScore = 0.0;
        int count = 0;

        for (Source source : sources) {
            if (source.getSourceUrl() != null && !source.getSourceUrl().isBlank()) {
                totalScore += getDomainCredibilityScore(source.getSourceUrl());
                count++;
            }
        }

        return count > 0 ? totalScore / count : 0.0;
    }

    /**
     * 도메인 추출
     */
    private String extractDomain(String url) {
        try {
            URL urlObj = new URL(url);
            String host = urlObj.getHost();
            // www. 제거
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 도메인별 신뢰도 점수
     */
    private double getDomainCredibilityScore(String url) {
        String domain = extractDomain(url);
        if (domain == null) return 0.5;

        // 신뢰할 수 있는 도메인들
        if (domain.contains("wikipedia.org") || 
            domain.contains("ac.kr") || 
            domain.contains("edu") ||
            domain.contains("gov") ||
            domain.contains("org")) {
            return 1.0;
        }
        
        // 일반적인 뉴스/블로그 사이트
        if (domain.contains("news") || 
            domain.contains("blog") || 
            domain.contains("medium.com")) {
            return 0.7;
        }

        // 기타 사이트
        return 0.5;
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
//    private double evalSource(List<Claim> claims) {
//        Set<String> urls = new LinkedHashSet<>();
//        for (Claim c : claims) {
//            List<Source> sources = c.getSources();
//            if (sources == null) continue;
//            for (Source s : sources) {
//                if (s.getSourceUrl() != null && !s.getSourceUrl().isBlank()) {
//                    urls.add(s.getSourceUrl().trim());
//                }
//            }
//        }
//        if (urls.isEmpty()) return 0.0;
//
//        long ok = 0;
//        for (String u : urls) {
//            if (isReachableURL(u)) ok++;
//        }
//        return ok / (double) urls.size();
//    }

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
     * 모델 점수 계산(개선):
     * - 타겟 모델의 각 문장 임베딩과 "다른 모델들의 모든 문장 임베딩" 간 코사인 유사도를 계산
     * - 해당 문장의 점수 = 그 중 Top-K 평균
     * - 최종 점수 = 타겟 모델 문장 점수들의 평균
     */
    private double calculateScore(String targetModel, Map<String, List<String>> modelToSentences) {
        List<String> targetSentences = modelToSentences.get(targetModel);
        if (targetSentences == null || targetSentences.isEmpty()) return 0.0;

        // 1) 다른 모델들의 모든 문장 임베딩 수집
        List<float[]> otherSentenceEmbeddings = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : modelToSentences.entrySet()) {
            String modelName = entry.getKey();
            if (modelName.equals(targetModel)) continue;

            List<String> sentences = entry.getValue();
            if (sentences == null || sentences.isEmpty()) continue;
            for (String s : sentences) {
                otherSentenceEmbeddings.add(embeddingService.embed(preprocessForEmbedding(s)));
            }
        }

        if (otherSentenceEmbeddings.isEmpty()) return 0.0;

        // 2) 타겟 모델의 각 문장 점수 = (타 모델 모든 문장과의 유사도) 중 Top-K 평균
        double totalSentenceScore = 0.0;
        int sentenceCount = 0;

        for (String sentence : targetSentences) {
            float[] targetEmbedding = embeddingService.embed(preprocessForEmbedding(sentence));
            java.util.List<Double> sims = new java.util.ArrayList<>(otherSentenceEmbeddings.size());
            for (float[] otherEmbedding : otherSentenceEmbeddings) {
                double sim = EmbeddingService.cosine(targetEmbedding, otherEmbedding);
                sims.add(sim);
            }
            if (sims.isEmpty()) continue;
            sims.sort(java.util.Collections.reverseOrder());
            int k = Math.min(TOP_K, sims.size());
            double sumTopK = 0.0;
            for (int i = 0; i < k; i++) sumTopK += sims.get(i);
            double sentenceScore = sumTopK / k;

            // 디버그 로그
            log.info("Score debug | model={} | sentence='{}' | compared={} | topK_avg={}",
                    targetModel, sentence, otherSentenceEmbeddings.size(), sentenceScore);

            totalSentenceScore += sentenceScore;
            sentenceCount++;
        }

        return (sentenceCount > 0) ? (totalSentenceScore / sentenceCount) : 0.0;
    }

    private String preprocessForEmbedding(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("[^가-힣a-zA-Z0-9\\s]", " ");
        String[] rawTokens = cleaned.trim().split("\\s+");
        java.util.List<String> tokens = new java.util.ArrayList<>();
        for (String token : rawTokens) {
            if (token.isBlank()) continue;
            String lowered = token.toLowerCase();
            if (KOREAN_STOPWORDS.contains(lowered)) continue;
            String stripped = stripKoreanParticles(token);
            if (stripped.length() <= 1) continue;
            tokens.add(stripped);
        }
        return String.join(" ", tokens);
    }

    private String stripKoreanParticles(String token) {
        for (String suffix : KOREAN_PARTICLE_SUFFIXES) {
            if (token.length() > suffix.length() && token.endsWith(suffix)) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }

    private CrossCheckModelDto buildModelDto(
            String modelName,
            Map<String, Answer> modelToAnswer,
            Map<String, Double> modelToScore
    ) {
        Answer answer = modelToAnswer.get(modelName);
        if (answer == null) return null;

        java.util.List<Source> sources = sourceRepository.findByAnswerId(answer.getId());
        java.util.List<CrossCheckReferenceDto> references = sources.stream()
                .map(s -> new CrossCheckReferenceDto(
                        s.getSourceTitle(),
                        s.getSourceSummary(),
                        s.getSourceUrl()
                ))
                .collect(java.util.stream.Collectors.toList());

        Double scoreOrNull = modelToScore.get(modelName);
        double score = scoreOrNull != null ? scoreOrNull : 0.0;
        int similarityPercent = (int) Math.round(Math.max(0.0, Math.min(1.0, score)) * 100.0);

        // Answer에 저장된 level 사용
        int hallucinationLevel = answer.getLevel() != null ? answer.getLevel() : 2;

        return new CrossCheckModelDto(hallucinationLevel, similarityPercent, references);
    }

    @Transactional(readOnly = true)
    public CrossCheckResponseDto getCrossChecklist(Long promptId) {
        List<Answer> answers = answerRepository.findByPromptId(promptId);
        
        if (answers.isEmpty()) {
//            return new CrossCheckResponseDto(
//                    null, null, null, null, null, null
//            );
            return crossCheckPrompt(promptId);
        }

        // 프롬프트 정보로 coreTitle 설정
        String coreTitle = null;
        if (answers.get(0).getPrompt() != null) {
            var prompt = answers.get(0).getPrompt();
            coreTitle = (prompt.getSummary() != null && !prompt.getSummary().isBlank())
                    ? prompt.getSummary() : prompt.getOriginalPrompt();
        }

        // coreStatement는 가장 높은 점수를 가진 답변의 첫 번째 문장으로 설정
        String coreStatement = null;
        Answer bestAnswer = answers.stream()
                .filter(a -> a.getScore() != null)
                .max(Comparator.comparing(Answer::getScore))
                .orElse(null);
        
        if (bestAnswer != null && bestAnswer.getContent() != null) {
            List<String> sentences = splitToSentences(bestAnswer.getContent());
            if (!sentences.isEmpty()) {
                coreStatement = sentences.get(0);
            }
        }

        // 모델별 DTO 생성
        CrossCheckModelDto gptDto = buildModelDtoFromAnswer("GPT", answers);
        CrossCheckModelDto claudeDto = buildModelDtoFromAnswer("CLAUDE", answers);
        CrossCheckModelDto geminiDto = buildModelDtoFromAnswer("GEMINI", answers);
        CrossCheckModelDto perplexityDto = buildModelDtoFromAnswer("PERPLEXITY", answers);

        return new CrossCheckResponseDto(
                coreTitle,
                coreStatement,
                gptDto,
                claudeDto,
                geminiDto,
                perplexityDto
        );
    }

    /**
     * 저장된 Answer 데이터로부터 CrossCheckModelDto 생성
     */
    private CrossCheckModelDto buildModelDtoFromAnswer(String modelName, List<Answer> answers) {
        Answer answer = answers.stream()
                .filter(a -> a.getModel().name().equals(modelName))
                .findFirst()
                .orElse(null);

        if (answer == null) return null;

        // 출처 정보 조회
        List<Source> sources = sourceRepository.findByAnswerId(answer.getId());
        List<CrossCheckReferenceDto> references = sources.stream()
                .map(s -> new CrossCheckReferenceDto(
                        s.getSourceTitle(),
                        s.getSourceSummary(),
                        s.getSourceUrl()
                ))
                .collect(Collectors.toList());

        // 저장된 점수와 레벨 사용
        Float score = answer.getScore();
        double scoreValue = score != null ? score : 0.0;
        int similarityPercent = (int) Math.round(Math.max(0.0, Math.min(1.0, scoreValue)) * 100.0);
        
        Integer level = answer.getLevel();
        int hallucinationLevel = level != null ? level : 2;

        return new CrossCheckModelDto(hallucinationLevel, similarityPercent, references);
    }

    @Transactional
    //사이드바 리스트 조회
    public List<SideBarPromptListDto> checkSideBar(Long userId){

        List<Prompt> list=promptRepository.findPromptsWithAnswersAndScoreNull(userId, PageRequest.of(0, 5));
        List<SideBarPromptListDto> dtoList=new ArrayList<>();

        for(Prompt one:list){
            dtoList.add(SideBarPromptListDto.toDto(one));
        }
        return dtoList;
    }
}
package jpabasic.truthaiserver.service;

import jakarta.transaction.Transactional;
import jpabasic.truthaiserver.domain.Answer;
import jpabasic.truthaiserver.domain.Claim;
import jpabasic.truthaiserver.dto.CrossCheckResponseDto;
import jpabasic.truthaiserver.dto.LLMResultDto;
import jpabasic.truthaiserver.repository.AnswerRepository;
import jpabasic.truthaiserver.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CrossCheckService {
    private final AnswerRepository answerRepository;
    private final ClaimRepository claimRepository;

    @Transactional
    public CrossCheckResponseDto crossCheckPrompt(Long promptId) {
        // 질문의 답변들(모델별)을 리스트로 가져옴
        List<Answer> answers = answerRepository.findByPromptId(promptId);
        Map<String, List<String>> modelToSentences = new HashMap<>();

        // 각 답변을 문장별로 나눈 뒤 맵에 저장
        for (Answer answer : answers) {
            modelToSentences.put(
                    answer.getModel().name(),
                    splitToSentences(answer.getContent())
            );
        }

        List<Claim> savedClaims = new ArrayList<>();
        for (String sentence : getALLUniqueSentences(modelToSentences)) {
            for (Answer answer : answers) {
                boolean contains = modelToSentences.get(answer.getModel().name())
                        .stream()
                        .anyMatch(s -> similarity(s, sentence) > 0.8);
                if (contains) {
                    Claim claim = new Claim(sentence, 1.0f, answer);
                    savedClaims.add(claimRepository.save(claim));
                }
            }
        }

        List<LLMResultDto> resultList = new ArrayList<>();
        for (String model : modelToSentences.keySet()) {
            double score = calculateScore(model, savedClaims);
            String opinion = generateOpinion(score);
            resultList.add(new LLMResultDto(model, opinion, score));
        }

        return new CrossCheckResponseDto(promptId, resultList);
    }

    private List<String> splitToSentences(String content) {
        return Arrays.stream(content.split("(?<=[.!?])\\s+")).toList();
    }

    private Set<String> getALLUniqueSentences(Map<String, List<String>> map){
        return map.values().stream().flatMap(List::stream).collect(Collectors.toSet());
    }

    private double similarity(String s1, String s2) {
        // 간단한 유사도 예시 (Jaccard)
        Set<String> set1 = new HashSet<>(Arrays.asList(s1.split(" ")));
        Set<String> set2 = new HashSet<>(Arrays.asList(s2.split(" ")));
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        return (double) intersection.size() / union.size();
    }

    private double calculateScore(String model, List<Claim> claims) {
        long total = claims.stream().map(Claim::getAnswer).map(a -> a.getModel().name()).distinct().count();
        long count = claims.stream()
                .filter(c -> c.getAnswer().getModel().name().equals(model))
                .count();
        return (double) count / total;
    }

    private String generateOpinion(double score) {
        if (score >= 1.0) return "완벽";
        else if (score >= 0.8) return "환각 의심 낮음";
        else if (score >= 0.5) return "환각 의심 높음, 정확도 낮음";
        else return "환각 가능성 높음";
    }
}

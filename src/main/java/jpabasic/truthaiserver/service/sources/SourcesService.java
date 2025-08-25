package jpabasic.truthaiserver.service.sources;

import jpabasic.truthaiserver.domain.Answer;
import jpabasic.truthaiserver.domain.Source;
import jpabasic.truthaiserver.dto.prompt.LLMResponseDto;
import jpabasic.truthaiserver.dto.sources.SourcesDto;
import jpabasic.truthaiserver.exception.BusinessException;
import jpabasic.truthaiserver.repository.AnswerRepository;
import jpabasic.truthaiserver.repository.SourceRepository;
import jpabasic.truthaiserver.service.AnswerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static jpabasic.truthaiserver.exception.ErrorMessages.ANSWER_NOT_FOUND;

@Service
@Slf4j
public class SourcesService {

    private final SourceRepository sourceRepository;
    private final AnswerRepository answerRepository;

    public SourcesService(SourceRepository sourceRepository, AnswerRepository answerRepository) {
        this.sourceRepository = sourceRepository;
        this.answerRepository = answerRepository;
    }




    //Sources 저장
    public List<SourcesDto> saveSources(LLMResponseDto dto, Long answerId) {


        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new BusinessException(ANSWER_NOT_FOUND));

        List<LLMResponseDto.SourceResponseDto> sources=dto.sources();
        log.debug("sources" + dto.toString());
        List<SourcesDto> sourcesDtos = new ArrayList<>();

        for (LLMResponseDto.SourceResponseDto s : sources) {
            Source source=new Source(s.title(),s.url(),answer);
            sourceRepository.save(source);

            SourcesDto result=SourcesDto.toDto(source);
            sourcesDtos.add(result);
        }

        return sourcesDtos;
    }
}

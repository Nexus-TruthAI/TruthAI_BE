package jpabasic.truthaiserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jpabasic.truthaiserver.domain.User;
import jpabasic.truthaiserver.dto.answer.LlmRequestDto;
import jpabasic.truthaiserver.dto.prompt.PromptAnswerDto;
import jpabasic.truthaiserver.service.LlmService;
import jpabasic.truthaiserver.service.SourcesService;
import jpabasic.truthaiserver.service.prompt.PromptService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name="프롬프트 관련 api")
@RequestMapping("/prompt")
public class PromptController {

    private final PromptService promptService;
    private final LlmService llmService;
    private final SourcesService sourcesService;

    public PromptController(PromptService promptService,LlmService llmService,SourcesService sourcesService)
    {
        this.promptService = promptService;
        this.llmService = llmService;
        this.sourcesService = sourcesService;
    }

    @PostMapping("/create-best")
    @Operation(summary="최적화 프롬프트 생성")
    public ResponseEntity<Map<String,Object>> savePrompt(@RequestBody LlmRequestDto dto,@AuthenticationPrincipal(expression = "user") User user){
        Long promptId=promptService.saveOriginalPrompt(dto,user);
        String optimizedPrompt=promptService.getOptimizedPrompt(dto,promptId);

        Map<String,Object> map=new HashMap<>();
        map.put("optimizedPrompt",optimizedPrompt);
        map.put("promptId",promptId);
        return ResponseEntity.ok(map); //저장된 promptId도 함께 반환.
    }

//    @PostMapping("/get-best")
//    @Operation(summary="최적화 프롬프트를 통해 응답 생성 받기")
//    public ResponseEntity<String> getOptimizedAnswer(@RequestBody LlmRequestDto dto, @AuthenticationPrincipal User user){
//
//        String optimizedPrompt=promptService.optimizingPrompt(dto);
//        return ResponseEntity.ok(optimizedPrompt);
//    }

    @PostMapping("/get-best/organized")
    @Operation(summary="최적화 프롬프트를 통해 응답 생성 받기",description = "현재는 아직 gpt만 가능합니다.")
    public ResponseEntity<PromptAnswerDto> getOrganizedAnswer(@RequestParam Long promptId,
                                                              @RequestBody LlmRequestDto dto,
                                                              @AuthenticationPrincipal User user){
        String optimizedPrompt=promptService.optimizingPrompt(dto,user,promptId);
        PromptAnswerDto result=llmService.seperateAnswers(promptId,optimizedPrompt);
//        sourcesService.saveSources(result); //출처 저장은 ai 교차 검증 시에 하는걸로..
        return ResponseEntity.ok(result);
    }

    @PostMapping("/summarize")
    @Operation(summary="프롬프트 내용 요약하기",description="model 필드 값은 gpt로 주세요!")
    public ResponseEntity<String> summarizePrompt(@RequestBody LlmRequestDto dto){
        String prompt=dto.getQuestion();
        String result=promptService.summarizePrompts(prompt);
        return ResponseEntity.ok(result);
    }



}

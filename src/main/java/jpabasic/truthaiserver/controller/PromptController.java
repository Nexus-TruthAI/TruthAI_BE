package jpabasic.truthaiserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jpabasic.truthaiserver.domain.LLMModel;
import jpabasic.truthaiserver.domain.Prompt;
import jpabasic.truthaiserver.domain.PromptDomain;
import jpabasic.truthaiserver.domain.User;
import jpabasic.truthaiserver.dto.answer.LlmAnswerDto;
import jpabasic.truthaiserver.dto.answer.LlmRequestDto;
import jpabasic.truthaiserver.dto.answer.Message;
import jpabasic.truthaiserver.dto.prompt.*;
import jpabasic.truthaiserver.dto.prompt.sidebar.SideBarPromptDto;
import jpabasic.truthaiserver.dto.prompt.sidebar.SideBarPromptListDto;
import jpabasic.truthaiserver.service.LlmService;
import jpabasic.truthaiserver.service.sources.SourcesService;
import jpabasic.truthaiserver.service.prompt.PromptService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
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

    @GetMapping("/side-bar/list")
    @Operation(summary="프롬프트 사이드바 리스트 조회")
    public ResponseEntity<List<SideBarPromptListDto>> checkPromptSideBar(@AuthenticationPrincipal(expression = "user") User user) {
        Long userId=user.getId();
        List<SideBarPromptListDto> result=promptService.checkSideBar(userId);
        return ResponseEntity.ok(result);
    }


    @GetMapping("/side-bar/details")
    @Operation(summary="사이드바에 저장된 프롬프트 상세 조회")
    public ResponseEntity<SideBarPromptDto> checkSideBarDetails(
            @RequestParam Long promptId
    ){
        SideBarPromptDto result=promptService.checkSideBarDetails(promptId);
        return ResponseEntity.ok(result);
    }


//    @PostMapping("/create-best")
//    @Operation(summary="최적화 프롬프트 생성",description = "templateKey 값은 optimzied로 주세요.")
//    public ResponseEntity<Map<String,Object>> savePrompt(@RequestBody OptPromptRequestDto dto, @AuthenticationPrincipal User user){
//        Long promptId=promptService.saveOriginalPrompt(dto,user);
//        List<Message> optimizedPrompt=promptService.getOptimizedPrompt(dto,promptId);
//
//        Map<String,Object> map=new HashMap<>();
//        map.put("optimizedPrompt",optimizedPrompt);
//        map.put("promptId",promptId);
//        return ResponseEntity.ok(map); //저장된 promptId도 함께 반환.
//    }

    @PostMapping("/create-best-prompt")
    @Operation(summary="최적화 프롬프트 생성 (수정 가능하도록) ",description = "templateKey 값은 editable 로 주세요.")
    public ResponseEntity<Map<String,Object>> optimizingPrompt(
            @RequestBody OptPromptRequestDto dto, @AuthenticationPrincipal(expression = "user") User user){

        List<Message> optimizedPrompt=promptService.getOptimizedPrompt(dto);

        String result = llmService.createGptAnswerWithPrompt(optimizedPrompt); //LLM 답변 받기
        System.out.println("🖥️ result:"+result);
        String summary = promptService.summarizePrompts(dto.getQuestion());

        //optimized_prompt 저장
        Long promptId=promptService.saveOptimizedPrompt(result,dto,user,summary);

        Map<String,Object> map=new HashMap<>();
        map.put("optimizedPrompt",result);
        map.put("promptId",promptId);
        return ResponseEntity.ok(map); //저장된 promptId도 함께 반환.
    }



    @PostMapping("/get-best/organized")
    @Operation(summary="최적화 프롬프트를 통해 응답 생성 받기",description = "gpt, claude 사용 가능. templateKey='optimized'로 주세요")
    public ResponseEntity<List<Map<LLMModel,PromptResultDto>>> getOrganizedAnswer(
                                                              @RequestParam Long promptId,
                                                              @RequestBody LlmRequestDto dto,
                                                              @AuthenticationPrincipal(expression = "user") User user){

        //최적화 프롬프트 받고 응답 받기
        List<Map<LLMModel,LLMResponseDto>> response=promptService.runByModel(dto);

        //응답 내용 저장
        List<Map<LLMModel, PromptResultDto>> result=promptService.saveAnswers(response,user,promptId);

        //정돈된 source로 응답
        return ResponseEntity.ok(result);
    }


    @GetMapping("/optimized-prompt-list")
    @Operation(summary="프롬프팅 결과 리스트 조회하기", description = "")
    public ResponseEntity<List<PromptListDto>> getOptimizedPromptList(@AuthenticationPrincipal(expression = "user") User user) {
        Long userId=user.getId();
        List<PromptListDto> result=promptService.getOptimizedPromptList(userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/crosscheck-list")
    @Operation(summary="교차검증(환각) 결과 리스트 조회하기", description = "")
    public ResponseEntity<List<PromptListDto>> getCrosscheckList(@AuthenticationPrincipal(expression = "user") User user) {
        Long userId=user.getId();
        List<PromptListDto> result=promptService.getCrosscheckList(userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{promptId}")
    @Operation(summary="최적화 전/후 프롬프트를 조회합니다.", description = "")
    public ResponseEntity<OptimizedPromptResultDto> getOptimizedPromptResult(@PathVariable Long promptId){
        OptimizedPromptResultDto result = promptService.getOptimizedPromptResult(promptId);
        return ResponseEntity.ok(result);
    }
}

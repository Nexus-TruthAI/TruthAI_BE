package jpabasic.truthaiserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jpabasic.truthaiserver.domain.User;
import jpabasic.truthaiserver.dto.crosscheck.CrossCheckListDto;
import jpabasic.truthaiserver.dto.crosscheck.CrossCheckResponseDto;
import jpabasic.truthaiserver.dto.prompt.sidebar.SideBarPromptListDto;
import jpabasic.truthaiserver.service.CrossCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@Tag(name = "교차검증 관련 api")
@RequestMapping("/crosscheck")
@RequiredArgsConstructor
public class CrossCheckController {
    private final CrossCheckService crossCheckService;


    @PostMapping("/{promptId}")
    @Operation(summary = "모델 답변간 비교", description = "프롬프트 ID를 전달해주시면 됩니다.")
    public CrossCheckResponseDto crossCheck(@PathVariable("promptId") Long promptId) {
        return crossCheckService.crossCheckPrompt(promptId);
    }


    @GetMapping("/{promptId}")
    @Operation(
            summary = "환각 결과 리스트",
            description = "프롬프트 ID에 해당하는 환각 검증 결과 리스트를 조회합니다."
    )
    public CrossCheckResponseDto getCrossCheckList(
            @PathVariable("promptId") Long promptId
    ) {
        return crossCheckService.getCrossChecklist(promptId);
    }

    @GetMapping("/side-bar/list")
    @Operation(summary="사이드바 교차검증 리스트 조회")
    public ResponseEntity<List<SideBarPromptListDto>> checkCrossCheckSideBar(@AuthenticationPrincipal(expression = "user") User user){
        Long userId=user.getId();
        List<SideBarPromptListDto> result=crossCheckService.checkSideBar(userId);
        return ResponseEntity.ok(result);
    }


}

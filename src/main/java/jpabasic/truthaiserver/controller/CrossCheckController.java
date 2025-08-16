package jpabasic.truthaiserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jpabasic.truthaiserver.dto.CrossCheckListDto;
import jpabasic.truthaiserver.dto.CrossCheckResponseDto;
import jpabasic.truthaiserver.service.CrossCheckService;
import jpabasic.truthaiserver.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@Tag(name = "교차검증 관련 api")
@RequestMapping("/crosscheck/{promptID}")
@RequiredArgsConstructor
public class CrossCheckController {
    private final CrossCheckService crossCheckService;


    @PostMapping
    @Operation(summary = "모델 답변간 비교", description = "프롬프트 ID를 전달해주시면 됩니다.")
    public CrossCheckResponseDto crossCheck(@PathVariable Long promptId) {
        return crossCheckService.crossCheckPrompt(promptId);
    }


    @GetMapping
    @Operation(
            summary = "교차검증 결과 리스트",
            description = "프롬프트 ID에 해당하는 교차검증 결과 리스트를 조회합니다."
    )
    public List<CrossCheckListDto> getCrossCheckList(
            @PathVariable Long promptId
    ) {
        return crossCheckService.getCrossChecklist(promptId);
    }

}

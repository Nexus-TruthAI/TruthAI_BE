package jpabasic.truthaiserver.controller;

import jpabasic.truthaiserver.dto.CrossCheckResponseDto;
import jpabasic.truthaiserver.service.CrossCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/crosscheck")
@RequiredArgsConstructor
public class CrossCheckController {
    private final CrossCheckService crossCheckService;

    @GetMapping("/{promptId}")
    public CrossCheckResponseDto crossCheck(@PathVariable Long promptId){
        return crossCheckService.crossCheckPrompt(promptId);
    }
}

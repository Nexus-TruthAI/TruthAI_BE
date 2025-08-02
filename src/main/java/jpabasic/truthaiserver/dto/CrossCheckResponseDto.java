package jpabasic.truthaiserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CrossCheckResponseDto {
    private Long promptId;
    private List<LLMResultDto> llmResultDtoList;
}

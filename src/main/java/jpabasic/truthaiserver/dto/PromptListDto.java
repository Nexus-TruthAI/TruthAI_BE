package jpabasic.truthaiserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PromptListDto {
    private Long id;
    private String originalPrompt;
    private LocalDateTime createdAt;
}

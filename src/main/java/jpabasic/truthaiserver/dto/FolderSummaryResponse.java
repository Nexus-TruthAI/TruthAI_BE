package jpabasic.truthaiserver.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FolderSummaryResponse {
    private Long id;
    private String name;
    private int promptCount;
}

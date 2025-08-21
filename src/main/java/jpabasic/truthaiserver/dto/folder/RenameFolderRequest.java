package jpabasic.truthaiserver.dto.folder;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RenameFolderRequest {
    @NotBlank
    private String name;
}

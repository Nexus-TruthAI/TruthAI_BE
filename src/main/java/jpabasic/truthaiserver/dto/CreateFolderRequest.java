package jpabasic.truthaiserver.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateFolderRequest {
    @NotNull
    private String folderName;
}

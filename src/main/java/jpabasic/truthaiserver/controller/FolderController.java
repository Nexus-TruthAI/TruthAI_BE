package jpabasic.truthaiserver.controller;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jpabasic.truthaiserver.domain.User;
import jpabasic.truthaiserver.dto.*;
import jpabasic.truthaiserver.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "폴더 관련 api")
@RequestMapping("/folder")
public class FolderController {

    private final FolderService folderService;
    @PostMapping
    @Operation(summary = "폴더 생성", description = "폴더 이름을 전달하면 해당 사용자 소유의 폴더를 생성합니다.")
    public CreateFolderResponse savePromptInFolder(@RequestBody @Valid CreateFolderRequest request, @AuthenticationPrincipal(expression = "user") User user) {
        Long id = folderService.createFolder(request, user);
        return new CreateFolderResponse(id);
    }

    // 폴더 조회
    @GetMapping
    @Operation(summary = "폴더 목록 조회")
    public List<FolderSummaryResponse> list(@AuthenticationPrincipal(expression = "user") User user) {
        return folderService.listFolders(user);
    }

    // 프롬프트를 폴더에 저장
    @PutMapping("/{folderID}/prompts/{promptId}")
    @Operation(summary = "프롬프트 폴더 저장/이동", description = "promptId의 프롬프트를 folderId 폴더로 이동/저장합니다.")
    public void movePrompt(@PathVariable Long folderID,
                           @PathVariable Long promptId){
        folderService.movePromptToFolder(folderID, promptId);
    }

    // 폴더 이름 변경
    @PatchMapping("/{folderId}")
    @Operation(summary = "폴더 이름 변경", description = "folderId 폴더의 이름을 변경합니다.")
    public void rename(@PathVariable Long folderId,
                       @RequestBody @Valid RenameFolderRequest request){
        folderService.renameFolder(folderId, request.getName());
    }

    @GetMapping("/{folderId}")
    @Operation(summary = "폴더 내 프롬프트 목록 조회", description = "folderId 폴더에 저장된 프롬프트 리스트를 반환합니다.")
    public List<PromptListDto> getPromptsInForder(
            @PathVariable Long folderId
    ){
        return folderService.getPromptsInFolder(folderId);
    }
}

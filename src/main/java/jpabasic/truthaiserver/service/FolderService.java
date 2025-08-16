package jpabasic.truthaiserver.service;

import jakarta.transaction.Transactional;
import jpabasic.truthaiserver.domain.Prompt;
import jpabasic.truthaiserver.domain.User;
import jpabasic.truthaiserver.domain.Folder;
import jpabasic.truthaiserver.dto.CreateFolderRequest;
import jpabasic.truthaiserver.dto.FolderSummaryResponse;
import jpabasic.truthaiserver.dto.PromptListDto;
import jpabasic.truthaiserver.dto.RenameFolderRequest;
import jpabasic.truthaiserver.repository.FolderRepository;
import jpabasic.truthaiserver.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class FolderService {
    private final FolderRepository folderRepository;
    private final PromptRepository promptRepository;

    // 폴더 생성
    @Transactional
    public Long createFolder(CreateFolderRequest request, User user) {
        Folder folder = new Folder();
        folder.assignUser(user);
        folder.rename(request.getFolderName());

        Folder saved = folderRepository.save(folder);
        return saved.getId();
    }

    // 폴더 목록 조회
    @Transactional
    public List<FolderSummaryResponse> listFolders(User user) {
        Long userId = user.getId();
        return folderRepository.findByUserId(userId).stream()
                .map(f -> new FolderSummaryResponse(
                        f.getId(),
                        f.getName(),
                        f.getPrompts() == null ? 0 : f.getPrompts().size()
                ))
                .toList();

    }

    // 프롬프트를 폴더에 저장, 또는 이동
    @Transactional
    public void movePromptToFolder(Long folderId, Long promptId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new NoSuchElementException("폴더를 찾을 수 없습니다."));
        Prompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new NoSuchElementException("프롬프트를 찾을 수 없습니다."));

        // 이동
        prompt.assignFolder(folder);
    }

    @Transactional
    public void renameFolder(Long folderId, String newName){
        Folder folder = folderRepository.findById((folderId))
                .orElseThrow(() -> new NoSuchElementException("폴더를 찾을 수 없습니다."));
        folder.rename(newName);
    }

    @Transactional
    public List<PromptListDto> getPromptsInFolder(Long folderId){
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new IllegalArgumentException("폴더가 존재하지 않습니다."));

        // 최신순으로 프롬프트 조회
        List<Prompt> prompts = promptRepository.findByFolderIdOrderByCreatedAtDesc(folderId);

        List<PromptListDto> list = new ArrayList<>(prompts.size());
        for (Prompt p : prompts){
            list.add(new PromptListDto(
                    p.getId(),
                    p.getOriginalPrompt(),
                    p.getCreatedAt()
            ));
        }
        return list;
    }
}
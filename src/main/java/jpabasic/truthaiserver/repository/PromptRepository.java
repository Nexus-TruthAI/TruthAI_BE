package jpabasic.truthaiserver.repository;

import jpabasic.truthaiserver.domain.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, Long> {
    Optional<Prompt> findByIdAndUserId(Long id, Long userId);
    List<Prompt> findByFolderIdOrderByCreatedAtDesc(Long folderId);
}

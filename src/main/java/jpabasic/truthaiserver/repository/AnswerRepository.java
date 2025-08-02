package jpabasic.truthaiserver.repository;

import jpabasic.truthaiserver.domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByPromptId(Long promptId);
}

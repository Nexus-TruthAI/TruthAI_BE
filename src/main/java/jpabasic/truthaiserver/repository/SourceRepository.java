package jpabasic.truthaiserver.repository;

import jpabasic.truthaiserver.domain.Source;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, Long> {
}

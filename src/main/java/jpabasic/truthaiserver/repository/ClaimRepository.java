package jpabasic.truthaiserver.repository;

import jpabasic.truthaiserver.domain.Claim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

}

package edu.comp5100.election.repository;

import edu.comp5100.election.domain.Candidate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findByElectionIdOrderByIdAsc(Long electionId);

    Optional<Candidate> findByIdAndElectionId(Long id, Long electionId);

    boolean existsByIdAndElectionId(Long id, Long electionId);

    long countByElectionId(Long electionId);
}

package edu.comp5100.election.repository;

import edu.comp5100.election.domain.ElectionEligibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectionEligibilityRepository extends JpaRepository<ElectionEligibility, Long> {

    Optional<ElectionEligibility> findByElectionIdAndVoterId(Long electionId, Long voterId);

    boolean existsByElectionIdAndVoterIdAndActiveTrue(Long electionId, Long voterId);

    boolean existsByElectionIdAndVoterId(Long electionId, Long voterId);

    List<ElectionEligibility> findByElectionId(Long electionId);

    List<ElectionEligibility> findByVoterId(Long voterId);

    long countByElectionIdAndActiveTrue(Long electionId);
}

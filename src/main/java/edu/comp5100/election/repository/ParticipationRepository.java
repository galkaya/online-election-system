package edu.comp5100.election.repository;

import edu.comp5100.election.domain.Participation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    boolean existsByElectionIdAndVoterId(Long electionId, Long voterId);

    long countByElectionId(Long electionId);

    List<Participation> findByElectionId(Long electionId);

    List<Participation> findByVoterId(Long voterId);
}

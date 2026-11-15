package edu.comp5100.election.repository;

import edu.comp5100.election.domain.Ballot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BallotRepository extends JpaRepository<Ballot, UUID> {

    long countByElectionId(Long electionId);

    boolean existsByReceiptHash(String receiptHash);

    /** The candidate id of every stored ballot of an election. The tally is computed from this. */
    @Query("select b.candidateId from Ballot b where b.electionId = :electionId")
    List<Long> findCandidateIdsByElectionId(@Param("electionId") Long electionId);
}

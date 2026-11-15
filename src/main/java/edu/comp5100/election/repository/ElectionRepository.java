package edu.comp5100.election.repository;

import edu.comp5100.election.domain.Election;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElectionRepository extends JpaRepository<Election, Long> {

    /**
     * Loads the election with a database row lock (SELECT ... FOR UPDATE). Voting, closing,
     * activation, candidate changes and eligibility changes all take this lock first, so they
     * run one at a time per election.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Election e where e.id = :id")
    Optional<Election> findByIdForUpdate(@Param("id") long id);

    List<Election> findAllByOrderByStartsAtDescIdDesc();
}

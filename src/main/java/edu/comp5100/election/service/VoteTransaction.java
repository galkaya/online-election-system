package edu.comp5100.election.service;

import edu.comp5100.election.domain.Ballot;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.Participation;
import edu.comp5100.election.domain.Role;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.policy.VoteContext;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.policy.VotingPolicy;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.CandidateRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database transactions of voting (NFR-02, NFR-03).
 *
 * <p>Lock order, always the same: (1) the election row, (2) the voter's account row. Closing,
 * activation and eligibility changes take the election lock; enabling or disabling an account
 * takes only the account lock. Because nobody takes the locks in the opposite order, there is no
 * deadlock, and no rule can change between the check and the write.
 */
@Component
public class VoteTransaction {

    private final ElectionRepository electionRepository;
    private final UserAccountRepository userAccountRepository;
    private final ElectionEligibilityRepository eligibilityRepository;
    private final ParticipationRepository participationRepository;
    private final CandidateRepository candidateRepository;
    private final BallotRepository ballotRepository;
    private final ReceiptGenerator receiptGenerator;
    private final Clock clock;

    public VoteTransaction(ElectionRepository electionRepository, UserAccountRepository userAccountRepository,
            ElectionEligibilityRepository eligibilityRepository, ParticipationRepository participationRepository,
            CandidateRepository candidateRepository, BallotRepository ballotRepository,
            ReceiptGenerator receiptGenerator, Clock clock) {
        this.electionRepository = electionRepository;
        this.userAccountRepository = userAccountRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.participationRepository = participationRepository;
        this.candidateRepository = candidateRepository;
        this.ballotRepository = ballotRepository;
        this.receiptGenerator = receiptGenerator;
        this.clock = clock;
    }

    /**
     * Stores the participation record and the ballot in one transaction, or stores nothing.
     * Any exception, including a rejection, rolls the whole transaction back.
     */
    @Transactional
    public VoteReceipt castVote(String username, long electionId, Long candidateId) {
        Election election = electionRepository.findByIdForUpdate(electionId)
                .orElseThrow(() -> new VoteRejectedException(VoteRejectionReason.ELECTION_NOT_FOUND));
        Optional<UserAccount> voter = userAccountRepository.findByUsernameForUpdate(username);
        // The clock is read only after the locks are held, so a vote that waited for a lock is
        // judged by the time and status that are current when it is actually processed.
        Instant now = clock.instant();

        boolean candidateOnBallot = candidateId != null
                && candidateRepository.existsByIdAndElectionId(candidateId, electionId);
        VoteContext context = contextFor(election, voter, now, candidateId != null, candidateOnBallot);
        Optional<VoteRejectionReason> rejection = VotingPolicy.evaluate(context);
        if (rejection.isPresent()) {
            throw new VoteRejectedException(rejection.get());
        }

        Long voterId = voter.orElseThrow().getId();
        try {
            participationRepository.saveAndFlush(new Participation(electionId, voterId));
        } catch (DataIntegrityViolationException duplicate) {
            // The unique constraint on (election_id, voter_id) is the last line of defence.
            throw new VoteRejectedException(VoteRejectionReason.ALREADY_VOTED);
        }

        String code = receiptGenerator.newCode();
        ballotRepository.saveAndFlush(new Ballot(electionId, candidateId, ReceiptCodec.hash(code)));
        return new VoteReceipt(ReceiptCodec.format(code));
    }

    /**
     * Returns the ballot when the voter may vote right now, otherwise throws the rejection.
     * Read-only: it takes no locks, and the real decision is made again in {@link #castVote}.
     */
    @Transactional(readOnly = true)
    public BallotView loadBallot(String username, long electionId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new VoteRejectedException(VoteRejectionReason.ELECTION_NOT_FOUND));
        Optional<UserAccount> voter = userAccountRepository.findByUsername(username);
        VoteContext context = contextFor(election, voter, clock.instant(), true, true);
        Optional<VoteRejectionReason> rejection = VotingPolicy.evaluateAccess(context);
        if (rejection.isPresent()) {
            throw new VoteRejectedException(rejection.get());
        }
        List<Candidate> candidates = candidateRepository.findByElectionIdOrderByIdAsc(electionId);
        return new BallotView(election, candidates);
    }

    private VoteContext contextFor(Election election, Optional<UserAccount> voter, Instant now,
            boolean candidateSelected, boolean candidateOnBallot) {
        boolean voterMayVote = voter.isPresent() && voter.get().isEnabled() && voter.get().getRole() == Role.VOTER;
        boolean eligible = voter.isPresent()
                && eligibilityRepository.existsByElectionIdAndVoterIdAndActiveTrue(election.getId(), voter.get().getId());
        boolean alreadyVoted = voter.isPresent()
                && participationRepository.existsByElectionIdAndVoterId(election.getId(), voter.get().getId());
        return new VoteContext(voterMayVote, eligible, election.getStatus(), election.getStartsAt(),
                election.getEndsAt(), now, alreadyVoted, candidateSelected, candidateOnBallot);
    }
}

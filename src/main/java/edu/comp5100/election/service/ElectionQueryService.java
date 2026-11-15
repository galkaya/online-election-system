package edu.comp5100.election.service;

import edu.comp5100.election.domain.AuditEventType;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionEligibility;
import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.domain.Participation;
import edu.comp5100.election.domain.Role;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.CandidateOption;
import edu.comp5100.election.policy.ElectionLifecyclePolicy;
import edu.comp5100.election.policy.ElectionPhase;
import edu.comp5100.election.policy.TallyCalculator;
import edu.comp5100.election.policy.TallyResult;
import edu.comp5100.election.repository.AuditEventRepository;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.CandidateRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only views for the pages (FR-08, FR-14, FR-15). */
@Service
@Transactional(readOnly = true)
public class ElectionQueryService {

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionEligibilityRepository eligibilityRepository;
    private final ParticipationRepository participationRepository;
    private final BallotRepository ballotRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public ElectionQueryService(ElectionRepository electionRepository, CandidateRepository candidateRepository,
            ElectionEligibilityRepository eligibilityRepository, ParticipationRepository participationRepository,
            BallotRepository ballotRepository, UserAccountRepository userAccountRepository,
            AuditEventRepository auditEventRepository, Clock clock) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.participationRepository = participationRepository;
        this.ballotRepository = ballotRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    public List<ElectionOverview> listOverviews() {
        Instant now = clock.instant();
        return electionRepository.findAllByOrderByStartsAtDescIdDesc().stream()
                .map(election -> overviewOf(election, now))
                .toList();
    }

    public Election requireElection(long electionId) {
        return electionRepository.findById(electionId)
                .orElseThrow(() -> new NotFoundException("This election does not exist."));
    }

    public ElectionAdminDetail adminDetail(long electionId) {
        Election election = requireElection(electionId);
        Instant now = clock.instant();
        ElectionOverview overview = overviewOf(election, now);
        List<Candidate> candidates = candidateRepository.findByElectionIdOrderByIdAsc(electionId);
        Set<Long> eligibleVoterIds = eligibilityRepository.findByElectionId(electionId).stream()
                .filter(ElectionEligibility::isActive)
                .map(ElectionEligibility::getVoterId)
                .collect(Collectors.toSet());
        Set<Long> votedVoterIds = participationRepository.findByElectionId(electionId).stream()
                .map(Participation::getVoterId)
                .collect(Collectors.toSet());
        List<VoterEligibilityRow> voters = userAccountRepository.findByRoleOrderByUsernameAsc(Role.VOTER).stream()
                .map(voter -> new VoterEligibilityRow(voter, eligibleVoterIds.contains(voter.getId()),
                        votedVoterIds.contains(voter.getId())))
                .toList();
        return new ElectionAdminDetail(overview, candidates, voters,
                ElectionLifecyclePolicy.canEditSetup(election.getStatus()),
                ElectionLifecyclePolicy.canClose(election.getStatus()),
                ElectionLifecyclePolicy.canManageEligibility(overview.phase()));
    }

    /**
     * The elections a voter sees: every activated election where the voter is eligible now or has
     * already voted. Drafts are never shown. The row says whether the voter has voted, never how.
     */
    public List<VoterElectionRow> voterDashboard(String username) {
        UserAccount voter = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("This account does not exist."));
        Set<Long> eligibleElectionIds = eligibilityRepository.findByVoterId(voter.getId()).stream()
                .filter(ElectionEligibility::isActive)
                .map(ElectionEligibility::getElectionId)
                .collect(Collectors.toSet());
        Set<Long> votedElectionIds = participationRepository.findByVoterId(voter.getId()).stream()
                .map(Participation::getElectionId)
                .collect(Collectors.toSet());
        Instant now = clock.instant();
        List<VoterElectionRow> rows = new ArrayList<>();
        for (Election election : electionRepository.findAllByOrderByStartsAtDescIdDesc()) {
            boolean eligible = eligibleElectionIds.contains(election.getId());
            boolean hasVoted = votedElectionIds.contains(election.getId());
            if (election.getStatus() == ElectionStatus.DRAFT || (!eligible && !hasVoted)) {
                continue;
            }
            ElectionPhase phase = election.phaseAt(now);
            boolean canVote = voter.isEnabled() && eligible && !hasVoted && phase == ElectionPhase.VOTING;
            rows.add(new VoterElectionRow(election, phase, eligible, hasVoted, canVote,
                    ElectionLifecyclePolicy.resultsVisible(phase)));
        }
        return rows;
    }

    public boolean hasVoted(String username, long electionId) {
        return userAccountRepository.findByUsername(username)
                .map(voter -> participationRepository.existsByElectionIdAndVoterId(electionId, voter.getId()))
                .orElse(false);
    }

    /**
     * FR-14: the tally of a finished election. Administrators may view any election; a voter only
     * an election in which the voter was eligible or took part. While the election is a draft,
     * scheduled or open, nobody gets candidate totals, not even an administrator.
     */
    public ElectionResults resultsFor(String username, long electionId) {
        Election election = requireElection(electionId);
        UserAccount viewer = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("This account does not exist."));
        if (viewer.getRole() != Role.ADMIN) {
            boolean related = election.getStatus() != ElectionStatus.DRAFT
                    && (eligibilityRepository.existsByElectionIdAndVoterId(electionId, viewer.getId())
                    || participationRepository.existsByElectionIdAndVoterId(electionId, viewer.getId()));
            if (!related) {
                throw new NotFoundException("This election does not exist.");
            }
        }
        ElectionPhase phase = election.phaseAt(clock.instant());
        if (!ElectionLifecyclePolicy.resultsVisible(phase)) {
            throw new ResultsNotAvailableException();
        }
        List<CandidateOption> candidates = candidateRepository.findByElectionIdOrderByIdAsc(electionId).stream()
                .map(candidate -> new CandidateOption(candidate.getId(), candidate.getName()))
                .toList();
        TallyResult tally = TallyCalculator.tally(candidates, ballotRepository.findCandidateIdsByElectionId(electionId));
        return new ElectionResults(election, phase, tally, eligibilityRepository.countByElectionIdAndActiveTrue(electionId));
    }

    private ElectionOverview overviewOf(Election election, Instant now) {
        Long id = election.getId();
        ElectionPhase phase = election.phaseAt(now);
        return new ElectionOverview(election, phase,
                candidateRepository.countByElectionId(id),
                eligibilityRepository.countByElectionIdAndActiveTrue(id),
                participationRepository.countByElectionId(id),
                ballotRepository.countByElectionId(id),
                auditEventRepository.countByElectionIdAndEventType(id, AuditEventType.VOTE_REJECTED),
                ElectionLifecyclePolicy.resultsVisible(phase));
    }
}

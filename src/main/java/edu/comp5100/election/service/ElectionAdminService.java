package edu.comp5100.election.service;

import edu.comp5100.election.domain.AuditEventType;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionEligibility;
import edu.comp5100.election.domain.Role;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ActivationProblem;
import edu.comp5100.election.policy.ElectionLifecyclePolicy;
import edu.comp5100.election.repository.CandidateRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrative changes to elections (FR-03 to FR-06). Every method that changes an existing
 * election first takes the same election row lock that voting takes, then re-checks the rules.
 * Each change and its audit record commit together.
 */
@Service
public class ElectionAdminService {

    private static final int TITLE_MAX = 120;
    private static final int DESCRIPTION_MAX = 1000;
    private static final int CANDIDATE_NAME_MAX = 100;
    private static final int BIOGRAPHY_MAX = 500;

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionEligibilityRepository eligibilityRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuditService auditService;
    private final Clock clock;

    public ElectionAdminService(ElectionRepository electionRepository, CandidateRepository candidateRepository,
            ElectionEligibilityRepository eligibilityRepository, UserAccountRepository userAccountRepository,
            AuditService auditService, Clock clock) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.userAccountRepository = userAccountRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public Election createElection(String actor, String title, String description, Instant startsAt, Instant endsAt) {
        String cleanTitle = requireText(title, TITLE_MAX, "The title");
        String cleanDescription = optionalText(description, DESCRIPTION_MAX, "The description");
        requireValidWindow(startsAt, endsAt);
        Election election = electionRepository.save(
                new Election(cleanTitle, cleanDescription, startsAt, endsAt, clock.instant()));
        auditService.recordAdminEvent(AuditEventType.ELECTION_CREATED, actor, election.getId(),
                "Title: " + cleanTitle);
        return election;
    }

    @Transactional
    public void updateDraft(String actor, long electionId, String title, String description, Instant startsAt,
            Instant endsAt) {
        Election election = lockElection(electionId);
        requireDraft(election);
        String cleanTitle = requireText(title, TITLE_MAX, "The title");
        String cleanDescription = optionalText(description, DESCRIPTION_MAX, "The description");
        requireValidWindow(startsAt, endsAt);
        election.updateSetup(cleanTitle, cleanDescription, startsAt, endsAt);
        auditService.recordAdminEvent(AuditEventType.ELECTION_UPDATED, actor, electionId, "Title: " + cleanTitle);
    }

    @Transactional
    public Candidate addCandidate(String actor, long electionId, String name, String biography) {
        Election election = lockElection(electionId);
        requireDraft(election);
        String cleanName = requireText(name, CANDIDATE_NAME_MAX, "The candidate name");
        String cleanBiography = optionalText(biography, BIOGRAPHY_MAX, "The candidate statement");
        boolean nameTaken = candidateRepository.findByElectionIdOrderByIdAsc(electionId).stream()
                .anyMatch(existing -> existing.getName().equalsIgnoreCase(cleanName));
        if (nameTaken) {
            throw new BusinessRuleException("This election already has a candidate with that name.");
        }
        Candidate candidate = candidateRepository.save(new Candidate(electionId, cleanName, cleanBiography));
        auditService.recordAdminEvent(AuditEventType.CANDIDATE_ADDED, actor, electionId, "Candidate: " + cleanName);
        return candidate;
    }

    @Transactional
    public void removeCandidate(String actor, long electionId, long candidateId) {
        Election election = lockElection(electionId);
        requireDraft(election);
        Candidate candidate = candidateRepository.findByIdAndElectionId(candidateId, electionId)
                .orElseThrow(() -> new NotFoundException("This candidate does not belong to the election."));
        // Safe to delete: a draft has never accepted a ballot.
        candidateRepository.delete(candidate);
        auditService.recordAdminEvent(AuditEventType.CANDIDATE_REMOVED, actor, electionId,
                "Candidate: " + candidate.getName());
    }

    @Transactional
    public void activate(String actor, long electionId) {
        Election election = lockElection(electionId);
        List<String> candidateNames = candidateRepository.findByElectionIdOrderByIdAsc(electionId).stream()
                .map(Candidate::getName)
                .toList();
        List<ActivationProblem> problems = ElectionLifecyclePolicy.activationProblems(election.getStatus(),
                election.getStartsAt(), election.getEndsAt(), candidateNames, clock.instant());
        if (!problems.isEmpty()) {
            throw new BusinessRuleException(problems.stream()
                    .map(ActivationProblem::message)
                    .collect(Collectors.joining(" ")));
        }
        election.markOpen();
        auditService.recordAdminEvent(AuditEventType.ELECTION_ACTIVATED, actor, electionId,
                "Candidates on the ballot: " + candidateNames.size());
    }

    /**
     * Closes an activated election. Once this transaction commits, no vote is accepted: a vote
     * that was waiting for the election lock sees the CLOSED status when it gets the lock.
     */
    @Transactional
    public void close(String actor, long electionId) {
        Election election = lockElection(electionId);
        if (!ElectionLifecyclePolicy.canClose(election.getStatus())) {
            throw new BusinessRuleException("Only an activated election can be closed.");
        }
        election.markClosed();
        auditService.recordAdminEvent(AuditEventType.ELECTION_CLOSED, actor, electionId,
                "Closed by an administrator.");
    }

    @Transactional
    public void grantEligibility(String actor, long electionId, long voterId) {
        Election election = lockElection(electionId);
        requireEligibilityManageable(election);
        UserAccount voter = userAccountRepository.findById(voterId)
                .filter(account -> account.getRole() == Role.VOTER)
                .orElseThrow(() -> new NotFoundException("This voter account does not exist."));
        ElectionEligibility eligibility = eligibilityRepository.findByElectionIdAndVoterId(electionId, voterId)
                .orElseGet(() -> new ElectionEligibility(electionId, voterId));
        if (eligibility.getId() != null && eligibility.isActive()) {
            return; // already eligible: nothing to change, nothing to audit
        }
        eligibility.setActive(true);
        eligibilityRepository.save(eligibility);
        auditService.recordAdminEvent(AuditEventType.ELIGIBILITY_GRANTED, actor, electionId,
                "Voter: " + voter.getUsername());
    }

    /** Revoking never touches participation records or ballots: an accepted vote stays counted. */
    @Transactional
    public void revokeEligibility(String actor, long electionId, long voterId) {
        Election election = lockElection(electionId);
        requireEligibilityManageable(election);
        ElectionEligibility eligibility = eligibilityRepository.findByElectionIdAndVoterId(electionId, voterId)
                .filter(ElectionEligibility::isActive)
                .orElseThrow(() -> new NotFoundException("This voter is not eligible for the election."));
        eligibility.setActive(false);
        String username = userAccountRepository.findById(voterId).map(UserAccount::getUsername).orElse("unknown");
        auditService.recordAdminEvent(AuditEventType.ELIGIBILITY_REVOKED, actor, electionId, "Voter: " + username);
    }

    private Election lockElection(long electionId) {
        return electionRepository.findByIdForUpdate(electionId)
                .orElseThrow(() -> new NotFoundException("This election does not exist."));
    }

    private static void requireDraft(Election election) {
        if (!ElectionLifecyclePolicy.canEditSetup(election.getStatus())) {
            throw new BusinessRuleException(
                    "The election has been activated. Its details, schedule and candidates can no longer change.");
        }
    }

    private void requireEligibilityManageable(Election election) {
        if (!ElectionLifecyclePolicy.canManageEligibility(election.phaseAt(clock.instant()))) {
            throw new BusinessRuleException("Voting is over. Eligibility can no longer change.");
        }
    }

    private static void requireValidWindow(Instant startsAt, Instant endsAt) {
        if (!ElectionLifecyclePolicy.isValidWindow(startsAt, endsAt)) {
            throw new BusinessRuleException("The start time must be before the end time.");
        }
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(fieldName + " is required.");
        }
        return optionalText(value, maxLength, fieldName);
    }

    private static String optionalText(String value, int maxLength, String fieldName) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > maxLength) {
            throw new BusinessRuleException(fieldName + " must be at most " + maxLength + " characters long.");
        }
        return clean;
    }
}

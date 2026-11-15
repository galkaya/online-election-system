package edu.comp5100.election.web;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.service.BallotView;
import edu.comp5100.election.service.ElectionQueryService;
import edu.comp5100.election.service.VoteNotProcessedException;
import edu.comp5100.election.service.VoteReceipt;
import edu.comp5100.election.service.VoteRejectedException;
import edu.comp5100.election.service.VotingService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Voter pages (FR-08 to FR-12). The voter is always the authenticated principal; no request
 * parameter can name another user. The controller holds no voting rules: it calls the service.
 */
@Controller
@RequestMapping("/voter")
public class VoterController {

    private final VotingService votingService;
    private final ElectionQueryService electionQueryService;

    public VoterController(VotingService votingService, ElectionQueryService electionQueryService) {
        this.votingService = votingService;
        this.electionQueryService = electionQueryService;
    }

    @GetMapping
    public String dashboard(Principal principal, Model model) {
        model.addAttribute("rows", electionQueryService.voterDashboard(principal.getName()));
        return "voter/dashboard";
    }

    @GetMapping("/elections/{electionId}/ballot")
    public String ballot(@PathVariable long electionId, Principal principal, Model model) {
        model.addAttribute("ballot", votingService.openBallot(principal.getName(), electionId));
        return "voter/ballot";
    }

    /** Shows the chosen candidate for confirmation. Changes nothing; POST keeps the choice out of URLs. */
    @PostMapping("/elections/{electionId}/review")
    public String review(@PathVariable long electionId, @RequestParam(required = false) Long candidateId,
            Principal principal, Model model, HttpServletResponse response) {
        BallotView ballot = votingService.openBallot(principal.getName(), electionId);
        model.addAttribute("ballot", ballot);
        Candidate selected = candidateId == null ? null : ballot.candidate(candidateId);
        if (selected == null) {
            VoteRejectionReason problem = candidateId == null
                    ? VoteRejectionReason.NO_CANDIDATE_SELECTED
                    : VoteRejectionReason.INVALID_CANDIDATE;
            model.addAttribute("selectionError", problem.message());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "voter/ballot";
        }
        model.addAttribute("selected", selected);
        return "voter/review";
    }

    /** Post/Redirect/Get: the confirmation code travels as a one-time flash attribute, never in a URL. */
    @PostMapping("/elections/{electionId}/vote")
    public String vote(@PathVariable long electionId, @RequestParam(required = false) Long candidateId,
            Principal principal, RedirectAttributes redirectAttributes) {
        VoteReceipt receipt = votingService.castVote(principal.getName(), electionId, candidateId);
        redirectAttributes.addFlashAttribute("receiptCode", receipt.code());
        return "redirect:/voter/elections/" + electionId + "/confirmation";
    }

    @GetMapping("/elections/{electionId}/confirmation")
    public String confirmation(@PathVariable long electionId, Principal principal, Model model) {
        if (!electionQueryService.hasVoted(principal.getName(), electionId)) {
            return "redirect:/voter";
        }
        model.addAttribute("election", electionQueryService.requireElection(electionId));
        return "voter/confirmation";
    }

    @ExceptionHandler(VoteRejectedException.class)
    public String voteRejected(VoteRejectedException rejected, Model model, HttpServletResponse response) {
        response.setStatus(statusFor(rejected.reason()));
        model.addAttribute("reason", rejected.reason());
        return "voter/rejected";
    }

    @ExceptionHandler(VoteNotProcessedException.class)
    public String voteNotProcessed(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        return "voter/not-processed";
    }

    static int statusFor(VoteRejectionReason reason) {
        return switch (reason) {
            case ELECTION_NOT_FOUND -> HttpServletResponse.SC_NOT_FOUND;
            case ACCOUNT_NOT_PERMITTED, NOT_ELIGIBLE -> HttpServletResponse.SC_FORBIDDEN;
            case NO_CANDIDATE_SELECTED, INVALID_CANDIDATE -> HttpServletResponse.SC_BAD_REQUEST;
            case ELECTION_NOT_OPEN, VOTING_NOT_STARTED, VOTING_ENDED, ELECTION_CLOSED, ALREADY_VOTED ->
                    HttpServletResponse.SC_CONFLICT;
        };
    }
}

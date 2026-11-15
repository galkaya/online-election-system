package edu.comp5100.election.web;

import edu.comp5100.election.domain.Election;
import edu.comp5100.election.service.BusinessRuleException;
import edu.comp5100.election.service.ElectionAdminDetail;
import edu.comp5100.election.service.ElectionAdminService;
import edu.comp5100.election.service.ElectionQueryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Election administration pages (FR-03 to FR-06). Access is restricted to ADMIN by the security
 * configuration; every state change is a POST protected by a CSRF token.
 */
@Controller
@RequestMapping("/admin")
public class AdminElectionController {

    private final ElectionAdminService electionAdminService;
    private final ElectionQueryService electionQueryService;
    private final DisplayTime displayTime;
    private final Clock clock;

    public AdminElectionController(ElectionAdminService electionAdminService,
            ElectionQueryService electionQueryService, DisplayTime displayTime, Clock clock) {
        this.electionAdminService = electionAdminService;
        this.electionQueryService = electionQueryService;
        this.displayTime = displayTime;
        this.clock = clock;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("elections", electionQueryService.listOverviews());
        return "admin/dashboard";
    }

    @GetMapping("/elections/new")
    public String newElection(Model model) {
        ElectionForm form = new ElectionForm();
        Instant start = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        form.setStartsAt(displayTime.toInput(start));
        form.setEndsAt(displayTime.toInput(start.plus(Duration.ofDays(1))));
        model.addAttribute("electionForm", form);
        return "admin/election-form";
    }

    @PostMapping("/elections")
    public String createElection(@Valid @ModelAttribute("electionForm") ElectionForm form, BindingResult binding,
            Principal principal, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        Instant[] window = parseWindow(form, binding);
        if (binding.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "admin/election-form";
        }
        Election election = electionAdminService.createElection(principal.getName(), form.getTitle(),
                form.getDescription(), window[0], window[1]);
        redirectAttributes.addFlashAttribute("success", "The draft election was created. Add at least two "
                + "candidates and the eligible voters, then activate it.");
        return "redirect:/admin/elections/" + election.getId();
    }

    @GetMapping("/elections/{electionId}")
    public String detail(@PathVariable long electionId, Model model) {
        ElectionAdminDetail detail = electionQueryService.adminDetail(electionId);
        model.addAttribute("detail", detail);
        if (!model.containsAttribute("electionForm")) {
            Election election = detail.overview().election();
            ElectionForm form = new ElectionForm();
            form.setTitle(election.getTitle());
            form.setDescription(election.getDescription());
            form.setStartsAt(displayTime.toInput(election.getStartsAt()));
            form.setEndsAt(displayTime.toInput(election.getEndsAt()));
            model.addAttribute("electionForm", form);
        }
        return "admin/election-detail";
    }

    @PostMapping("/elections/{electionId}/update")
    public String updateElection(@PathVariable long electionId,
            @Valid @ModelAttribute("electionForm") ElectionForm form, BindingResult binding, Principal principal,
            Model model, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        Instant[] window = parseWindow(form, binding);
        if (binding.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return detail(electionId, model);
        }
        return change(electionId, redirectAttributes, "The election details were saved.",
                () -> electionAdminService.updateDraft(principal.getName(), electionId, form.getTitle(),
                        form.getDescription(), window[0], window[1]));
    }

    @PostMapping("/elections/{electionId}/candidates")
    public String addCandidate(@PathVariable long electionId, @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String biography, Principal principal,
            RedirectAttributes redirectAttributes) {
        return change(electionId, redirectAttributes, "The candidate was added.",
                () -> electionAdminService.addCandidate(principal.getName(), electionId, name, biography));
    }

    @PostMapping("/elections/{electionId}/candidates/{candidateId}/remove")
    public String removeCandidate(@PathVariable long electionId, @PathVariable long candidateId, Principal principal,
            RedirectAttributes redirectAttributes) {
        return change(electionId, redirectAttributes, "The candidate was removed.",
                () -> electionAdminService.removeCandidate(principal.getName(), electionId, candidateId));
    }

    @PostMapping("/elections/{electionId}/eligibility/{voterId}/grant")
    public String grantEligibility(@PathVariable long electionId, @PathVariable long voterId, Principal principal,
            RedirectAttributes redirectAttributes) {
        return change(electionId, redirectAttributes, "The voter is now eligible for this election.",
                () -> electionAdminService.grantEligibility(principal.getName(), electionId, voterId));
    }

    @PostMapping("/elections/{electionId}/eligibility/{voterId}/revoke")
    public String revokeEligibility(@PathVariable long electionId, @PathVariable long voterId, Principal principal,
            RedirectAttributes redirectAttributes) {
        return change(electionId, redirectAttributes, "The voter is no longer eligible for this election.",
                () -> electionAdminService.revokeEligibility(principal.getName(), electionId, voterId));
    }

    @PostMapping("/elections/{electionId}/activate")
    public String activate(@PathVariable long electionId, Principal principal,
            RedirectAttributes redirectAttributes) {
        return change(electionId, redirectAttributes,
                "The election was activated. Votes are accepted inside the voting window.",
                () -> electionAdminService.activate(principal.getName(), electionId));
    }

    @PostMapping("/elections/{electionId}/close")
    public String close(@PathVariable long electionId, Principal principal, RedirectAttributes redirectAttributes) {
        return change(electionId, redirectAttributes,
                "The election was closed. No further votes are accepted and the results are published.",
                () -> electionAdminService.close(principal.getName(), electionId));
    }

    /** Runs one administrative change and redirects back to the election page with a message. */
    private String change(long electionId, RedirectAttributes redirectAttributes, String successMessage,
            Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("success", successMessage);
        } catch (BusinessRuleException refused) {
            redirectAttributes.addFlashAttribute("error", refused.getMessage());
        }
        return "redirect:/admin/elections/" + electionId;
    }

    /** Parses both date-time fields; adds field errors and returns null entries when they are invalid. */
    private Instant[] parseWindow(ElectionForm form, BindingResult binding) {
        Instant startsAt = parseField(form.getStartsAt(), "startsAt", binding);
        Instant endsAt = parseField(form.getEndsAt(), "endsAt", binding);
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            binding.rejectValue("endsAt", "window", "The end time must be after the start time.");
        }
        return new Instant[] {startsAt, endsAt};
    }

    private Instant parseField(String text, String field, BindingResult binding) {
        if (binding.hasFieldErrors(field) || text == null) {
            return null;
        }
        try {
            return displayTime.parseInput(text);
        } catch (DateTimeParseException invalid) {
            binding.rejectValue(field, "datetime", "This is not a real date and time.");
            return null;
        }
    }
}

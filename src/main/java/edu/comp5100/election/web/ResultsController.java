package edu.comp5100.election.web;

import edu.comp5100.election.service.ElectionQueryService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Results page (FR-14). The service decides whether results may be shown at all. */
@Controller
public class ResultsController {

    private final ElectionQueryService electionQueryService;

    public ResultsController(ElectionQueryService electionQueryService) {
        this.electionQueryService = electionQueryService;
    }

    @GetMapping("/elections/{electionId}/results")
    public String results(@PathVariable long electionId, Principal principal, Model model) {
        model.addAttribute("results", electionQueryService.resultsFor(principal.getName(), electionId));
        return "results";
    }
}

package edu.comp5100.election.web;

import edu.comp5100.election.service.AuditService;
import edu.comp5100.election.service.ElectionQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Administrator audit page (FR-15): events, rejected attempts and per-election counts. */
@Controller
public class AdminAuditController {

    private final AuditService auditService;
    private final ElectionQueryService electionQueryService;

    public AdminAuditController(AuditService auditService, ElectionQueryService electionQueryService) {
        this.auditService = auditService;
        this.electionQueryService = electionQueryService;
    }

    @GetMapping("/admin/audit")
    public String audit(Model model) {
        model.addAttribute("elections", electionQueryService.listOverviews());
        model.addAttribute("events", auditService.recentEvents());
        return "admin/audit";
    }
}

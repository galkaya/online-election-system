package edu.comp5100.election.web;

import edu.comp5100.election.service.BusinessRuleException;
import edu.comp5100.election.service.UserAdminService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Voter account administration (FR-02). */
@Controller
@RequestMapping("/admin/voters")
public class AdminVoterController {

    private final UserAdminService userAdminService;

    public AdminVoterController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public String voters(Model model) {
        model.addAttribute("voters", userAdminService.listVoters());
        return "admin/voters";
    }

    @PostMapping
    public String createVoter(@RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String displayName, @RequestParam(defaultValue = "") String password,
            Principal principal, RedirectAttributes redirectAttributes) {
        try {
            userAdminService.createVoter(principal.getName(), username, displayName, password);
            redirectAttributes.addFlashAttribute("success", "The voter account was created.");
        } catch (BusinessRuleException refused) {
            redirectAttributes.addFlashAttribute("error", refused.getMessage());
            // Give the non-secret fields back so the administrator does not retype them.
            redirectAttributes.addFlashAttribute("enteredUsername", username);
            redirectAttributes.addFlashAttribute("enteredDisplayName", displayName);
        }
        return "redirect:/admin/voters";
    }

    @PostMapping("/{voterId}/enable")
    public String enable(@PathVariable long voterId, Principal principal, RedirectAttributes redirectAttributes) {
        userAdminService.setVoterEnabled(principal.getName(), voterId, true);
        redirectAttributes.addFlashAttribute("success", "The voter account was enabled.");
        return "redirect:/admin/voters";
    }

    @PostMapping("/{voterId}/disable")
    public String disable(@PathVariable long voterId, Principal principal, RedirectAttributes redirectAttributes) {
        userAdminService.setVoterEnabled(principal.getName(), voterId, false);
        redirectAttributes.addFlashAttribute("success",
                "The voter account was disabled. It can no longer sign in or vote.");
        return "redirect:/admin/voters";
    }
}

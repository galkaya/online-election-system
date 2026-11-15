package edu.comp5100.election.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sign-in page and the role-based landing redirect (FR-01). */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return admin ? "redirect:/admin" : "redirect:/voter";
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return signedIn ? "redirect:/" : "login";
    }
}

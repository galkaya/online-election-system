package edu.comp5100.election.security;

import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * FR-17: checks on every request that the signed-in account still exists, is still enabled and
 * still has the role stored in the session. If not, the session is ended. The voting service
 * repeats the account check inside its own transaction, so this filter is a second barrier,
 * not the only one.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserAccountRepository userAccountRepository;

    public AccountStatusFilter(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        if (signedIn && !accountStillValid(authentication)) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            response.sendRedirect(request.getContextPath() + "/login?disabled");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean accountStillValid(Authentication authentication) {
        Optional<UserAccount> account = userAccountRepository.findByUsername(authentication.getName());
        if (account.isEmpty() || !account.get().isEnabled()) {
            return false;
        }
        String expectedAuthority = "ROLE_" + account.get().getRole().name();
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> expectedAuthority.equals(authority.getAuthority()));
    }
}

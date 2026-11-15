package edu.comp5100.election.service;

import edu.comp5100.election.domain.AuditEventType;
import edu.comp5100.election.domain.Role;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.UserAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Management of fictional voter accounts (FR-02). Accounts are disabled, never deleted. */
@Service
public class UserAdminService {

    public static final String USERNAME_RULE = "^[a-z0-9][a-z0-9._-]{2,49}$";
    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 72;
    private static final Pattern USERNAME_PATTERN = Pattern.compile(USERNAME_RULE);

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public UserAdminService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder,
            AuditService auditService, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> listVoters() {
        return userAccountRepository.findByRoleOrderByUsernameAsc(Role.VOTER);
    }

    @Transactional
    public UserAccount createVoter(String actor, String username, String displayName, String rawPassword) {
        String cleanUsername = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(cleanUsername).matches()) {
            throw new BusinessRuleException("The username must be 3 to 50 characters: lower-case letters, digits, "
                    + "dots, dashes or underscores, starting with a letter or digit.");
        }
        String cleanDisplayName = displayName == null ? "" : displayName.trim();
        if (cleanDisplayName.isEmpty() || cleanDisplayName.length() > 100) {
            throw new BusinessRuleException("The display name is required and must be at most 100 characters long.");
        }
        if (rawPassword == null || rawPassword.length() < PASSWORD_MIN || rawPassword.length() > PASSWORD_MAX) {
            throw new BusinessRuleException("The password must be " + PASSWORD_MIN + " to " + PASSWORD_MAX
                    + " characters long.");
        }
        if (userAccountRepository.existsByUsername(cleanUsername)) {
            throw new BusinessRuleException("That username is already taken.");
        }
        UserAccount account = userAccountRepository.save(new UserAccount(cleanUsername,
                passwordEncoder.encode(rawPassword), cleanDisplayName, Role.VOTER, clock.instant()));
        auditService.recordAdminEvent(AuditEventType.VOTER_CREATED, actor, null, "Voter: " + cleanUsername);
        return account;
    }

    /**
     * Enables or disables a voter account. It takes the same account row lock as voting, so a
     * vote is either finished before the account is disabled or sees the disabled account.
     * Existing participation records and ballots are never changed.
     */
    @Transactional
    public void setVoterEnabled(String actor, long voterId, boolean enabled) {
        UserAccount account = userAccountRepository.findByIdForUpdate(voterId)
                .filter(candidate -> candidate.getRole() == Role.VOTER)
                .orElseThrow(() -> new NotFoundException("This voter account does not exist."));
        if (account.isEnabled() == enabled) {
            return;
        }
        account.setEnabled(enabled);
        auditService.recordAdminEvent(enabled ? AuditEventType.VOTER_ENABLED : AuditEventType.VOTER_DISABLED, actor,
                null, "Voter: " + account.getUsername());
    }
}

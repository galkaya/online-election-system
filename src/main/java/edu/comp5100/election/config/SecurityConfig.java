package edu.comp5100.election.config;

import edu.comp5100.election.repository.UserAccountRepository;
import edu.comp5100.election.security.AccountStatusFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Session-based form login with two roles (FR-01, FR-16, NFR-04). CSRF protection keeps its
 * secure default (on). Authorization is enforced here on the server for every URL; hiding a
 * menu item is never the protection.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserAccountRepository userAccountRepository)
            throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/favicon.svg", "/login", "/receipt", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/voter/**").hasRole("VOTER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error"))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .addFilterBefore(new AccountStatusFilter(userAccountRepository), AuthorizationFilter.class);
        return http.build();
    }

    /** Framework default: bcrypt behind a delegating encoder, so the hash format can evolve. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}

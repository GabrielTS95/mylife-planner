package com.mylifeplanner.config;

import com.mylifeplanner.repository.AppUserRepository;
import com.mylifeplanner.service.AccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.mylifeplanner.model.AppPage;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> {
            var account = users.findByUsername(username.strip().toLowerCase(java.util.Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
            return new PlannerUserDetails(account);
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountService accounts) throws Exception {
        return http.authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/error", "/favicon.ico").permitAll();
                    auth.requestMatchers("/settings", "/settings/**").access((authentication, context) ->
                            new AuthorizationDecision(accounts.findAuthenticated(authentication.get())
                                    .map(user -> user.isEnabled() && user.isAdministrator()).orElse(false)));
                    auth.requestMatchers("/account", "/account/**", "/").authenticated();
                    for (AppPage page : AppPage.values()) {
                        if (page == AppPage.HOME) continue;
                        auth.requestMatchers(page.getPath(), page.getPath() + "/**").access((authentication, context) ->
                                new AuthorizationDecision(accounts.findAuthenticated(authentication.get())
                                        .map(user -> user.canAccess(page)).orElse(false)));
                    }
                    auth.anyRequest().denyAll();
                })
                .addFilterAfter(new AccountSessionFilter(accounts), AnonymousAuthenticationFilter.class)
                .formLogin(form -> form.loginPage("/login").successHandler((request, response, authentication) ->
                        response.sendRedirect(request.getContextPath() + accounts.landingPage(accounts.currentUser())))
                        .failureUrl("/login?error").permitAll())
                .rememberMe(remember -> remember
                        .key("mylifeplanner-remember-secret")
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds(60 * 60 * 24 * 30))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "remember-me"))
                .build();
    }
}

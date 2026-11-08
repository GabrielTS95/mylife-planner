package com.mylifeplanner.config;

import com.mylifeplanner.service.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class AccountSessionFilter extends OncePerRequestFilter {
    private final AccountService accounts;
    public AccountSessionFilter(AccountService accounts) { this.accounts = accounts; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            var user = accounts.findAuthenticated(authentication).orElse(null);
            boolean stale = authentication.getPrincipal() instanceof PlannerUserDetails principal
                    && user != null && principal.getCredentialVersion() != user.getCredentialVersion();
            if (user == null || !user.isEnabled() || user.isTemporaryPasswordExpired() || stale) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
            String path = request.getServletPath();
            // MockMvc may supply only requestURI; real servlet containers provide servletPath.
            if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
            if (user.isMustChangePassword() && !path.equals("/account/password") && !path.equals("/logout")
                    && !path.startsWith("/css/") && !path.startsWith("/js/") && !path.equals("/error")) {
                response.sendRedirect(request.getContextPath() + "/account/password");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}

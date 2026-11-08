package com.mylifeplanner.service;

import com.mylifeplanner.model.AppUser;
import com.mylifeplanner.model.AppPage;
import com.mylifeplanner.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;

@Service
public class AccountService {
    public static final String USERNAME = "erwintorr";
    private final AppUserRepository users;
    public AccountService(AppUserRepository users) { this.users = users; }
    public AppUser getOwner() { return users.findByUsername(USERNAME).orElseThrow(); }
    public Optional<AppUser> findAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) return Optional.empty();
        return users.findByUsername(authentication.getName());
    }
    public AppUser currentUser() {
        return findAuthenticated(SecurityContextHolder.getContext().getAuthentication())
                .filter(AppUser::isEnabled).orElseThrow(() -> new AccessDeniedException("Inicia sesión."));
    }
    public boolean canAccess(AppPage page) { return currentUser().canAccess(page); }
    public void requireAccess(AppPage page) {
        if (!canAccess(page)) throw new AccessDeniedException("No tienes acceso a esta sección.");
    }
    public String landingPage(AppUser user) {
        if (user.isMustChangePassword()) return "/account/password";
        for (AppPage page : AppPage.values()) if (user.canAccess(page)) return page.getPath();
        return "/account";
    }
    public void requireAdministrator() {
        if (!currentUser().isAdministrator()) throw new AccessDeniedException("Solo el administrador puede gestionar usuarios.");
    }
}

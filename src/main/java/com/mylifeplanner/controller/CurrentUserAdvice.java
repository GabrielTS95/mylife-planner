package com.mylifeplanner.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.security.Principal;
import com.mylifeplanner.service.AccountService;
import com.mylifeplanner.model.AppPage;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice
public class CurrentUserAdvice {
    private final AccountService accounts;
    public CurrentUserAdvice(AccountService accounts) { this.accounts = accounts; }
    @InitBinder
    public void protectOwnership(WebDataBinder binder) { binder.setDisallowedFields("user", "user.*"); }
    @ModelAttribute
    public void access(Model model, Principal principal) {
        var user = principal == null ? null : accounts.findAuthenticated(
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).orElse(null);
        java.util.Map<String, Boolean> access = new java.util.LinkedHashMap<>();
        for (AppPage page : AppPage.values()) access.put(page.name(), user != null && user.canAccess(page));
        model.addAttribute("access", access);
        model.addAttribute("isAdministrator", user != null && user.isAdministrator());
        model.addAttribute("landingPath", user == null ? "/login" : accounts.landingPage(user));
    }
    @ModelAttribute("signedInUsername")
    public String username(Principal principal) { return principal == null ? "" : principal.getName(); }
}

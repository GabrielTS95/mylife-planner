package com.mylifeplanner.controller;

import com.mylifeplanner.service.AccountService;
import com.mylifeplanner.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/account")
public class AccountController {
    private final AccountService accounts;
    private final UserManagementService users;
    public AccountController(AccountService accounts, UserManagementService users) { this.accounts = accounts; this.users = users; }
    @GetMapping
    public String account(Model model) {
        model.addAttribute("activePage", "account");
        return "account/overview";
    }
    @GetMapping("/password")
    public String password(Model model) {
        model.addAttribute("activePage", "account");
        model.addAttribute("temporaryPassword", accounts.currentUser().isMustChangePassword());
        return "account/password";
    }
    @PostMapping("/password")
    public String change(@RequestParam String currentPassword, @RequestParam String newPassword,
                         @RequestParam String confirmation, Model model, HttpServletRequest request,
                         HttpServletResponse response, Authentication authentication) {
        try { users.changePassword(currentPassword, newPassword, confirmation); }
        catch (IllegalArgumentException ex) {
            model.addAttribute("passwordError", ex.getMessage());
            return password(model);
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?passwordChanged";
    }
}

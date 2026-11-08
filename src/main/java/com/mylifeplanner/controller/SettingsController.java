package com.mylifeplanner.controller;

import com.mylifeplanner.model.AppPage;
import com.mylifeplanner.service.UserManagementService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Set;

@Controller
@RequestMapping("/settings")
public class SettingsController {
    private final UserManagementService users;
    public SettingsController(UserManagementService users) { this.users = users; }
    @GetMapping
    public String list(Model model) {
        model.addAttribute("activePage", "settings");
        model.addAttribute("users", users.list());
        model.addAttribute("pages", AppPage.values());
        return "settings/list";
    }
    @PostMapping("/users")
    public String create(@RequestParam String username, @RequestParam(required = false) Set<AppPage> pages,
                         RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("temporaryCredential", users.create(username, pages == null ? Set.of() : pages));
            flash.addFlashAttribute("settingsSuccess", "Usuario creado. Comparte la contraseña temporal de forma privada.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("settingsError", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            flash.addFlashAttribute("settingsError", "Ese usuario ya existe.");
        }
        return "redirect:/settings";
    }
    @PostMapping("/users/{id}/access")
    public String access(@PathVariable Long id, @RequestParam(required = false) Set<AppPage> pages,
                         @RequestParam(defaultValue = "false") boolean enabled, RedirectAttributes flash) {
        try {
            users.updateAccess(id, pages == null ? Set.of() : pages, enabled);
            flash.addFlashAttribute("settingsSuccess", "Permisos actualizados. Se aplican desde la siguiente solicitud.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("settingsError", ex.getMessage()); }
        return "redirect:/settings";
    }
    @PostMapping("/users/{id}/password")
    public String reset(@PathVariable Long id, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("temporaryCredential", users.resetPassword(id));
            flash.addFlashAttribute("settingsSuccess", "Nueva contraseña temporal creada. Las sesiones anteriores se cerrarán.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("settingsError", ex.getMessage()); }
        return "redirect:/settings";
    }
}

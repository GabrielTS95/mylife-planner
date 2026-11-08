package com.mylifeplanner.service;

import com.mylifeplanner.model.AppPage;
import com.mylifeplanner.model.AppUser;
import com.mylifeplanner.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class UserManagementService {
    private final AppUserRepository users;
    private final AccountService accounts;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    public UserManagementService(AppUserRepository users, AccountService accounts, PasswordEncoder encoder) {
        this.users = users; this.accounts = accounts; this.encoder = encoder;
    }
    public record TemporaryCredential(String username, String password, Instant expiresAt) {}
    public List<AppUser> list() {
        accounts.requireAdministrator();
        return users.findAll(org.springframework.data.domain.Sort.by("username"));
    }
    @Transactional
    public TemporaryCredential create(String username, Set<AppPage> pages) {
        accounts.requireAdministrator();
        String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,49}"))
            throw new IllegalArgumentException("El usuario debe tener entre 3 y 50 caracteres: letras, números, punto, guion o guion bajo.");
        if (users.existsByUsernameIgnoreCase(normalized)) throw new IllegalArgumentException("Ese usuario ya existe.");
        AppUser user = new AppUser();
        user.setUsername(normalized);
        user.setPages(pages);
        TemporaryCredential credential = issueTemporaryPassword(user);
        users.saveAndFlush(user);
        return credential;
    }
    @Transactional
    public void updateAccess(Long id, Set<AppPage> pages, boolean enabled) {
        AppUser user = managedUser(id);
        user.setPages(pages);
        if (user.isEnabled() != enabled) user.revokeSessions();
        user.setEnabled(enabled);
        users.save(user);
    }
    @Transactional
    public TemporaryCredential resetPassword(Long id) {
        AppUser user = managedUser(id);
        TemporaryCredential credential = issueTemporaryPassword(user);
        users.save(user);
        return credential;
    }
    private AppUser managedUser(Long id) {
        accounts.requireAdministrator();
        AppUser user = users.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (user.isAdministrator()) throw new IllegalArgumentException("La cuenta administradora conserva todos sus permisos. Cambia tu contraseña desde tu perfil.");
        return user;
    }
    private TemporaryCredential issueTemporaryPassword(AppUser user) {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = Instant.now().plus(24, ChronoUnit.HOURS);
        user.setPasswordHash(encoder.encode(password));
        user.setMustChangePassword(true);
        user.setTemporaryPasswordExpiresAt(expires);
        user.revokeSessions();
        return new TemporaryCredential(user.getUsername(), password, expires);
    }
    @Transactional
    public void changePassword(String currentPassword, String newPassword, String confirmation) {
        AppUser user = users.findForUpdate(accounts.currentUser().getId()).orElseThrow();
        if (user.isTemporaryPasswordExpired()) throw new IllegalArgumentException("La contraseña temporal venció. Solicita otra al administrador.");
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPasswordHash()))
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        if (newPassword == null || newPassword.length() < 12 || newPassword.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Usa al menos 12 caracteres y un máximo de 72 bytes para tu nueva contraseña.");
        if (!newPassword.equals(confirmation)) throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
        if (encoder.matches(newPassword, user.getPasswordHash())) throw new IllegalArgumentException("La nueva contraseña debe ser diferente de la actual.");
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setTemporaryPasswordExpiresAt(null);
        user.revokeSessions();
        users.save(user);
    }
}

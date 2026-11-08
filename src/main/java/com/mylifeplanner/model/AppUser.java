package com.mylifeplanner.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(columnDefinition = "boolean default false")
    private Boolean administrator = false;
    @Column(columnDefinition = "boolean default true")
    private Boolean enabled = true;
    @Column(columnDefinition = "boolean default false")
    private Boolean mustChangePassword = false;
    private Instant temporaryPasswordExpiresAt;
    @Column(columnDefinition = "bigint default 0")
    private Long credentialVersion = 0L;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_page_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "page", nullable = false, length = 30)
    private Set<AppPage> pages = new HashSet<>();

    public boolean isAdministrator() { return Boolean.TRUE.equals(administrator); }
    public void setAdministrator(boolean value) { administrator = value; }
    public boolean isEnabled() { return !Boolean.FALSE.equals(enabled); }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isMustChangePassword() { return Boolean.TRUE.equals(mustChangePassword); }
    public void setMustChangePassword(boolean value) { mustChangePassword = value; }
    public Instant getTemporaryPasswordExpiresAt() { return temporaryPasswordExpiresAt; }
    public void setTemporaryPasswordExpiresAt(Instant value) { temporaryPasswordExpiresAt = value; }
    public long getCredentialVersion() { return credentialVersion == null ? 0L : credentialVersion; }
    public void revokeSessions() { credentialVersion = getCredentialVersion() + 1; }
    public Set<AppPage> getPages() { return pages; }
    public void setPages(Set<AppPage> value) { pages = new HashSet<>(value); }
    public boolean canAccess(AppPage page) { return isEnabled() && (isAdministrator() || pages.contains(page)); }
    public boolean isTemporaryPasswordExpired() {
        return isMustChangePassword() && (temporaryPasswordExpiresAt == null || !Instant.now().isBefore(temporaryPasswordExpiresAt));
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
}

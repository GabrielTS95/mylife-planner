package com.mylifeplanner.config;

import com.mylifeplanner.model.AppUser;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public class PlannerUserDetails extends User {
    private final long credentialVersion;
    public PlannerUserDetails(AppUser user) {
        super(user.getUsername(), user.getPasswordHash(), user.isEnabled(), true,
                !user.isTemporaryPasswordExpired(), true,
                AuthorityUtils.createAuthorityList(user.isAdministrator() ? "ROLE_ADMIN" : "ROLE_USER"));
        credentialVersion = user.getCredentialVersion();
    }
    public long getCredentialVersion() { return credentialVersion; }
}

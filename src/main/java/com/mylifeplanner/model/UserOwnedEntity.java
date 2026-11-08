package com.mylifeplanner.model;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class UserOwnedEntity {
    // Initially nullable for migration; the initializer backfills and enforces NOT NULL.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;
    public AppUser getUser() { return user; }
    public void setUser(AppUser value) { user = value; }
}

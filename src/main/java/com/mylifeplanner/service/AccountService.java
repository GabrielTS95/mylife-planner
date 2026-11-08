package com.mylifeplanner.service;

import com.mylifeplanner.model.AppUser;
import com.mylifeplanner.repository.AppUserRepository;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    public static final String USERNAME = "erwintorr";
    private final AppUserRepository users;
    public AccountService(AppUserRepository users) { this.users = users; }
    public AppUser getOwner() { return users.findByUsername(USERNAME).orElseThrow(); }
}

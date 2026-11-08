package com.mylifeplanner.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.security.Principal;

@ControllerAdvice
public class CurrentUserAdvice {
    @ModelAttribute("signedInUsername")
    public String username(Principal principal) { return principal == null ? "" : principal.getName(); }
}

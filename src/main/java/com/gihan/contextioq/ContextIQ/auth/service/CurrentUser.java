package com.gihan.contextioq.ContextIQ.auth.service;

import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import com.gihan.contextioq.ContextIQ.auth.repository.UserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUser {

  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public AppUser get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) throw unauthorized();
    if (auth.getPrincipal() instanceof OidcUser oidc) return users
      .findByGoogleSubject(oidc.getSubject())
      .orElseThrow(this::unauthorized);
    try {
      return users
        .findById(UUID.fromString(auth.getName()))
        .orElseThrow(this::unauthorized);
    } catch (IllegalArgumentException e) {
      throw unauthorized();
    }
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Sign in to continue"
    );
  }
}

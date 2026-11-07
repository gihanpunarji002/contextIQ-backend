package com.gihan.contextioq.ContextIQ.auth.service;

import com.gihan.contextioq.ContextIQ.auth.dto.AuthDtos.*;
import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import com.gihan.contextioq.ContextIQ.auth.repository.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final String dummyHash;

  public AccountService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
    dummyHash = encoder.encode("constant-time-missing-account-check");
  }

  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  @Transactional
  public AppUser register(RegisterRequest r) {
    try {
      return users.saveAndFlush(
        new AppUser(
          r.firstName().strip(),
          r.lastName().strip(),
          normalizeEmail(r.email()),
          encoder.encode(r.password()),
          null
        )
      );
    } catch (DataIntegrityViolationException e) {
      throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Unable to register this email. Try signing in instead."
      );
    }
  }

  @Transactional(readOnly = true)
  public AppUser login(LoginRequest r) {
    AppUser u = users.findByEmail(normalizeEmail(r.email())).orElse(null);
    String hash =
      u == null || u.getPasswordHash() == null
        ? dummyHash
        : u.getPasswordHash();
    boolean matches = encoder.matches(r.password(), hash);
    if (
      u == null || u.getPasswordHash() == null || !matches
    ) throw new BadCredentialsException("Invalid email or password");
    return u;
  }

  @Transactional
  public AppUser google(OidcUser identity) {
    if (
      !Boolean.TRUE.equals(identity.getEmailVerified()) ||
      identity.getEmail() == null
    ) throw oauthError();
    var existing = users.findByGoogleSubject(identity.getSubject());
    if (existing.isPresent()) return existing.get();
    String email = normalizeEmail(identity.getEmail());
    // Never silently link a Google identity to an existing password account by email.
    if (
      email.length() > 254 || users.findByEmail(email).isPresent()
    ) throw oauthError();
    try {
      return users.saveAndFlush(
        new AppUser(
          name(identity.getGivenName()),
          name(identity.getFamilyName()),
          email,
          null,
          identity.getSubject()
        )
      );
    } catch (DataIntegrityViolationException e) {
      throw oauthError();
    }
  }

  private String name(String value) {
    return value == null
      ? ""
      : value.substring(0, Math.min(80, value.length()));
  }

  private OAuth2AuthenticationException oauthError() {
    return new OAuth2AuthenticationException(
      new OAuth2Error("account_unavailable"),
      "Google sign-in unavailable for this account"
    );
  }
}

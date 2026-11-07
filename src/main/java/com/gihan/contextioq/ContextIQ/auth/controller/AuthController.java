package com.gihan.contextioq.ContextIQ.auth.controller;

import com.gihan.contextioq.ContextIQ.auth.dto.AuthDtos.*;
import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import com.gihan.contextioq.ContextIQ.auth.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AccountService accounts;
  private final CurrentUser current;
  private final boolean googleEnabled;

  public AuthController(
    AccountService accounts,
    CurrentUser current,
    @Value("${contextiq.auth.google-enabled:false}") boolean enabled
  ) {
    this.accounts = accounts;
    this.current = current;
    googleEnabled = enabled;
  }

  public record CsrfView(String token, String headerName) {}

  public record Providers(boolean google) {}

  @GetMapping("/csrf")
  public CsrfView csrf(CsrfToken token) {
    return new CsrfView(token.getToken(), token.getHeaderName());
  }

  @GetMapping("/providers")
  public Providers providers() {
    return new Providers(googleEnabled);
  }

  @GetMapping("/me")
  public UserView me() {
    return UserView.from(current.get());
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserView register(
    @Valid @RequestBody RegisterRequest r,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    return signIn(accounts.register(r), request, response);
  }

  @PostMapping("/login")
  public UserView login(
    @Valid @RequestBody LoginRequest r,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    return signIn(accounts.login(r), request, response);
  }

  private UserView signIn(
    AppUser user,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    // Rotate the session ID and CSRF token at the authentication boundary.
    request.getSession();
    request.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
      UsernamePasswordAuthenticationToken.authenticated(
        user.getId().toString(),
        null,
        AuthorityUtils.createAuthorityList("ROLE_USER")
      )
    );
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(
      context,
      request,
      response
    );
    new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
    return UserView.from(user);
  }
}

package com.gihan.contextioq.ContextIQ.config;

import com.gihan.contextioq.ContextIQ.auth.service.AccountService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
      "pbkdf2",
      java.util.Map.of(
        "pbkdf2",
        Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()
      )
    );
  }

  @Bean
  @ConditionalOnProperty(
    name = "contextiq.auth.google-enabled",
    havingValue = "true"
  )
  ClientRegistrationRepository googleRegistration(
    @Value("${GOOGLE_CLIENT_ID}") String id,
    @Value("${GOOGLE_CLIENT_SECRET}") String secret
  ) {
    if (id.isBlank() || secret.isBlank()) throw new IllegalStateException(
      "Google credentials are required when Google login is enabled"
    );
    return new InMemoryClientRegistrationRepository(
      CommonOAuth2Provider.GOOGLE.getBuilder("google")
        .clientId(id)
        .clientSecret(secret)
        .scope("openid", "profile", "email")
        .build()
    );
  }

  @Bean
  SecurityFilterChain security(
    HttpSecurity http,
    ObjectProvider<ClientRegistrationRepository> clients,
    AccountService accounts,
    @Value("${contextiq.frontend-url}") String frontend
  ) throws Exception {
    http.cors(Customizer.withDefaults());
    // Session-backed CSRF tokens are returned by /auth/csrf and sent as a request header.
    http.csrf(Customizer.withDefaults());
    http.requestCache(cache -> cache.disable());
    http.authorizeHttpRequests(auth ->
      auth
        .requestMatchers(
          "/api/v1/health",
          "/actuator/health",
          "/actuator/health/**",
          "/api/v1/auth/csrf",
          "/api/v1/auth/providers",
          "/api/v1/auth/login",
          "/api/v1/auth/register",
          "/oauth2/**",
          "/login/oauth2/**",
          "/error"
        )
        .permitAll()
        .anyRequest()
        .authenticated()
    );
    http.exceptionHandling(errors ->
      errors
        .authenticationEntryPoint((req, res, e) ->
          problem(res, 401, "Unauthorized", "Sign in to continue")
        )
        .accessDeniedHandler((req, res, e) ->
          problem(
            res,
            403,
            "Forbidden",
            "Request not allowed. Refresh the page and try again."
          )
        )
    );
    http.logout(logout ->
      logout
        .logoutUrl("/api/v1/auth/logout")
        .invalidateHttpSession(true)
        .clearAuthentication(true)
        .deleteCookies("JSESSIONID")
        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204))
    );
    if (clients.getIfAvailable() != null) {
      OidcUserService delegate = new OidcUserService();
      http.oauth2Login(oauth ->
        oauth
          .userInfoEndpoint(info ->
            info.oidcUserService(request -> {
              var identity = delegate.loadUser(request);
              accounts.google(identity);
              return identity;
            })
          )
          .successHandler((req, res, auth) ->
            res.sendRedirect(frontend + "/dashboard")
          )
          .failureHandler((req, res, e) ->
            res.sendRedirect(frontend + "/login?error=google")
          )
      );
    }
    return http.build();
  }

  private static void problem(
    HttpServletResponse response,
    int status,
    String title,
    String detail
  ) throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response
      .getWriter()
      .write(
        "{\"type\":\"about:blank\",\"status\":" +
          status +
          ",\"title\":\"" +
          title +
          "\",\"detail\":\"" +
          detail +
          "\"}"
      );
  }
}

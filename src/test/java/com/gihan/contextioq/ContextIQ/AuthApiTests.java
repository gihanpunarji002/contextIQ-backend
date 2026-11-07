package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gihan.contextioq.ContextIQ.auth.repository.UserRepository;
import com.gihan.contextioq.ContextIQ.auth.service.AccountService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  UserRepository users;

  @Autowired
  PasswordEncoder encoder;

  @Autowired
  AccountService accounts;

  private static final String PASSWORD = "a-long-unique-test-passphrase";

  private MockHttpSession session() {
    return new MockHttpSession();
  }

  private ResultActions postJson(
    String path,
    String body,
    MockHttpSession session
  ) throws Exception {
    String json = mvc
      .perform(get("/api/v1/auth/csrf").session(session))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    String token = JsonPath.read(json, "$.token");
    return mvc.perform(
      post(path)
        .session(session)
        .header("X-CSRF-TOKEN", token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
    );
  }

  private String registration(String email) {
    return (
      "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"" +
      email +
      "\",\"password\":\"" +
      PASSWORD +
      "\"}"
    );
  }

  private String login(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }

  @Test
  void authenticationRequiredAndCsrfEnforced() throws Exception {
    mvc
      .perform(get("/api/v1/dashboard/summary"))
      .andExpect(status().isUnauthorized());
    mvc
      .perform(get("/api/v1/chat/conversations"))
      .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    mvc
      .perform(
        post("/api/v1/auth/register")
          .contentType(MediaType.APPLICATION_JSON)
          .content(registration("blocked@example.com"))
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(get("/api/v1/auth/providers"))
      .andExpect(jsonPath("$.google").value(false));
  }

  @Test
  void registerLoginLogoutAndPasswordHash() throws Exception {
    String email = "auth-" + UUID.randomUUID() + "@example.com";
    var s = session();
    String before = s.getId();
    postJson("/api/v1/auth/register", registration(email.toUpperCase()), s)
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.firstName").value("Ada"))
      .andExpect(jsonPath("$.passwordHash").doesNotExist());
    assertNotEquals(before, s.getId());
    var u = users.findByEmail(email).orElseThrow();
    assertNotEquals(PASSWORD, u.getPasswordHash());
    assertTrue(encoder.matches(PASSWORD, u.getPasswordHash()));
    mvc
      .perform(get("/api/v1/auth/me").session(s))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.email").value(email));
    mvc
      .perform(get("/api/v1/dashboard/summary").session(s))
      .andExpect(status().isOk());
    postJson("/api/v1/auth/logout", "{}", s).andExpect(status().isNoContent());
    assertTrue(s.isInvalid());
    var other = session();
    postJson("/api/v1/auth/login", login(email, "wrong-password"), other)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    postJson("/api/v1/auth/login", login(email, PASSWORD), other).andExpect(
      status().isOk()
    );
    mvc
      .perform(get("/api/v1/auth/me").session(other))
      .andExpect(status().isOk());
    postJson("/api/v1/auth/register", registration(email), session()).andExpect(
      status().isConflict()
    );
  }

  @Test
  void validation() throws Exception {
    postJson("/api/v1/auth/register", "{}", session()).andExpect(
      status().isBadRequest()
    );
    postJson(
      "/api/v1/auth/register",
      registration("bad-email"),
      session()
    ).andExpect(status().isBadRequest());
    postJson(
      "/api/v1/auth/register",
      registration("short@example.com").replace(PASSWORD, "short"),
      session()
    ).andExpect(status().isBadRequest());
  }

  @Test
  void conversationIsolation() throws Exception {
    var a = session();
    var b = session();
    postJson(
      "/api/v1/auth/register",
      registration("a-" + UUID.randomUUID() + "@example.com"),
      a
    ).andExpect(status().isCreated());
    postJson(
      "/api/v1/auth/register",
      registration("b-" + UUID.randomUUID() + "@example.com"),
      b
    ).andExpect(status().isCreated());
    String reply = postJson(
      "/api/v1/chat/messages",
      "{\"message\":\"private conversation\"}",
      a
    )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    String id = JsonPath.read(reply, "$.conversationId");
    mvc
      .perform(get("/api/v1/chat/conversations").session(b))
      .andExpect(jsonPath("$.length()").value(0));
    mvc
      .perform(get("/api/v1/chat/conversations/" + id + "/messages").session(b))
      .andExpect(status().isNotFound());
    postJson(
      "/api/v1/chat/messages",
      "{\"message\":\"intrusion\",\"conversationId\":\"" + id + "\"}",
      b
    ).andExpect(status().isNotFound());
    mvc
      .perform(get("/api/v1/chat/conversations/" + id + "/messages").session(a))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void googleProvisioningRequiresVerifiedEmailAndDoesNotLinkPasswordAccounts()
    throws Exception {
    String email = "google-" + UUID.randomUUID() + "@example.com";
    OidcUser identity = mock(OidcUser.class);
    when(identity.getEmail()).thenReturn(email);
    when(identity.getSubject()).thenReturn(UUID.randomUUID().toString());
    when(identity.getEmailVerified()).thenReturn(false);
    assertThrows(OAuth2AuthenticationException.class, () ->
      accounts.google(identity)
    );
    when(identity.getEmailVerified()).thenReturn(true);
    when(identity.getGivenName()).thenReturn("Google");
    when(identity.getFamilyName()).thenReturn("User");
    var user = accounts.google(identity);
    assertNull(user.getPasswordHash());
    assertEquals(user.getId(), accounts.google(identity).getId());
    String local = "local-" + UUID.randomUUID() + "@example.com";
    postJson("/api/v1/auth/register", registration(local), session()).andExpect(
      status().isCreated()
    );
    when(identity.getEmail()).thenReturn(local);
    when(identity.getSubject()).thenReturn(UUID.randomUUID().toString());
    assertThrows(OAuth2AuthenticationException.class, () ->
      accounts.google(identity)
    );
  }
}

package com.gihan.contextioq.ContextIQ;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Dummy OAuth configuration: these tests never exchange codes or call Google.
@SpringBootTest(
  properties = {
    "contextiq.auth.google-enabled=true",
    "GOOGLE_CLIENT_ID=test-client-id",
    "GOOGLE_CLIENT_SECRET=test-only-not-a-real-secret",
  }
)
@AutoConfigureMockMvc
class GoogleLoginTests {

  @Autowired
  MockMvc mvc;

  @Test
  void googleStartsAnAuthorizationCodeFlow() throws Exception {
    mvc
      .perform(get("/api/v1/auth/providers"))
      .andExpect(jsonPath("$.google").value(true));
    mvc
      .perform(get("/oauth2/authorization/google"))
      .andExpect(status().is3xxRedirection())
      .andExpect(
        header().string(
          "Location",
          allOf(
            startsWith("https://accounts.google.com/"),
            containsString("response_type=code"),
            containsString("state="),
            containsString("nonce="),
            containsString("client_id=test-client-id")
          )
        )
      );
  }

  @Test
  void unsolicitedCallbackFailsClosed() throws Exception {
    mvc
      .perform(
        get("/login/oauth2/code/google")
          .param("code", "fake-code")
          .param("state", "unknown-state")
      )
      .andExpect(status().is3xxRedirection())
      .andExpect(redirectedUrl("http://localhost:5173/login?error=google"));
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }
}

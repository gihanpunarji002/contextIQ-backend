package com.gihan.contextioq.ContextIQ;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
  properties = { "contextiq.ai.mode=OLLAMA" }
)
@AutoConfigureMockMvc
class OllamaConfigurationTests {

  @Autowired
  MockMvc mvc;

  @Test
  void localProviderStatusRequiresAuthentication()
    throws Exception {
    mvc.perform(get("/api/v1/ai/status")).andExpect(status().isUnauthorized());
    mvc
      .perform(get("/api/v1/ai/status").with(user("status-test")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.mode").value("OLLAMA"))
      .andExpect(jsonPath("$.configured").value(true))
      .andExpect(jsonPath("$.documentSearch").value(true))
      .andExpect(jsonPath("$.apiKey").doesNotExist());
  }
}

package com.gihan.contextioq.ContextIQ;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gihan.contextioq.ContextIQ.chat.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChatApiTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Autowired
  org.springframework.web.context.WebApplicationContext context;

  @org.junit.jupiter.api.BeforeEach
  void account() {
    mvc =
      org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(
        context
      )
        .apply(
          org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()
        )
        .defaultRequest(
          get("/").with(
            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
              "10000000-0000-0000-0000-000000000001"
            )
          )
        )
        .build();

    jdbc.update(
      "INSERT INTO app_users(id,first_name,last_name,email,password_hash,created_at) SELECT '10000000-0000-0000-0000-000000000001','Test','User','chat-test@example.com','test-only-hash',CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE id='10000000-0000-0000-0000-000000000001')"
    );
  }

  @Autowired
  ConversationRepository conversations;

  @Test
  void healthAndSummary() throws Exception {
    mvc
      .perform(get("/api/v1/health"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.application").value("ContextIQ"));
    mvc
      .perform(get("/api/v1/dashboard/summary"))
      .andExpect(jsonPath("$.mode").value("MOCK"));
  }

  @Test
  void invalidRequests() throws Exception {
    for (String body : new String[] {
      "{}",
      "{\"message\":\" \"}",
      "{\"message\":\"hello\",\"conversationId\":\"bad\"}",
    })
      mvc
        .perform(
          post("/api/v1/chat/messages")
            .with(
              org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
        )
        .andExpect(status().isBadRequest());
    mvc
      .perform(
        get(
          "/api/v1/chat/conversations/00000000-0000-0000-0000-000000000000/messages"
        )
      )
      .andExpect(status().isNotFound());
    mvc
      .perform(
        post("/api/v1/chat/messages")
          .with(
            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
          )
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"message\":\"hello\",\"conversationId\":\"00000000-0000-0000-0000-000000000000\"}"
          )
      )
      .andExpect(status().isNotFound());
  }

  @Test
  void persistsAndReusesConversation() throws Exception {
    mvc
      .perform(
        post("/api/v1/chat/messages")
          .with(
            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
          )
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"message\":\"Foundation test\"}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.mode").value("MOCK"))
      .andExpect(jsonPath("$.userMessageId").isNotEmpty())
      .andExpect(
        jsonPath("$.response").value(org.hamcrest.Matchers.startsWith("[MOCK]"))
      );
    var id = conversations
      .findAll()
      .stream()
      .filter(c -> c.getTitle().equals("Foundation test"))
      .findFirst()
      .orElseThrow()
      .getId();
    mvc
      .perform(
        post("/api/v1/chat/messages")
          .with(
            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
          )
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"message\":\"Again\",\"conversationId\":\"" + id + "\"}")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.conversationId").value(id.toString()));
    mvc
      .perform(get("/api/v1/chat/conversations/" + id + "/messages"))
      .andExpect(jsonPath("$.length()").value(4))
      .andExpect(jsonPath("$[0].role").value("USER"))
      .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
    mvc.perform(get("/api/v1/chat/conversations")).andExpect(status().isOk());
  }

  @Test
  void oversizedMessagesAndCors() throws Exception {
    mvc
      .perform(
        post("/api/v1/chat/messages")
          .with(
            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
          )
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"message\":\"" + "x".repeat(8001) + "\"}")
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        options("/api/v1/chat/messages")
          .header("Origin", "http://localhost:5173")
          .header("Access-Control-Request-Method", "POST")
      )
      .andExpect(status().isOk())
      .andExpect(
        header().string("Access-Control-Allow-Origin", "http://localhost:5173")
      );
    mvc
      .perform(
        options("/api/v1/chat/messages")
          .header("Origin", "https://untrusted.example")
          .header("Access-Control-Request-Method", "POST")
      )
      .andExpect(status().isForbidden());
  }
}

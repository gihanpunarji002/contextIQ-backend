package com.gihan.contextioq.ContextIQ;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gihan.contextioq.ContextIQ.ai.service.AiChatService;
import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import com.gihan.contextioq.ContextIQ.auth.repository.UserRepository;
import com.gihan.contextioq.ContextIQ.auth.service.CurrentUser;
import com.gihan.contextioq.ContextIQ.chat.dto.ChatDtos.SendRequest;
import com.gihan.contextioq.ContextIQ.chat.repository.*;
import com.gihan.contextioq.ContextIQ.chat.service.ChatService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class ChatGenerationTests {

  @Autowired
  ChatService chat;

  @Autowired
  UserRepository users;

  @Autowired
  ConversationRepository conversations;

  @Autowired
  MessageRepository messages;

  @MockitoBean
  AiChatService ai;

  @MockitoBean
  CurrentUser current;

  @MockitoBean
  com.gihan.contextioq.ContextIQ.rag.RetrievalService retrieval;

  @BeforeEach
  void setup() {
    var u = users.save(
      new AppUser(
        "Test",
        "User",
        UUID.randomUUID() + "@example.com",
        "test-only-hash",
        null
      )
    );
    when(current.get()).thenReturn(u);
    when(ai.status()).thenReturn(
      new AiChatService.Status("OLLAMA", "test-model", true)
    );
  }

  @Test
  void historyAndUsagePersistWithoutTransactionDuringGeneration() {
    when(ai.reply(anyList())).thenAnswer(invocation -> {
      assertFalse(
        TransactionSynchronizationManager.isActualTransactionActive()
      );
      return new AiChatService.Reply(
        "Generated answer",
        "OLLAMA",
        "test-model",
        10,
        4
      );
    });
    var first = chat.send(new SendRequest("First", null));
    var second = chat.send(
      new SendRequest("Follow up", first.conversationId())
    );
    var capture = org.mockito.ArgumentCaptor.<List<AiChatService.Turn>>captor();
    verify(ai, times(2)).reply(capture.capture());
    assertEquals(
      List.of(
        new AiChatService.Turn("user", "First"),
        new AiChatService.Turn("assistant", "Generated answer"),
        new AiChatService.Turn("user", "Follow up")
      ),
      capture.getAllValues().get(1)
    );
    var history = chat.history(second.conversationId());
    assertEquals(4, history.size());
    assertEquals("OLLAMA", history.get(1).mode());
    assertEquals(10, history.get(1).inputTokens());
  }

  @Test
  void groundedAnswerPersistsSourceSnapshotsAndNoMatchSkipsGeneration() {
    var source = new com.gihan.contextioq.ContextIQ.rag.Source(UUID.randomUUID(), UUID.randomUUID(),
      "launch.md", 0, "Juniper launches on May 14.", 0.8);
    when(retrieval.retrieve(any(), eq("When?"))).thenReturn(List.of(source));
    when(ai.groundedReply(eq("When?"), anyList())).thenReturn(
      new AiChatService.Reply("May 14 [1]", "OLLAMA", "test", 20, 5));
    var answer = chat.send(new SendRequest("When?", null, true));
    assertTrue(answer.retrievalUsed());
    assertEquals(List.of(source), chat.history(answer.conversationId()).get(1).sources());
    when(retrieval.retrieve(any(), eq("Unknown?"))).thenReturn(List.of());
    var empty = chat.send(new SendRequest("Unknown?", null, true));
    assertTrue(empty.response().contains("couldn't find"));
    assertEquals(0, empty.outputTokens());
    verify(ai, never()).groundedReply(eq("Unknown?"), anyList());
    verify(ai, never()).reply(anyList());
  }

  @Test
  void providerFailureDoesNotPersistATurn() {
    long before = conversations.count(),
      messageCount = messages.count();
    when(ai.reply(anyList())).thenThrow(
      new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "Provider unavailable"
      )
    );
    assertThrows(ResponseStatusException.class, () ->
      chat.send(new SendRequest("Fail", null))
    );
    assertEquals(before, conversations.count());
    assertEquals(messageCount, messages.count());
  }
}

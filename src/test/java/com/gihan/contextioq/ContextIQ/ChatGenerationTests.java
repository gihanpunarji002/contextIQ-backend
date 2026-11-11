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

  @Autowired
  com.gihan.contextioq.ContextIQ.document.DocumentRepository documents;

  @Autowired
  com.gihan.contextioq.ContextIQ.document.FolderRepository folders;

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
    var doc = documents.insert(current.get().getId(), "launch.md", 20, "nomic", List.of("May 14"), List.of(new float[]{1}));
    var source = new com.gihan.contextioq.ContextIQ.rag.Source(doc.id(), UUID.randomUUID(),
      "launch.md", 0, "Juniper launches on May 14.", 0.8);
    when(retrieval.retrieve(any(), eq("When?"), anyList(), anyList())).thenReturn(List.of(source));
    when(ai.groundedReply(eq("When?"), anyList())).thenReturn(
      new AiChatService.Reply("May 14 [1]", "OLLAMA", "test", 20, 5));
    var answer = chat.send(new SendRequest("When?", null, true, List.of(doc.id())));
    assertTrue(answer.retrievalUsed());
    verify(retrieval).retrieve(current.get().getId(), "When?", List.of(doc.id()), List.of());
    assertEquals(doc.id(), chat.documentContext(answer.conversationId()).documents().getFirst().id());
    assertTrue(chat.documentContext(answer.conversationId()).useDocuments());
    documents.delete(doc.id(), current.get().getId());
    assertFalse(chat.documentContext(answer.conversationId()).documents().getFirst().available());
    assertEquals(List.of(source), chat.history(answer.conversationId()).get(1).sources());
    when(retrieval.retrieve(any(), eq("Unknown?"), anyList(), anyList())).thenReturn(List.of());
    var empty = chat.send(new SendRequest("Unknown?", answer.conversationId(), true));
    assertTrue(empty.response().contains("couldn't find"));
    verify(retrieval).retrieve(current.get().getId(), "Unknown?", List.of(), List.of());
    assertEquals("launch.md", chat.documentContext(answer.conversationId()).documents().getFirst().filename());
    assertEquals(0, empty.outputTokens());
    verify(ai, never()).groundedReply(eq("Unknown?"), anyList());
    verify(ai, never()).reply(anyList());
  }

  @Test
  void folderScopePersistsAndEmptyFoldersNeverBroadenRetrieval() {
    var folder = folders.create(current.get().getId(), new com.gihan.contextioq.ContextIQ.document.FolderDtos.FolderRequest(
      "Work", com.gihan.contextioq.ContextIQ.document.FolderDtos.Icon.WORK, com.gihan.contextioq.ContextIQ.document.FolderDtos.Color.BLUE));
    var empty = chat.send(new SendRequest("Empty folder", null, true, List.of(), List.of(folder.id())));
    verify(retrieval).retrieve(current.get().getId(), "Empty folder", List.of(), List.of());
    assertEquals(folder.id(), chat.documentContext(empty.conversationId()).folders().getFirst().id());
    var doc = documents.insert(current.get().getId(), "work.txt", 10, "nomic", List.of("Work evidence"), List.of(new float[]{1}));
    folders.move(doc.id(), current.get().getId(), folder.id());
    // Omitted folder IDs retain the saved folder and resolve its current contents on the next question.
    chat.send(new SendRequest("Now populated", empty.conversationId(), true));
    verify(retrieval).retrieve(current.get().getId(), "Now populated", List.of(), List.of(folder.id()));
    folders.move(doc.id(), current.get().getId(), null);
    chat.send(new SendRequest("Moved out", empty.conversationId(), true));
    verify(retrieval).retrieve(current.get().getId(), "Moved out", List.of(), List.of());
    verify(ai, never()).reply(anyList());
    verify(ai, never()).groundedReply(anyString(), anyList());
  }

  @Test
  void changingSelectionDuringGenerationRejectsTheStaleReply() {
    when(ai.reply(anyList())).thenReturn(new AiChatService.Reply("First", "OLLAMA", "test", 1, 1));
    var first = chat.send(new SendRequest("Start", null));
    when(ai.reply(anyList())).thenAnswer(invocation -> {
      chat.selectDocuments(first.conversationId(), new com.gihan.contextioq.ContextIQ.chat.dto.ChatDtos.DocumentSelection(false, List.of()));
      return new AiChatService.Reply("Stale", "OLLAMA", "test", 1, 1);
    });
    var failure = assertThrows(ResponseStatusException.class,
      () -> chat.send(new SendRequest("Follow up", first.conversationId())));
    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    assertEquals(2, chat.history(first.conversationId()).size());
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

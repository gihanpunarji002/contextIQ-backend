package com.gihan.contextioq.ContextIQ.chat.service;

import com.gihan.contextioq.ContextIQ.ai.service.AiChatService;
import com.gihan.contextioq.ContextIQ.chat.dto.ChatDtos.*;
import com.gihan.contextioq.ContextIQ.chat.entity.*;
import com.gihan.contextioq.ContextIQ.chat.repository.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatService.class);
  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final AiChatService ai;
  private final com.gihan.contextioq.ContextIQ.auth.service.CurrentUser current;

  public ChatService(
    ConversationRepository c,
    MessageRepository m,
    AiChatService ai,
    com.gihan.contextioq.ContextIQ.auth.service.CurrentUser current
  ) {
    conversations = c;
    messages = m;
    this.ai = ai;
    this.current = current;
  }

  @Transactional
  public SendResponse send(SendRequest request) {
    String text = request.message().strip();
    Conversation c =
      request.conversationId() == null
        ? conversations.save(
            new Conversation(
              text.substring(0, Math.min(120, text.length())),
              current.get().getId()
            )
          )
        : conversations
            .findForUpdateById(request.conversationId(), current.get().getId())
            .orElseThrow(() ->
              new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Conversation not found"
              )
            );
    // Serialize turns per conversation; microsecond spacing gives stable PostgreSQL ordering.
    Instant time = Instant.now().truncatedTo(
      java.time.temporal.ChronoUnit.MICROS
    );
    if (!time.isAfter(c.getUpdatedAt())) time = c
      .getUpdatedAt()
      .plusNanos(1000);
    Message user = messages.save(new Message(c, MessageRole.USER, text, time));
    Message assistant = messages.save(
      new Message(
        c,
        MessageRole.ASSISTANT,
        ai.reply(text),
        time.plusNanos(1000)
      )
    );
    c.touch(assistant.getCreatedAt());
    log.info("Saved {} chat turn in conversation {}", ai.mode(), c.getId());
    return new SendResponse(
      c.getId(),
      user.getId(),
      assistant.getId(),
      assistant.getContent(),
      ai.mode()
    );
  }

  @Transactional(readOnly = true)
  public List<ConversationView> conversations() {
    return conversations
      .findByOwnerId(
        current.get().getId(),
        Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id"))
      )
      .stream()
      .map(ConversationView::from)
      .toList();
  }

  @Transactional(readOnly = true)
  public List<MessageView> history(UUID id) {
    if (
      !conversations.existsByIdAndOwnerId(id, current.get().getId())
    ) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Conversation not found"
    );
    return messages
      .findByConversationIdOrderByCreatedAtAscIdAsc(id)
      .stream()
      .map(MessageView::from)
      .toList();
  }
}

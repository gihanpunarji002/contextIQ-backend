package com.gihan.contextioq.ContextIQ.chat.dto;

import com.gihan.contextioq.ContextIQ.chat.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ChatDtos {

  private ChatDtos() {}

  public record SendRequest(
    @NotBlank @Size(max = 8000) String message,
    UUID conversationId
  ) {}

  public record ConversationView(
    UUID id,
    String title,
    Instant createdAt,
    Instant updatedAt
  ) {
    public static ConversationView from(Conversation c) {
      return new ConversationView(
        c.getId(),
        c.getTitle(),
        c.getCreatedAt(),
        c.getUpdatedAt()
      );
    }
  }

  public record MessageView(
    UUID id,
    MessageRole role,
    String content,
    Instant createdAt
  ) {
    public static MessageView from(Message m) {
      return new MessageView(
        m.getId(),
        m.getRole(),
        m.getContent(),
        m.getCreatedAt()
      );
    }
  }

  public record SendResponse(
    UUID conversationId,
    UUID userMessageId,
    UUID assistantMessageId,
    String response,
    String mode
  ) {}
}

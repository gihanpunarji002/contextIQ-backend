package com.gihan.contextioq.ContextIQ.chat.dto;

import com.gihan.contextioq.ContextIQ.chat.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ChatDtos {

  private ChatDtos() {}

  public record SendRequest(
    @NotBlank @Size(max = 8000) String message,
    UUID conversationId,
    Boolean useDocuments
  ) {
    public SendRequest { useDocuments = Boolean.TRUE.equals(useDocuments); }
    public SendRequest(String message, UUID conversationId) { this(message, conversationId, false); }
  }

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
    Instant createdAt,
    String mode,
    String model,
    Integer inputTokens,
    Integer outputTokens,
    boolean retrievalUsed,
    java.util.List<com.gihan.contextioq.ContextIQ.rag.Source> sources
  ) {
    public static MessageView from(Message m, java.util.List<com.gihan.contextioq.ContextIQ.rag.Source> sources) {
      return new MessageView(
        m.getId(),
        m.getRole(),
        m.getContent(),
        m.getCreatedAt(),
        m.getMode(),
        m.getModel(),
        m.getInputTokens(),
        m.getOutputTokens(),
        m.isRetrievalUsed(),
        sources
      );
    }
  }

  public record SendResponse(
    UUID conversationId,
    UUID userMessageId,
    UUID assistantMessageId,
    String response,
    String mode,
    String model,
    Integer inputTokens,
    Integer outputTokens,
    boolean retrievalUsed,
    java.util.List<com.gihan.contextioq.ContextIQ.rag.Source> sources
  ) {}
}

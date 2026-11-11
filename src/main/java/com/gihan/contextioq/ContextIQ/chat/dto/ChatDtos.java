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
    Boolean useDocuments,
    @Size(max = 50) java.util.List<@NotNull UUID> documentIds,
    @Size(max = 50) java.util.List<@NotNull UUID> folderIds
  ) {
    public SendRequest(String message, UUID conversationId, Boolean useDocuments, java.util.List<UUID> documentIds) { this(message, conversationId, useDocuments, documentIds, null); }
    public SendRequest(String message, UUID conversationId, Boolean useDocuments) { this(message, conversationId, useDocuments, null); }
    public SendRequest { useDocuments = Boolean.TRUE.equals(useDocuments); }
    public SendRequest(String message, UUID conversationId) { this(message, conversationId, false, null); }
  }

  public record SelectedDocument(UUID id, String filename, boolean available) {}
  public record SelectedFolder(UUID id, String name, boolean available, long documentCount) {}
  public record DocumentContext(boolean useDocuments, java.util.List<SelectedDocument> documents, java.util.List<SelectedFolder> folders) {
    public DocumentContext(boolean useDocuments, java.util.List<SelectedDocument> documents) { this(useDocuments, documents, java.util.List.of()); }
  }
  public record DocumentSelection(boolean useDocuments, @NotNull @Size(max = 50) java.util.List<@NotNull UUID> documentIds,
      @Size(max = 50) java.util.List<@NotNull UUID> folderIds) {
    public DocumentSelection(boolean useDocuments, java.util.List<UUID> documentIds) { this(useDocuments, documentIds, null); }
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

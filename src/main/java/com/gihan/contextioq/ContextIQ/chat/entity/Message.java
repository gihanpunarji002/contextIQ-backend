package com.gihan.contextioq.ContextIQ.chat.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class Message {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;
  private UUID workspaceId;
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MessageRole role;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(length = 20)
  private String mode;

  @Column(length = 120)
  private String model;

  @Column(nullable = false)
  private boolean retrievalUsed;

  public boolean isRetrievalUsed() { return retrievalUsed; }
  public void setRetrievalUsed(boolean value) { retrievalUsed = value; }

  private Integer inputTokens;
  private Integer outputTokens;

  public void setProvider(
    com.gihan.contextioq.ContextIQ.ai.service.AiChatService.Reply reply
  ) {
    mode = reply.mode();
    model = reply.model();
    inputTokens = reply.inputTokens();
    outputTokens = reply.outputTokens();
  }

  public String getMode() {
    return mode;
  }

  public String getModel() {
    return model;
  }

  public Integer getInputTokens() {
    return inputTokens;
  }

  public Integer getOutputTokens() {
    return outputTokens;
  }

  protected Message() {}

  public Message(
    Conversation c,
    MessageRole role,
    String content,
    Instant time
  ) {
    id = UUID.randomUUID();
    conversation = c;
    workspaceId = c.getWorkspaceId();
    organizationId = c.getOrganizationId();
    this.role = role;
    this.content = content;
    createdAt = time;
  }

  public UUID getId() {
    return id;
  }

  public MessageRole getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

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

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MessageRole role;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(nullable = false)
  private Instant createdAt;

  protected Message() {}

  public Message(
    Conversation c,
    MessageRole role,
    String content,
    Instant time
  ) {
    id = UUID.randomUUID();
    conversation = c;
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

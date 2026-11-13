package com.gihan.contextioq.ContextIQ.chat.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {

  @Id
  private UUID id;

  private UUID ownerId;
  private UUID workspaceId;
  private UUID organizationId;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected Conversation() {}

  public Conversation(String title, UUID ownerId) {
    this(title, ownerId, ownerId, ownerId);
  }

  public Conversation(String title, UUID ownerId, UUID workspaceId, UUID organizationId) {
    this.workspaceId = workspaceId;
    this.organizationId = organizationId;
    this.id = UUID.randomUUID();
    this.ownerId = ownerId;
    this.title = title;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void touch(Instant time) {
    updatedAt = time;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() { return workspaceId; }
  public UUID getOrganizationId() { return organizationId; }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getTitle() {
    return title;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}

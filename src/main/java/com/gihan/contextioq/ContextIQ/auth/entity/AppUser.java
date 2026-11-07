package com.gihan.contextioq.ContextIQ.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

  @Id
  private UUID id;

  @Column(nullable = false, length = 80)
  private String firstName;

  @Column(nullable = false, length = 80)
  private String lastName;

  @Column(nullable = false, length = 254, unique = true)
  private String email;

  @Column(length = 255)
  private String passwordHash;

  @Column(length = 255, unique = true)
  private String googleSubject;

  @Column(nullable = false)
  private Instant createdAt;

  protected AppUser() {}

  public AppUser(
    String firstName,
    String lastName,
    String email,
    String passwordHash,
    String googleSubject
  ) {
    id = UUID.randomUUID();
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.passwordHash = passwordHash;
    this.googleSubject = googleSubject;
    createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getGoogleSubject() {
    return googleSubject;
  }
}

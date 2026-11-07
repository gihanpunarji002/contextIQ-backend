package com.gihan.contextioq.ContextIQ.auth.dto;

import com.gihan.contextioq.ContextIQ.auth.entity.AppUser;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AuthDtos {

  private AuthDtos() {}

  public record RegisterRequest(
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 128) String password
  ) {}

  public record LoginRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 128) String password
  ) {}

  public record UserView(
    UUID id,
    String firstName,
    String lastName,
    String email,
    String provider
  ) {
    public static UserView from(AppUser u) {
      return new UserView(
        u.getId(),
        u.getFirstName(),
        u.getLastName(),
        u.getEmail(),
        u.getGoogleSubject() == null ? "LOCAL" : "GOOGLE"
      );
    }
  }
}

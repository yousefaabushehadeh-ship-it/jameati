package com.jameati.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AiChatRequest(
    @NotBlank @Size(max=1000) String message,
    @Size(max=8) List<@NotNull @Valid Message> history) {
  public AiChatRequest { history = history == null ? List.of() : List.copyOf(history); }
  public record Message(@NotNull Role role, @NotBlank @Size(max=2000) String content) {}
  public enum Role { user, assistant }
}

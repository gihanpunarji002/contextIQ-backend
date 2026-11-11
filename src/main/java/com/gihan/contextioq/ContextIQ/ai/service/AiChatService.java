package com.gihan.contextioq.ContextIQ.ai.service;

import java.util.List;

public interface AiChatService {
  record Turn(String role, String content) {}

  record Reply(
    String content,
    String mode,
    String model,
    Integer inputTokens,
    Integer outputTokens
  ) {}

  record Status(String mode, String model, boolean configured, boolean documentSearch, boolean documentSelection) {
    public Status(String mode, String model, boolean configured, boolean documentSearch) {
      this(mode, model, configured, documentSearch, true);
    }
    public Status(String mode, String model, boolean configured) {
      this(mode, model, configured, "OLLAMA".equals(mode));
    }
  }

  Reply reply(List<Turn> history);
  default Reply groundedReply(String question, java.util.List<com.gihan.contextioq.ContextIQ.rag.Source> sources) {
    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
      "Document chat requires the Ollama provider.");
  }
  Status status();
}

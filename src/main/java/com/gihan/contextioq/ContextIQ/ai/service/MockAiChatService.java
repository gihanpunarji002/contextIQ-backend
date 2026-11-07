package com.gihan.contextioq.ContextIQ.ai.service;

import org.springframework.stereotype.Service;

@Service
public class MockAiChatService implements AiChatService {

  public String reply(String message) {
    return "[MOCK] Your message was saved. This is a deterministic demonstration response. No documents were searched and no AI provider was called.";
  }

  public String mode() {
    return "MOCK";
  }
}

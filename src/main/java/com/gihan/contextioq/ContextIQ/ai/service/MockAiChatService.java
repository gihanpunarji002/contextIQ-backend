package com.gihan.contextioq.ContextIQ.ai.service;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "contextiq.ai.mode", havingValue = "MOCK")
public class MockAiChatService implements AiChatService {

  public Reply reply(List<Turn> history) {
    return new Reply(
      "[MOCK] Your message was saved. This is a deterministic demonstration response. No documents were searched and no AI provider was called.",
      "MOCK",
      null,
      null,
      null
    );
  }

  public Status status() {
    return new Status("MOCK", null, true);
  }
}

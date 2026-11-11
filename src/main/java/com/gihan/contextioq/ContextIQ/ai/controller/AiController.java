package com.gihan.contextioq.ContextIQ.ai.controller;

import com.gihan.contextioq.ContextIQ.ai.service.AiChatService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

  private final AiChatService ai;

  public AiController(AiChatService ai) {
    this.ai = ai;
  }

  @GetMapping("/status")
  public AiChatService.Status status() {
    return ai.status();
  }
}

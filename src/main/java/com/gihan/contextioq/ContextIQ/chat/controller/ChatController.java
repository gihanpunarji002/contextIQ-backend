package com.gihan.contextioq.ContextIQ.chat.controller;

import com.gihan.contextioq.ContextIQ.chat.dto.ChatDtos.*;
import com.gihan.contextioq.ContextIQ.chat.service.ChatService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

  private final ChatService service;

  public ChatController(ChatService service) {
    this.service = service;
  }

  @PostMapping("/messages")
  public SendResponse send(@Valid @RequestBody SendRequest request) {
    return service.send(request);
  }

  @GetMapping("/conversations/{id}/documents")
  public DocumentContext documents(@PathVariable UUID id) { return service.documentContext(id); }

  @PostMapping("/conversations/{id}/documents")
  public DocumentContext selectDocuments(@PathVariable UUID id, @Valid @RequestBody DocumentSelection request) {
    return service.selectDocuments(id, request);
  }

  @GetMapping("/conversations")
  public List<ConversationView> conversations() {
    return service.conversations();
  }

  @GetMapping("/conversations/{id}/messages")
  public List<MessageView> history(@PathVariable UUID id) {
    return service.history(id);
  }
}

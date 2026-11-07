package com.gihan.contextioq.ContextIQ.health;

import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {

  public record Health(String status, String application, String phase) {}

  @GetMapping("/api/v1/health")
  public Health health() {
    return new Health("UP", "ContextIQ", "PHASE_0");
  }
}

package com.gihan.contextioq.ContextIQ.dashboard.controller;

import com.gihan.contextioq.ContextIQ.dashboard.dto.DashboardSummary;
import com.gihan.contextioq.ContextIQ.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardController {

  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/dashboard/summary")
  public DashboardSummary summary() {
    return service.summary();
  }
}

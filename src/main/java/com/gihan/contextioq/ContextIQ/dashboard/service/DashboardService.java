package com.gihan.contextioq.ContextIQ.dashboard.service;

import com.gihan.contextioq.ContextIQ.dashboard.dto.DashboardSummary;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

  public DashboardSummary summary() {
    return new DashboardSummary("MOCK", 128, 24, 356, 3);
  }
}

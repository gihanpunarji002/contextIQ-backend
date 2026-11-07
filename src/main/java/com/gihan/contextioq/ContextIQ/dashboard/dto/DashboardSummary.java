package com.gihan.contextioq.ContextIQ.dashboard.dto;

public record DashboardSummary(
  String mode,
  int documents,
  int conversations,
  int queries,
  int workspaces
) {}

package com.pulsemonitor.model;

import java.time.Instant;

public class MonitorResult {
  private final String url;
  private String status = "UNKNOWN";
  private long latency = -1;
  private Instant lastChecked = null;
  private int successCount = 0;
  private int totalCheckCount = 0;

  public MonitorResult(String url) {
    this.url = url;
  }

  public String getUrl() { return url; }
  public String getStatus() { return status; }
  public long getLatency() { return latency; }
  public Instant getLastChecked() { return lastChecked; }

  public void update(String status, long latency) {
    this.status = status;
    this.latency = latency;
    this.lastChecked = Instant.now();
    this.totalCheckCount++;
    if ("UP".equals(status)) {
      this.successCount++;
    }
  }

  public double getUptimePercentage() {
    if (totalCheckCount == 0) {
      return 0.0;
    }
    return (successCount / (double) totalCheckCount) * 100;
  }

  public int getSuccessCount() {
    return successCount;
  }

  public int getTotalCheckCount() {
    return totalCheckCount;
  }


}

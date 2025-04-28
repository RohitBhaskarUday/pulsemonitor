package com.pulsemonitor.service;

import com.pulsemonitor.model.MonitorResult;
import com.pulsemonitor.repository.MonitorRepository;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class LatencyChecker {
  private final Vertx vertx;
  private final MonitorRepository monitorRepository;
  private final WebClient webClient;

  private final Map<String, MonitorResult> monitorResults = new ConcurrentHashMap<>();

  public LatencyChecker(Vertx vertx, MonitorRepository monitorRepository) {
    this.vertx = vertx;
    this.monitorRepository = monitorRepository;
    this.webClient = WebClient.create(vertx, new WebClientOptions().setFollowRedirects(true));
  }

  public void startPeriodicCheck(long intervalMs) {
    vertx.setPeriodic(intervalMs, id -> checkLatencyForAll());
  }

  private void checkLatencyForAll() {
    monitorRepository.getAllMonitoredUrlsFromDb().onSuccess(urlList -> {
      for (String url : urlList) {
        // ✅ Validate the URL here (correct place!)
        if (url == null || url.isBlank()) {
          System.err.println("[WARNING] Skipping invalid URL: " + url);
          continue;
        }

        MonitorResult result = monitorResults.computeIfAbsent(url, MonitorResult::new);
        long startTime = System.currentTimeMillis();

        webClient.getAbs(url)
          .send()
          .onSuccess(response -> {
            long latency = System.currentTimeMillis() - startTime;
            String status = (response.statusCode() >= 50 && response.statusCode() < 300) ? "UP" : "DOWN";

            // ✅ Update metrics:
            result.update(status, latency);

            // ✅ Store the result into check_history table
            monitorRepository.saveLatencyCheckResult(url, status, latency, result.getSuccessCount(), result.getTotalCheckCount(), result.getUptimePercentage());

            System.out.println("[✓] " + url + " is " + status +
              " (Latency: " + latency + " ms)" +
              " | Uptime: " + result.getUptimePercentage() + "%" +
              " | Handled by Thread: " + Thread.currentThread().getName());
          })
          .onFailure(err -> {
            monitorRepository.saveLatencyCheckResult(url, "DOWN", -1, result.getSuccessCount(), result.getTotalCheckCount(), result.getUptimePercentage());
            System.out.println("[✗] " + url + " is DOWN. Reason: " + err.getMessage() +
              " | Handled by Thread: " + Thread.currentThread().getName());
          });
      }
    }).onFailure(err -> {
      System.err.println("Failed to fetch monitored URLs: " + err.getMessage());
    });
  }
}

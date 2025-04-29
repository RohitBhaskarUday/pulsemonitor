package com.pulsemonitor.service;

import com.pulsemonitor.model.MonitorResult;
import com.pulsemonitor.repository.MonitorRepository;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class LatencyChecker {
  private final Vertx vertx;
  private final MonitorRepository monitorRepository;
  private final WebClient webClient;
  // Batch size - tune this (5 or 10)
  private static final int BATCH_SIZE = 8;
  private final Map<String, MonitorResult> monitorResults = new ConcurrentHashMap<>();

  public LatencyChecker(Vertx vertx, MonitorRepository monitorRepository) {
    this.vertx = vertx;
    this.monitorRepository = monitorRepository;
    this.webClient = WebClient.create(vertx, new WebClientOptions()
      .setFollowRedirects(true)
      .setMaxHeaderSize(16 * 1024) // Set 16KB allowed header size
    );
  }

  public void startPeriodicCheck(long intervalMs) {
    vertx.setPeriodic(intervalMs, id -> checkLatencyInBatches());
  }

  private void checkLatencyInBatches() {
    monitorRepository.getAllMonitoredUrlsFromDb().onSuccess(urlList -> {
      List<String> urls = new ArrayList<>(urlList);
      processBatch(urls, 0);
    }).onFailure(err -> {
      System.err.println("Failed to fetch monitored URLs: " + err.getMessage());
    });
  }

  private void processBatch(List<String> urls, int startIndex) {
    int endIndex = Math.min(startIndex + BATCH_SIZE, urls.size());
    List<String> batch = urls.subList(startIndex, endIndex);

    List<io.vertx.core.Future> futures = new ArrayList<>();

    for (String url : batch) {
      if (url == null || url.isBlank()) {
        System.err.println("[WARNING] Skipping invalid URL: " + url);
        continue;
      }

      MonitorResult result = monitorResults.computeIfAbsent(url, MonitorResult::new);
      long startTime = System.currentTimeMillis();

      var future = webClient.getAbs(url)
        .send()
        .onSuccess(response -> {
          long latency = System.currentTimeMillis() - startTime;
          String status = (response.statusCode() >= 200 && response.statusCode() < 300) ? "UP" : "DOWN";

          result.update(status, latency);
          monitorRepository.saveLatencyCheckResult(url, status, latency, result.getSuccessCount(), result.getTotalCheckCount(), result.getUptimePercentage());

          System.out.println("[✓] " + url + " is " + status +
            " (Latency: " + latency + " ms)" +
            " | Uptime: " + result.getUptimePercentage() + "%" +
            " | Handled by Thread: " + Thread.currentThread().getName());
        })
        .onFailure(err -> {
          result.update("DOWN", -1);
          monitorRepository.saveLatencyCheckResult(url, "DOWN", -1, result.getSuccessCount(), result.getTotalCheckCount(), result.getUptimePercentage());
          System.out.println("[✗] " + url + " is DOWN. Reason: " + err.getMessage() +
            " | Handled by Thread: " + Thread.currentThread().getName());
        });

      futures.add(future);
    }

    // After all requests in this batch are complete, process next batch
    io.vertx.core.CompositeFuture.all(futures).onComplete(ar -> {
      if (endIndex < urls.size()) {
        processBatch(urls, endIndex);
      } else {
        System.out.println("✅ Completed latency check for all URLs.");
      }
    });
  }
}

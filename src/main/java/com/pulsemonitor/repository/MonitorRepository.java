package com.pulsemonitor.repository;


import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

public class MonitorRepository {

  private final JDBCClient jdbcClient;

  public MonitorRepository(JDBCClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  // ✅ Add URL (with duplicate prevention)
  public Future<Void> addUrl(String url) {
    Promise<Void> promise = Promise.promise();

    String insertSql = "INSERT INTO monitored_urls (url) VALUES (?)";

    jdbcClient.getConnection(connHandler -> {
      if (connHandler.succeeded()) {
        SQLConnection connection = connHandler.result();
        System.out.println("++++++"+ url);
        connection.updateWithParams(insertSql, new JsonArray().add(url), res -> {
          connection.close();

          if (res.succeeded()) {
            System.out.println("success inserted URL "+ url);
            promise.complete();
          } else {
            // Check if the error is because of duplicate URL
            if (res.cause().getMessage().contains("Unique index or primary key violation")) {
              promise.fail("URL is already being monitored");
            } else {

              System.out.println("Error inserted URL "+ url + "Reason "+res.cause().getMessage());
              promise.fail(res.cause());
            }
          }
        });
      } else {
        promise.fail(connHandler.cause());
      }
    });

    return promise.future();
  }



  // 🟡 Delete URL
  public Future<Boolean> deleteUrl(String url) {
    Promise<Boolean> promise = Promise.promise();
    String deleteSql = "DELETE FROM monitored_urls WHERE url = ?";

    jdbcClient.getConnection(connHandler -> {
      if (connHandler.succeeded()) {
        SQLConnection connection = connHandler.result();
        connection.updateWithParams(deleteSql, new JsonArray().add(url), res -> {
          connection.close();
          if (res.succeeded()) {
            boolean deleted = res.result().getUpdated() > 0;
            promise.complete(deleted);
          } else {
            promise.fail(res.cause());
          }
        });
      } else {
        promise.fail(connHandler.cause());
      }
    });

    return promise.future();
  }

  //get all
  public Future<JsonArray> getAllMonitoredUrls() {
    Promise<JsonArray> promise = Promise.promise();

    String query = "SELECT ch.id AS history_id, mu.url AS url, ch.checked_at, ch.uptime_percentage " +
      "FROM check_history ch " +
      "JOIN monitored_urls mu ON ch.url_id = mu.id " +
      "ORDER BY ch.checked_at DESC " +
      "LIMIT 10";  // 🟡 Limit to 10 most recent entries


    jdbcClient.getConnection(connHandler -> {
      if (connHandler.succeeded()) {
        SQLConnection connection = connHandler.result();
        connection.query(query, res -> {
          connection.close();
          if (res.succeeded()) {
            JsonArray results = new JsonArray();
            res.result().getRows().forEach(row -> {
              JsonObject json = new JsonObject()
                .put("history_id", row.getLong("HISTORY_ID"))
                .put("url", row.getString("URL"))
                .put("created_at", row.getString("CHECKED_AT"))
                .put("uptime_percentage", row.getDouble("UPTIME_PERCENTAGE"));
              results.add(json);
            });
            promise.complete(results);
          } else {
            promise.fail(res.cause());
          }
        });
      } else {
        promise.fail(connHandler.cause());
      }
    });

    return promise.future();
  }


  public Future<List<String>> getAllMonitoredUrlsFromDb() {
    Promise<List<String>> promise = Promise.promise();
    String query = "SELECT url AS url FROM monitored_urls";

    jdbcClient.getConnection(connHandler -> {
      if (connHandler.succeeded()) {
        SQLConnection connection = connHandler.result();
        connection.query(query, res -> {
          connection.close();
          if (res.succeeded()) {
            List<String> urls = res.result().getRows().stream()
              .map(row -> {
                //System.out.println("Row keys: "+ row.fieldNames()); for debugging
                return row.getString("URL"); // this URL was an issue
              })
              .collect(Collectors.toList());
            System.out.println("List of the URLS "+urls);
            promise.complete(urls);
          } else {
            promise.fail(res.cause());
          }
        });
      } else {
        promise.fail(connHandler.cause());
      }
    });

    return promise.future();
  }

  public void saveLatencyCheckResult(String url, String status, double latency, int successCount, int totalCheckCount, double uptimePercentage) {
    String findUrlIdQuery = "SELECT id FROM monitored_urls WHERE url = ?";
    String insertHistoryQuery = "INSERT INTO check_history (url_id, status, latency, success_count, total_check_count, uptime_percentage) VALUES (?, ?, ?, ?, ?, ?)";

    jdbcClient.getConnection(connHandler -> {
      if (connHandler.succeeded()) {
        SQLConnection connection = connHandler.result();
        connection.queryWithParams(findUrlIdQuery, new JsonArray().add(url), res -> {
          if (res.succeeded() && !res.result().getRows().isEmpty()) {
            Long urlId = res.result().getRows().get(0).getLong("ID");

            connection.updateWithParams(insertHistoryQuery,
              new JsonArray().add(urlId).add(status).add(latency).add(successCount).add(totalCheckCount).add(uptimePercentage),
              insertRes -> {
                if (!insertRes.succeeded()) {
                  System.out.println(" success count "+ successCount);
                  System.err.println("Failed to insert latency record: " + insertRes.cause().getMessage());
                }
                connection.close();
              });
          } else {
            System.err.println("URL not found while saving latency result: " + url);
            connection.close();
          }
        });
      } else {
        System.err.println("DB Connection error: " + connHandler.cause().getMessage());
      }
    });
  }



}

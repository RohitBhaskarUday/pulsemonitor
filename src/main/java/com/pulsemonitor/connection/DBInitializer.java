package com.pulsemonitor.connection;

import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

public class DBInitializer {
  public static void initializeDatabase(JDBCClient jdbcClient) {
    String createUrlsTable = "CREATE TABLE IF NOT EXISTS monitored_urls (" +
      "id IDENTITY PRIMARY KEY, " +
      "url VARCHAR(255) NOT NULL UNIQUE, " +
      "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
      ");";

    String createHistoryTable = "CREATE TABLE IF NOT EXISTS check_history (" +
      "id IDENTITY PRIMARY KEY, " +
      "url_id BIGINT NOT NULL, " +
      "status VARCHAR(10), " +
      "latency BIGINT, " +
      "success_count BIGINT, " +
      "total_check_count BIGINT," +
      "checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
      "uptime_percentage DOUBLE, "+
      "FOREIGN KEY (url_id) REFERENCES monitored_urls(id) ON DELETE CASCADE" +
      ");";

    jdbcClient.getConnection(connectionHandler -> {
      if (connectionHandler.succeeded()) {
        SQLConnection connection = connectionHandler.result();
        connection.execute(createUrlsTable, res1 -> {
          if (res1.succeeded()) {
            connection.execute(createHistoryTable, res2 -> {
              if (res2.succeeded()) {
                System.out.println("✅ Tables created successfully!");
              } else {
                res2.cause().printStackTrace();
              }
              connection.close();
            });
          } else {
            res1.cause().printStackTrace();
            connection.close();
          }
        });
      } else {
        connectionHandler.cause().printStackTrace();
      }
    });
  }
}

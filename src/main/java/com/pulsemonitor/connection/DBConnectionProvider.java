package com.pulsemonitor.connection;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

public class DBConnectionProvider {

  public static JDBCClient createJdbcClient(Vertx vertx) {
    String jdbcUrl = "jdbc:h2:./data/pulse_monitor_db";

    JsonObject config = new JsonObject()
      .put("url", jdbcUrl)
      .put("driver_class", "org.h2.Driver")
      .put("max_pool_size", 10);

    return JDBCClient.createShared(vertx, config);
  }
}

package com.pulsemonitor;

import com.pulsemonitor.connection.DBConnectionProvider;
import com.pulsemonitor.connection.DBInitializer;
import com.pulsemonitor.repository.MonitorRepository;
import com.pulsemonitor.service.LatencyChecker;
//import com.pulsemonitor.util.JsonUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

import java.net.MalformedURLException;
import java.net.URL;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    Router router = Router.router(vertx);
    //this enables the json body to be parsed
    router.route().handler(BodyHandler.create());

    //Initialize DB only after Vert.x is ready
    JDBCClient jdbcClient = DBConnectionProvider.createJdbcClient(vertx);
    DBInitializer.initializeDatabase(jdbcClient);
    MonitorRepository monitorRepository = new MonitorRepository(jdbcClient);


    router.get("/health").handler(ctx ->
      ctx.response()
        .putHeader("content-type", "application/json")
        .end("Welcome to the application")
    );

    // GET /monitor - List all monitored URLs
    router.get("/monitor").handler(ctx -> {
      monitorRepository.getAllMonitoredUrls().onSuccess(jsonArray -> {
        ctx.response().putHeader("content-type", "application/json")
          .end(jsonArray.encodePrettily());
      }).onFailure(err -> {
        ctx.response().setStatusCode(500)
          .putHeader("content-type", "application/json")
          .end("{\"error\": \"" + err.getMessage() + "\"}");
      });
    });

    // POST /monitor - Add the URLs that you are checking
    router.post("/monitor").handler(ctx ->{

      JsonObject body = ctx.body().asJsonObject();
      System.out.println("This is the body " + body); //log
      String url = body.getString("url");

      if (url == null || url.isBlank()) {
        ctx.response().setStatusCode(400)
          .putHeader("content-type", "application/json")
          .end("{\"error\": \"Missing or empty URL in request body\"}");
        return;
      }

      try {
        new URL(url);  // Validate the URL format
      } catch (MalformedURLException e) {
        ctx.response().setStatusCode(400)
          .putHeader("content-type", "application/json")
          .end("{\"error\": \"Invalid URL format. Please use http:// or https://\"}");
        return;
      }

      monitorRepository.addUrl(url).onSuccess(x-> {
        ctx.response()
          .putHeader("content-type","application/json")
          .end("{\"message\": \"URL added for monitoring: " + url + "\"}");
      }).onFailure(err->{
        ctx.response().setStatusCode(409)
          .putHeader("content-type", "application/json")
          .end("{\"error\": \"" + err.getMessage() + "\"}");
      });


    });


    // DELETE /monitor?url=https://www.google.com
    router.delete("/monitor").handler(ctx -> {
      String url = ctx.queryParams().get("url");

      System.out.println(" the url from the endpoint is "+ url);

      if (url == null || url.isEmpty()) {
        ctx.response().setStatusCode(400)
          .putHeader("content-type", "application/json")
          .end("{\"error\": \"Missing URL in query parameter\"}");
        return;
      }

      System.out.println("This is the url going to be deleted "+url);

      monitorRepository.deleteUrl(url).onSuccess(deleted -> {
        if (deleted) {
          ctx.response().putHeader("content-type", "application/json")
            .end("{\"message\": \"URL removed from monitoring: " + url + "\"}");
        } else {
          ctx.response().setStatusCode(404)
            .putHeader("content-type", "application/json")
            .end("{\"error\": \"URL not found in monitoring list\"}");
        }
      }).onFailure(err -> {
        ctx.response()
          .setStatusCode(500)
          .putHeader("content-type", "application/json")
          .end("{\"error\": \"Internal server error\"}");
        err.printStackTrace();
      });
    });


    // ✅ Start periodic latency checking every 10 seconds (10,000 ms)
    // private final MonitorRepository monitorRepository;
    LatencyChecker latencyChecker = new LatencyChecker(vertx, monitorRepository);
    latencyChecker.startPeriodicCheck(10_000);  // Check every 10 seconds


    // Bind router to HTTP server
    vertx.createHttpServer().requestHandler(router)
      .listen(8888).onComplete(http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("HTTP server started on port 8888");
      } else {
        startPromise.fail(http.cause());
      }
    });
  }
}

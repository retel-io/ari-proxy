package io.retel.ariproxy.health;

import static akka.http.javadsl.server.Directives.*;
import static akka.http.javadsl.server.Directives.pathPrefix;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.retel.ariproxy.boundary.callcontext.CallContextProvider;
import io.retel.ariproxy.health.api.HealthReport;
import io.retel.ariproxy.health.api.HealthResponse;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CallContextProvider.class);
  private static final ObjectWriter writer = new ObjectMapper().writer();

  private HealthService() {
    throw new IllegalStateException("Utility class");
  }

  public static ServerBinding run(
      final ActorSystem<?> system,
      final Collection<Supplier<CompletableFuture<HealthReport>>> healthSuppliers,
      final int httpPort) {
    try {
      final String address = "0.0.0.0";
      final ServerBinding binding =
          Http.get(system)
              .newServerAt(address, httpPort)
              .bind(buildHandlerProvider(healthSuppliers))
              .toCompletableFuture()
              .get();
      LOGGER.info("HTTP server online at http://{}:{}/...", address, httpPort);

      return binding;
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException("Unable to start http server", e);
    }
  }

  private static Route buildHandlerProvider(
      final Collection<Supplier<CompletableFuture<HealthReport>>> healthSuppliers) {
    return pathPrefix(
        "health",
        () ->
            concat(
                path("smoke", () -> get(() -> complete(StatusCodes.OK))),
                get(() -> handleHealthBaseRoute(healthSuppliers))));
  }

  private static Route handleHealthBaseRoute(
      final Collection<Supplier<CompletableFuture<HealthReport>>> healthSuppliers) {
    return completeWithFuture(
        generateHealthReport(healthSuppliers).thenApply(HealthService::healthReportToHttpResponse));
  }

  private static CompletableFuture<HealthReport> generateHealthReport(
      final Collection<Supplier<CompletableFuture<HealthReport>>> healthSuppliers) {
    return CompletableFuture.supplyAsync(
        () ->
            healthSuppliers.parallelStream()
                .map(
                    supplier -> {
                      try {
                        return supplier.get().get();
                      } catch (InterruptedException | ExecutionException e) {
                        LOGGER.warn("Unable to determine health status", e);
                        return HealthReport.error(
                            "Unable to determine health status: " + e.getMessage());
                      }
                    })
                .reduce(HealthReport.empty(), HealthReport::merge));
  }

  private static HttpResponse healthReportToHttpResponse(final HealthReport r) {
    try {
      final String payload =
          writer.writeValueAsString(HealthResponse.fromErrors(r.errors().toJavaList()));
      return HttpResponse.create()
          .withStatus(StatusCodes.OK)
          .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, payload));
    } catch (JsonProcessingException e) {
      LOGGER.error("Unable to serialize report", e);
      return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
    }
  }
}

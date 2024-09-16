package io.retel.ariproxy.health;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.HttpCredentials;
import com.typesafe.config.Config;
import io.retel.ariproxy.health.api.HealthReport;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

public class AriConnectionCheck {

  public static final String URI = "uri";
  public static final String PASSWORD = "password";
  public static final String USER = "user";
  public static final String ASTERISK_PING_ROUTE = "/asterisk/ping";

  private AriConnectionCheck() {
    throw new IllegalStateException("Utility class");
  }

  public static Behavior<ReportAriConnectionHealth> create(final Config restConfig) {

    return Behaviors.setup(
        context ->
            Behaviors.receive(ReportAriConnectionHealth.class)
                .onMessage(
                    ReportAriConnectionHealth.class,
                    message -> reportHealth(context, restConfig, message))
                .build());
  }

  private static Behavior<ReportAriConnectionHealth> reportHealth(
      final ActorContext<ReportAriConnectionHealth> context,
      final Config restConfig,
      final ReportAriConnectionHealth message) {
    provideHealthReport(context, restConfig)
        .thenAccept(healthReport -> message.replyTo().tell(healthReport));

    return Behaviors.same();
  }

  private static CompletableFuture<HealthReport> provideHealthReport(
      final ActorContext<ReportAriConnectionHealth> context, final Config restConfig) {

    final String restUri = restConfig.getString(URI);
    final String restUser = restConfig.getString(USER);
    final String restPassword = restConfig.getString(PASSWORD);

    final HttpRequest httpRequest =
        HttpRequest.create()
            .withMethod(HttpMethods.GET)
            .addCredentials(HttpCredentials.createBasicHttpCredentials(restUser, restPassword))
            .withUri(restUri + ASTERISK_PING_ROUTE);

    final CompletionStage<HttpResponse> responseCompletionStage =
        Http.get(context.getSystem()).singleRequest(httpRequest);
    return responseCompletionStage
        .handle(
            (httpResponse, error) -> {
              if (error != null) {
                return HealthReport.error(
                    AriConnectionCheck.class,
                    "error during connection to ARI: %s".formatted(error.getMessage()));
              }

              if (httpResponse.status().equals(StatusCodes.OK)) {
                return HealthReport.ok();

              } else {
                return HealthReport.error(
                    AriConnectionCheck.class,
                    "unexpected return value: %s".formatted(httpResponse.status()));
              }
            })
        .toCompletableFuture();
  }

  public record ReportAriConnectionHealth(ActorRef<HealthReport> replyTo) {

    @Override
    public String toString() {
      return ReflectionToStringBuilder.toString(this, SHORT_PREFIX_STYLE);
    }
  }
}

package io.retel.ariproxy.boundary.commandsandresponses;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.metrics.StopCallSetupTimer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import scala.concurrent.duration.Duration;

class AriCommandResponseKafkaProcessorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final String TEST_SYSTEM = this.getClass().getSimpleName();
  private ActorSystem system;

  @AfterEach
  void teardown() {
    TestKit.shutdownActorSystem(system);
    system.terminate();
  }

  @BeforeEach
  void setup() {
    system = ActorSystem.create(TEST_SYSTEM);
  }

  @Test()
  void properlyHandleInvalidCommandMessage() {
    final TestKit kafkaProducer = new TestKit(system);
    final TestKit metricsService = new TestKit(system);
    final TestKit callContextProvider = new TestKit(system);

    final ConsumerRecord<String, String> consumerRecord =
        new ConsumerRecord<>("topic", 0, 0, "key", "NOT JSON");
    final Source<ConsumerRecord<String, String>, NotUsed> source = Source.single(consumerRecord);
    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.<ProducerRecord<String, String>>ignore()
            .mapMaterializedValue(q -> NotUsed.getInstance());

    AriCommandResponseKafkaProcessor.commandResponseProcessing()
        .on(system)
        .withHandler(requestAndContext -> Http.get(system).singleRequest(requestAndContext._1))
        .withCallContextProvider(callContextProvider.getRef())
        .withMetricsService(metricsService.getRef())
        .from(source)
        .to(sink)
        .run();

    kafkaProducer.expectNoMsg(Duration.apply(250, TimeUnit.MILLISECONDS));
  }

  @ParameterizedTest
  @ArgumentsSource(ResponseArgumentsProvider.class)
  void testCommandResponseProcessing(
      final String commandJsonFilename,
      final HttpResponse asteriskResponse,
      final String expectedResponseJsonFilename,
      final String resourceIdExpectedToRegisterInCallContext)
      throws Exception {

    final TestKit kafkaProducer = new TestKit(system);
    final TestKit metricsService = new TestKit(system);
    final TestKit callContextProvider = new TestKit(system);

    final ConsumerRecord<String, String> consumerRecord =
        new ConsumerRecord<>("topic", 0, 0, "none", loadJsonAsString(commandJsonFilename));

    final Source<ConsumerRecord<String, String>, NotUsed> source = Source.single(consumerRecord);
    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            kafkaProducer.getRef(), new ProducerRecord<String, String>("topic", "endMessage"));

    AriCommandResponseKafkaProcessor.commandResponseProcessing()
        .on(system)
        .withHandler(r -> CompletableFuture.supplyAsync(() -> asteriskResponse))
        .withCallContextProvider(callContextProvider.getRef())
        .withMetricsService(metricsService.getRef())
        .from(source)
        .to(sink)
        .run();

    Optional.ofNullable(resourceIdExpectedToRegisterInCallContext)
        .ifPresent(
            resourceId -> {
              final RegisterCallContext registerCallContext =
                  callContextProvider.expectMsgClass(RegisterCallContext.class);
              assertThat(registerCallContext.callContext(), is("CALL_CONTEXT"));
              assertThat(registerCallContext.resourceId(), is(resourceId));
              callContextProvider.reply(new CallContextProvided("CALL CONTEXT"));
            });

    final StopCallSetupTimer stopCallSetupTimer =
        metricsService.expectMsgClass(StopCallSetupTimer.class);
    assertThat(stopCallSetupTimer.getCallcontext(), is("CALL_CONTEXT"));
    assertThat(stopCallSetupTimer.getApplication(), is("test-app"));

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> responseRecord =
        kafkaProducer.expectMsgClass(ProducerRecord.class);
    assertThat(responseRecord.topic(), is("eventsAndResponsesTopic"));
    assertThat(responseRecord.key(), is("CALL_CONTEXT"));
    assertEquals(
        OBJECT_MAPPER.readTree(loadJsonAsString(expectedResponseJsonFilename)),
        OBJECT_MAPPER.readTree(responseRecord.value()));

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> endMsg =
        kafkaProducer.expectMsgClass(ProducerRecord.class);
    assertThat(endMsg.topic(), is("topic"));
    assertThat(endMsg.value(), is("endMessage"));
  }

  static class ResponseArgumentsProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "messages/commands/channelPlaybackCommand.json",
              HttpResponse.create().withStatus(StatusCodes.OK).withEntity("{ \"key\":\"value\" }"),
              "messages/responses/channelPlaybackResponse.json",
              "c4958563-1ba4-4f2f-a60f-626a624bf0e6"),
          Arguments.of(
              "messages/commands/channelAnswerCommand.json",
              HttpResponse.create().withStatus(StatusCodes.NO_CONTENT),
              "messages/responses/channelAnswerResponse.json",
              null),
          Arguments.of(
              "messages/commands/channelAnswerCommandWithoutCommandId.json",
              HttpResponse.create().withStatus(StatusCodes.NO_CONTENT),
              "messages/responses/channelAnswerResponseWithoutCommandId.json",
              null));
    }
  }

  private static String loadJsonAsString(final String fileName) throws IOException {
    final ClassLoader classLoader = AriCommandResponseKafkaProcessorTest.class.getClassLoader();
    final File file = new File(classLoader.getResource(fileName).getFile());
    return new String(Files.readAllBytes(file.toPath()));
  }
}

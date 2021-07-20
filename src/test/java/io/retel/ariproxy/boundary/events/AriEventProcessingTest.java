package io.retel.ariproxy.boundary.events;

import static io.retel.ariproxy.boundary.events.StasisEvents.applicationReplacedEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.invalidEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.playbackFinishedEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.recordingFinishedEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.stasisStartEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.unknownEvent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.NotUsed;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.scaladsl.model.ws.TextMessage.Strict;
import akka.pattern.StatusReply;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.MemoryKeyValueStore;
import io.retel.ariproxy.boundary.callcontext.TestableCallContextProvider;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.metrics.IncreaseCounter;
import io.retel.ariproxy.metrics.StartCallSetupTimer;
import io.vavr.collection.Seq;
import io.vavr.concurrent.Future;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AriEventProcessingTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(AriEventProcessingTest.class);

  private static final ActorTestKit testKit =
      ActorTestKit.create("testKit", ConfigFactory.defaultApplication());

  private static final String fakeCommandsTopic = "commands";
  private static final String fakeEventsAndResponsesTopic = "events-and-responses";
  private static final String CALL_CONTEXT = "CALL_CONTEXT";

  @Test()
  @DisplayName("Ensure an UnparsableMessageException is thrown for a bogus message.")
  void generateProducerRecordFromEventHandlesUnparseableMessage() {
    assertThrows(
        RuntimeException.class,
        () -> {
          AriEventProcessing.generateProducerRecordFromEvent(
              fakeCommandsTopic,
              fakeEventsAndResponsesTopic,
              new Strict(invalidEvent),
              testKit.<CallContextProviderMessage>createTestProbe().ref(),
              LOGGER,
              () -> LOGGER.error("Shutdown triggered"),
              testKit.system());
        });
  }

  @Test
  void throwRuntimeExceptionWhenEncounteringAnUnknownEvent() {
    assertThrows(
        RuntimeException.class,
        () ->
            AriEventProcessing.generateProducerRecordFromEvent(
                fakeCommandsTopic,
                fakeEventsAndResponsesTopic,
                new Strict(unknownEvent),
                testKit.<CallContextProviderMessage>createTestProbe().ref(),
                LOGGER,
                () -> LOGGER.error("Shutdown triggered"),
                testKit.system()));
  }

  @DisplayName(
      "Verify processing of both channel and playback events results in the expected kafka producer record")
  @ParameterizedTest
  @ValueSource(strings = {stasisStartEvent, playbackFinishedEvent, recordingFinishedEvent})
  void generateProducerRecordFromAllAriMessageTypes(final String ariEvent) {
    final TestProbe<CallContextProviderMessage> callContextProviderProbe =
        testKit.createTestProbe(CallContextProviderMessage.class);
    final ActorRef<CallContextProviderMessage> callContextProvider =
        testKit.spawn(
            Behaviors.receive(CallContextProviderMessage.class)
                .onMessage(
                    ProvideCallContext.class,
                    msg -> {
                      callContextProviderProbe.ref().tell(msg);
                      msg.replyTo()
                          .tell(StatusReply.success(new CallContextProvided(CALL_CONTEXT)));

                      return Behaviors.same();
                    })
                .build());

    final Future<Source<ProducerRecord<String, String>, NotUsed>> wsToKafkaProcessor =
        Future.of(
            () ->
                AriEventProcessing.generateProducerRecordFromEvent(
                    fakeCommandsTopic,
                    fakeEventsAndResponsesTopic,
                    new Strict(ariEvent),
                    callContextProvider,
                    LOGGER,
                    () -> LOGGER.error("Shutdown triggered"),
                    testKit.system()));

    callContextProviderProbe.expectMessageClass(ProvideCallContext.class);
    final ProducerRecord<String, String> record =
        wsToKafkaProcessor
            .flatMap(
                source ->
                    Future.fromCompletableFuture(
                        source.runWith(Sink.last(), testKit.system()).toCompletableFuture()))
            .await()
            .get();

    assertThat(record.key(), is(CALL_CONTEXT));
    assertThat(record.topic(), is(fakeEventsAndResponsesTopic));
  }

  @ParameterizedTest
  @EnumSource(
      value = AriMessageType.class,
      mode = Mode.EXCLUDE,
      names = {"STASIS_START", "STASIS_END"})
  void verifyNoMetricsAreGatheredForTheSpecifiedEventTypes(AriMessageType type) {
    final Seq<MetricsGatherer> decision = AriEventProcessing.determineMetricsGatherer(type);

    assertThat(
        ((IncreaseCounter) decision.get(0).withCallContextSupplier(() -> CALL_CONTEXT)).getName(),
        is(type.name()));
  }

  @Test
  void checkApplicationReplacedHandlerIsTriggered() throws InterruptedException {
    final AtomicBoolean didTriggerShutdown = new AtomicBoolean(false);
    final CountDownLatch waitForShutdownTriggered = new CountDownLatch(1);
    final Future<Source<ProducerRecord<String, String>, NotUsed>> wsToKafkaProcessor =
        Future.of(
            () ->
                AriEventProcessing.generateProducerRecordFromEvent(
                    fakeCommandsTopic,
                    fakeEventsAndResponsesTopic,
                    new Strict(applicationReplacedEvent),
                    testKit.<CallContextProviderMessage>createTestProbe().ref(),
                    LOGGER,
                    () -> {
                      LOGGER.error("Shutdown triggered");
                      didTriggerShutdown.set(true);
                      waitForShutdownTriggered.countDown();
                    },
                    testKit.system()));

    waitForShutdownTriggered.await(1000, TimeUnit.MILLISECONDS);
    assertTrue(didTriggerShutdown.get(), "shutdown was triggered");
    assertThat(wsToKafkaProcessor.await().get(), is(Source.empty()));
  }

  @Test
  void verifyTheRequiredMetricsAreGatheredForStasisStart() {
    final Seq<MetricsGatherer> metricsGatherers =
        AriEventProcessing.determineMetricsGatherer(AriMessageType.STASIS_START);

    metricsGatherers.forEach(
        metricsGatherer -> {
          final Object metricsReq = metricsGatherer.withCallContextSupplier(() -> CALL_CONTEXT);
          System.out.println(metricsReq);
        });

    final Seq<Object> metricsRequests =
        metricsGatherers.map(
            metricsGatherer -> metricsGatherer.withCallContextSupplier(() -> CALL_CONTEXT));

    assertThat(len(metricsGatherers), is(3));

    final IncreaseCounter eventTypeCounter = (IncreaseCounter) metricsRequests.get(0);
    final IncreaseCounter callsStartedCounter = (IncreaseCounter) metricsRequests.get(1);
    final StartCallSetupTimer callSetupTimer = (StartCallSetupTimer) metricsRequests.get(2);

    assertThat(eventTypeCounter.getName(), is(AriMessageType.STASIS_START.name()));
    assertThat(callsStartedCounter.getName(), is("CallsStarted"));
    assertThat(callSetupTimer.getCallContext(), is(CALL_CONTEXT));
  }

  @Test
  void verifyTheRequiredMetricsAreGatheredForStasisEnd() {
    final Seq<MetricsGatherer> metricsGatherers =
        AriEventProcessing.determineMetricsGatherer(AriMessageType.STASIS_END);

    final Seq<Object> metricsRequests =
        metricsGatherers.map(
            metricsGatherer -> metricsGatherer.withCallContextSupplier(() -> CALL_CONTEXT));

    assertThat(len(metricsGatherers), is(2));

    final IncreaseCounter eventTypeCounter = (IncreaseCounter) metricsRequests.get(0);
    final IncreaseCounter callsEndedCounter = (IncreaseCounter) metricsRequests.get(1);

    assertThat(eventTypeCounter.getName(), is(AriMessageType.STASIS_END.name()));
    assertThat(callsEndedCounter.getName(), is("CallsEnded"));
  }

  @Test
  void verifyGetCallContextWorksAsExpected() {
    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(
            testKit, new MemoryKeyValueStore("RESOURCE_ID123", CALL_CONTEXT));
    final Try<String> callContext =
        AriEventProcessing.getCallContext(
            "RESOURCE_ID123",
            callContextProvider.ref(),
            Option.none(),
            ProviderPolicy.CREATE_IF_MISSING,
            testKit.system());

    final ProvideCallContext provideCallContext =
        callContextProvider.probe().expectMessageClass(ProvideCallContext.class);
    assertThat(provideCallContext.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    assertThat(provideCallContext.resourceId(), is("RESOURCE_ID123"));

    assertThat(callContext.get(), is(CALL_CONTEXT));
  }

  @Test
  void verifyGetCallContextReturnsAFailedTryIfNoCallContextCanBeProvided() {
    assertThat(
        AriEventProcessing.getCallContext(
                "RESOURCE_ID",
                testKit.<CallContextProviderMessage>createTestProbe().ref(),
                Option.none(),
                ProviderPolicy.CREATE_IF_MISSING,
                testKit.system())
            .isFailure(),
        is(true));
  }

  @ParameterizedTest
  @CsvSource({"/type,StasisStart", "/channel/id,1532965104.0"})
  void verifyPropertiesCanBeExtractedFromAChannelMessage(String path, String expected) {
    String actual =
        AriEventProcessing.getValueFromMessageByPath(new Strict(stasisStartEvent), path).get();
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @CsvSource({"/type,PlaybackFinished", "/playback/id,072f6484-f781-405b-8c30-0a9a4496d14d"})
  void verifyPropertiesCanBeExtractedFromAPlaybackMessage(String path, String expected) {
    String actual =
        AriEventProcessing.getValueFromMessageByPath(new Strict(playbackFinishedEvent), path).get();
    assertEquals(expected, actual);
  }

  @Test
  void verifyGetValueFromMessageByPathHandlesInvalidMessagesProperly() {
    assertTrue(
        AriEventProcessing.getValueFromMessageByPath(new Strict(invalidEvent), "/smth").isEmpty());
  }

  private static int len(Seq<?> seq) {
    return seq.foldLeft(0, (acc, item) -> acc + 1);
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }
}

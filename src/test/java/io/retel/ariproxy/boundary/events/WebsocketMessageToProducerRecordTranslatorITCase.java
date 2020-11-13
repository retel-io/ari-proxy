package io.retel.ariproxy.boundary.events;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.ws.Message;
import akka.http.scaladsl.model.ws.TextMessage.Strict;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.metrics.IncreaseCounter;
import io.retel.ariproxy.metrics.StartCallSetupTimer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebsocketMessageToProducerRecordTranslatorITCase {

  private static final CallContextProvided CALL_CONTEXT_PROVIDED =
      new CallContextProvided("CALL_CONTEXT_PROVIDED");
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

  @Test
  void verifyProcessingPipelineWorksAsExpectedForBogusMessages() {

    final TestKit catchAllProbe = new TestKit(system);

    final Source<Message, NotUsed> source = Source.single(new Strict("invalid message from ws"));
    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            catchAllProbe.getRef(), new ProducerRecord<String, String>("none", "completed"));

    WebsocketMessageToProducerRecordTranslator.eventProcessing(
            system,
            () -> catchAllProbe.getRef().tell("Application replaced", catchAllProbe.getRef()),
            catchAllProbe.getRef(),
            catchAllProbe.getRef(),
            source,
            sink)
        .run(system);

    final ProducerRecord<String, String> completeMsg =
        catchAllProbe.expectMsgClass(ProducerRecord.class);
    assertThat(completeMsg.topic(), is("none"));
    assertThat(completeMsg.value(), is("completed"));
  }

  @Test
  @DisplayName(
      "A websocket message shall be converted into a kafka producer record while also recording metrics")
  void verifyProsessingPipelineWorksAsExpected() {
    final TestKit callcontextProvider = new TestKit(system);
    final TestKit metricsService = new TestKit(system);
    final TestKit kafkaProducer = new TestKit(system);
    final TestKit applicationReplacedHandler = new TestKit(system);

    final Strict stasisStartEvent = new Strict(StasisEvents.stasisStartEvent);

    final String resourceId = "1532965104.0";

    final Source<Message, NotUsed> source = Source.single(stasisStartEvent);

    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            kafkaProducer.getRef(), new ProducerRecord<String, String>("none", "completed"));

    WebsocketMessageToProducerRecordTranslator.eventProcessing(
            system,
            () ->
                applicationReplacedHandler
                    .getRef()
                    .tell("Application replaced", ActorRef.noSender()),
            callcontextProvider.getRef(),
            metricsService.getRef(),
            source,
            sink)
        .run(system);

    final ProvideCallContext provideCallContextForMetrics =
        callcontextProvider.expectMsgClass(ProvideCallContext.class);
    assertThat(provideCallContextForMetrics.resourceId(), is(resourceId));
    assertThat(provideCallContextForMetrics.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    callcontextProvider.reply(CALL_CONTEXT_PROVIDED);

    kafkaProducer.expectMsgClass(ProducerRecord.class);

    final IncreaseCounter eventTypeCounter = metricsService.expectMsgClass(IncreaseCounter.class);
    assertThat(eventTypeCounter.getName(), CoreMatchers.is(AriMessageType.STASIS_START.name()));

    final IncreaseCounter callsStartedCounter =
        metricsService.expectMsgClass(IncreaseCounter.class);
    assertThat(callsStartedCounter.getName(), is("CallsStarted"));

    final ProvideCallContext provideCallContextForRouting =
        callcontextProvider.expectMsgClass(ProvideCallContext.class);
    assertThat(provideCallContextForRouting.resourceId(), is(resourceId));
    assertThat(provideCallContextForRouting.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    callcontextProvider.reply(CALL_CONTEXT_PROVIDED);

    final StartCallSetupTimer startCallSetupTimer =
        metricsService.expectMsgClass(StartCallSetupTimer.class);
    assertThat(startCallSetupTimer.getCallContext(), is(CALL_CONTEXT_PROVIDED.callContext()));

    final ProducerRecord completedRecord = kafkaProducer.expectMsgClass(ProducerRecord.class);
    assertThat(completedRecord.topic(), is("none"));
    assertThat(completedRecord.value(), is("completed"));
  }
}

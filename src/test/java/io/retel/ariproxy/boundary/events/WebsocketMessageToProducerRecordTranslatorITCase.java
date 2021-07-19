package io.retel.ariproxy.boundary.events;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.http.javadsl.model.ws.Message;
import akka.http.scaladsl.model.ws.TextMessage.Strict;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.metrics.IncreaseCounter;
import io.retel.ariproxy.metrics.StartCallSetupTimer;
import io.vavr.control.Option;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebsocketMessageToProducerRecordTranslatorITCase {

  private static final CallContextProvided CALL_CONTEXT_PROVIDED =
      new CallContextProvided("CALL_CONTEXT_PROVIDED");
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

  @Test
  void verifyProcessingPipelineWorksAsExpectedForBogusMessages() {

    final TestKit catchAllProbe = new TestKit(system);

    final Source<Message, NotUsed> source = Source.single(new Strict("invalid message from ws"));
    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            catchAllProbe.getRef(), new ProducerRecord<String, String>("none", "completed"));

    WebsocketMessageToProducerRecordTranslator.eventProcessing()
        .on(system)
        .withHandler(
            () -> catchAllProbe.getRef().tell("Application replaced", catchAllProbe.getRef()))
        .withCallContextProvider(Adapter.toTyped(catchAllProbe.getRef()))
        .withMetricsService(Adapter.toTyped(catchAllProbe.getRef()))
        .from(source)
        .to(sink)
        .run();

    final ProducerRecord<String, String> completeMsg =
        catchAllProbe.expectMsgClass(ProducerRecord.class);
    assertThat(completeMsg.topic(), is("none"));
    assertThat(completeMsg.value(), is("completed"));
  }

  @Test
  @DisplayName(
      "A StasisStart without call context shall be converted into a kafka producer record while also recording metrics")
  void verifyProcessingPipelineWorksAsExpectedForStasisStartWithoutCallContext() throws Exception {
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

    WebsocketMessageToProducerRecordTranslator.eventProcessing()
        .on(system)
        .withHandler(
            () ->
                applicationReplacedHandler
                    .getRef()
                    .tell("Application replaced", ActorRef.noSender()))
        .withCallContextProvider(Adapter.toTyped(callcontextProvider.getRef()))
        .withMetricsService(Adapter.toTyped(metricsService.getRef()))
        .from(source)
        .to(sink)
        .run();

    final ProvideCallContext provideCallContextForMetrics =
        callcontextProvider.expectMsgClass(ProvideCallContext.class);
    assertThat(provideCallContextForMetrics.resourceId(), is(resourceId));
    assertThat(provideCallContextForMetrics.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    assertThat(provideCallContextForMetrics.maybeCallContextFromChannelVars(), is(Option.none()));
    callcontextProvider.reply(CALL_CONTEXT_PROVIDED);

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> record =
        kafkaProducer.expectMsgClass(ProducerRecord.class);
    assertThat(record.topic(), is("eventsAndResponsesTopic"));
    assertThat(record.key(), is(CALL_CONTEXT_PROVIDED.callContext()));
    assertThat(
        OBJECT_MAPPER.readTree(record.value()),
        equalTo(
            OBJECT_MAPPER.readTree(
                loadJsonAsString("messages/events/stasisStartEventWithoutCallContext.json"))));

    final IncreaseCounter eventTypeCounter = metricsService.expectMsgClass(IncreaseCounter.class);
    assertThat(eventTypeCounter.getName(), CoreMatchers.is(AriMessageType.STASIS_START.name()));

    final IncreaseCounter callsStartedCounter =
        metricsService.expectMsgClass(IncreaseCounter.class);
    assertThat(callsStartedCounter.getName(), is("CallsStarted"));

    final ProvideCallContext provideCallContextForRouting =
        callcontextProvider.expectMsgClass(ProvideCallContext.class);
    assertThat(provideCallContextForRouting.resourceId(), is(resourceId));
    assertThat(provideCallContextForRouting.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    assertThat(provideCallContextForRouting.maybeCallContextFromChannelVars(), is(Option.none()));
    callcontextProvider.reply(CALL_CONTEXT_PROVIDED);

    final StartCallSetupTimer startCallSetupTimer =
        metricsService.expectMsgClass(StartCallSetupTimer.class);
    assertThat(startCallSetupTimer.getCallContext(), is(CALL_CONTEXT_PROVIDED.callContext()));

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> completedRecord =
        kafkaProducer.expectMsgClass(ProducerRecord.class);
    assertThat(completedRecord.topic(), is("none"));
    assertThat(completedRecord.value(), is("completed"));
  }

  @Test
  @DisplayName(
      "A StasisStart without call context shall be converted into a kafka producer record while also recording metrics")
  void verifyProcessingPipelineWorksAsExpectedForStasisStartWithCallContext() throws Exception {
    final TestKit callcontextProvider = new TestKit(system);
    final TestKit metricsService = new TestKit(system);
    final TestKit kafkaProducer = new TestKit(system);
    final TestKit applicationReplacedHandler = new TestKit(system);

    final Strict stasisStartEvent = new Strict(StasisEvents.stasisStartEventWithCallContext);

    final String resourceId = "1532965104.0";

    final Source<Message, NotUsed> source = Source.single(stasisStartEvent);

    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            kafkaProducer.getRef(), new ProducerRecord<String, String>("none", "completed"));

    WebsocketMessageToProducerRecordTranslator.eventProcessing()
        .on(system)
        .withHandler(
            () ->
                applicationReplacedHandler
                    .getRef()
                    .tell("Application replaced", ActorRef.noSender()))
        .withCallContextProvider(Adapter.toTyped(callcontextProvider.getRef()))
        .withMetricsService(Adapter.toTyped(metricsService.getRef()))
        .from(source)
        .to(sink)
        .run();

    final ProvideCallContext provideCallContextForMetrics =
        callcontextProvider.expectMsgClass(ProvideCallContext.class);
    assertThat(provideCallContextForMetrics.resourceId(), is(resourceId));
    assertThat(provideCallContextForMetrics.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    assertThat(
        provideCallContextForMetrics.maybeCallContextFromChannelVars(),
        is(Option.some("aCallContext")));
    callcontextProvider.reply(CALL_CONTEXT_PROVIDED);

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> record =
        kafkaProducer.expectMsgClass(ProducerRecord.class);
    assertThat(record.topic(), is("eventsAndResponsesTopic"));
    assertThat(record.key(), is(CALL_CONTEXT_PROVIDED.callContext()));
    assertThat(
        OBJECT_MAPPER.readTree(record.value()),
        equalTo(
            OBJECT_MAPPER.readTree(
                loadJsonAsString("messages/events/stasisStartEventWithCallContext.json"))));

    final IncreaseCounter eventTypeCounter = metricsService.expectMsgClass(IncreaseCounter.class);
    assertThat(eventTypeCounter.getName(), CoreMatchers.is(AriMessageType.STASIS_START.name()));

    final IncreaseCounter callsStartedCounter =
        metricsService.expectMsgClass(IncreaseCounter.class);
    assertThat(callsStartedCounter.getName(), is("CallsStarted"));

    final ProvideCallContext provideCallContextForRouting =
        callcontextProvider.expectMsgClass(ProvideCallContext.class);
    assertThat(provideCallContextForRouting.resourceId(), is(resourceId));
    assertThat(provideCallContextForRouting.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    assertThat(
        provideCallContextForRouting.maybeCallContextFromChannelVars(),
        is(Option.some("aCallContext")));
    callcontextProvider.reply(CALL_CONTEXT_PROVIDED);

    final StartCallSetupTimer startCallSetupTimer =
        metricsService.expectMsgClass(StartCallSetupTimer.class);
    assertThat(startCallSetupTimer.getCallContext(), is(CALL_CONTEXT_PROVIDED.callContext()));

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> completedRecord =
        kafkaProducer.expectMsgClass(ProducerRecord.class);
    assertThat(completedRecord.topic(), is("none"));
    assertThat(completedRecord.value(), is("completed"));
  }

  private static String loadJsonAsString(final String fileName) throws IOException {
    final ClassLoader classLoader =
        WebsocketMessageToProducerRecordTranslatorITCase.class.getClassLoader();
    final File file = new File(classLoader.getResource(fileName).getFile());
    return new String(Files.readAllBytes(file.toPath()));
  }
}

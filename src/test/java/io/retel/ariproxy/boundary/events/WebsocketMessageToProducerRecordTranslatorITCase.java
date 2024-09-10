package io.retel.ariproxy.boundary.events;

import static io.retel.ariproxy.TestUtils.withCallContextKeyPrefix;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import akka.NotUsed;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.model.ws.Message;
import akka.http.scaladsl.model.ws.TextMessage;
import akka.http.scaladsl.model.ws.TextMessage.Strict;
import akka.pattern.StatusReply;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.MemoryKeyValueStore;
import io.retel.ariproxy.boundary.callcontext.TestableCallContextProvider;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.vavr.control.Option;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebsocketMessageToProducerRecordTranslatorITCase {

  private static final ActorTestKit testKit =
      ActorTestKit.create("testKit", ConfigFactory.defaultApplication());

  private static final CallContextProvided CALL_CONTEXT_PROVIDED =
      new CallContextProvided("CALL_CONTEXT_PROVIDED");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void verifyProcessingPipelineWorksAsExpectedForBogusMessages() {
    final Source<Message, NotUsed> source = Source.single(new Strict("invalid message from ws"));
    final TestProbe<Object> kafkaProducerProbe = testKit.createTestProbe();
    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            Adapter.toClassic(kafkaProducerProbe.ref()),
            new ProducerRecord<String, String>("none", "completed"));
    final TestProbe<String> shutdownRequestedProbe = testKit.createTestProbe();

    WebsocketMessageToProducerRecordTranslator.eventProcessing(
            testKit.system(),
            testKit.<CallContextProviderMessage>createTestProbe().ref(),
            source,
            sink,
            () -> shutdownRequestedProbe.getRef().tell("Application replaced"))
        .run(testKit.system());

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> completeMsg =
        (ProducerRecord<String, String>)
            (ProducerRecord<?, ?>) kafkaProducerProbe.expectMessageClass(ProducerRecord.class);
    assertThat(completeMsg.topic(), is("none"));
    assertThat(completeMsg.value(), is("completed"));

    shutdownRequestedProbe.expectNoMessage();
    kafkaProducerProbe.expectNoMessage();
  }

  @Test
  @DisplayName(
      "A StasisStart without call context shall be converted into a kafka producer record while"
          + " also recording metrics")
  void verifyProcessingPipelineWorksAsExpectedForStasisStartWithoutCallContext() throws Exception {
    final String resourceId = "1532965104.0";
    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(
            testKit,
            new MemoryKeyValueStore(
                withCallContextKeyPrefix(resourceId), CALL_CONTEXT_PROVIDED.callContext()));
    final TestProbe<Object> kafkaProducerProbe = testKit.createTestProbe();
    final TestProbe<String> shutdownRequestedProbe = testKit.createTestProbe();

    final Strict stasisStartEvent = new Strict(StasisEvents.stasisStartEvent);
    final Source<Message, NotUsed> source = Source.single(stasisStartEvent);

    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            Adapter.toClassic(kafkaProducerProbe.getRef()),
            new ProducerRecord<String, String>("none", "completed"));

    WebsocketMessageToProducerRecordTranslator.eventProcessing(
            testKit.system(),
            callContextProvider.ref(),
            source,
            sink,
            () -> shutdownRequestedProbe.getRef().tell("Application replaced"))
        .run(testKit.system());

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> record =
        kafkaProducerProbe.expectMessageClass(ProducerRecord.class);
    assertThat(record.topic(), is("eventsAndResponsesTopic"));
    assertThat(record.key(), is(CALL_CONTEXT_PROVIDED.callContext()));
    assertThat(
        OBJECT_MAPPER.readTree(record.value()),
        equalTo(
            OBJECT_MAPPER.readTree(
                loadJsonAsString("messages/events/stasisStartEventWithoutCallContext.json"))));

    final ProvideCallContext provideCallContextForRouting =
        callContextProvider.probe().expectMessageClass(ProvideCallContext.class);
    assertThat(provideCallContextForRouting.resourceId(), is(resourceId));
    assertThat(provideCallContextForRouting.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    assertThat(provideCallContextForRouting.maybeCallContextFromChannelVars(), is(Option.none()));

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> completedRecord =
        kafkaProducerProbe.expectMessageClass(ProducerRecord.class);
    assertThat(completedRecord.topic(), is("none"));
    assertThat(completedRecord.value(), is("completed"));

    callContextProvider.probe().expectNoMessage();
    kafkaProducerProbe.expectNoMessage();
    shutdownRequestedProbe.expectNoMessage();
  }

  @Test
  @DisplayName(
      "A StasisStart without call context shall be converted into a kafka producer record while"
          + " also recording metrics")
  void verifyProcessingPipelineWorksAsExpectedForStasisStartWithCallContext() throws Exception {
    final String resourceId = "1532965104.0";
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
                          .tell(
                              StatusReply.success(
                                  new CallContextProvided(CALL_CONTEXT_PROVIDED.callContext())));

                      return Behaviors.same();
                    })
                .build());
    final TestProbe<Object> kafkaProducerProbe = testKit.createTestProbe();
    final TestProbe<String> shutdownRequestedProbe = testKit.createTestProbe();

    final Strict stasisStartEvent = new Strict(StasisEvents.stasisStartEventWithCallContext);
    final Source<Message, NotUsed> source = Source.single(stasisStartEvent);

    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            Adapter.toClassic(kafkaProducerProbe.getRef()),
            new ProducerRecord<String, String>("none", "completed"));

    WebsocketMessageToProducerRecordTranslator.eventProcessing(
            testKit.system(),
            callContextProvider,
            source,
            sink,
            () -> shutdownRequestedProbe.getRef().tell("Application replaced"))
        .run(testKit.system());

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> record =
        kafkaProducerProbe.expectMessageClass(ProducerRecord.class);
    assertThat(record.topic(), is("eventsAndResponsesTopic"));
    assertThat(record.key(), is(CALL_CONTEXT_PROVIDED.callContext()));
    assertThat(
        OBJECT_MAPPER.readTree(record.value()),
        equalTo(
            OBJECT_MAPPER.readTree(
                loadJsonAsString("messages/events/stasisStartEventWithCallContext.json"))));

    final ProvideCallContext provideCallContextForRouting =
        callContextProviderProbe.expectMessageClass(ProvideCallContext.class);
    assertThat(provideCallContextForRouting.resourceId(), is(resourceId));
    assertThat(provideCallContextForRouting.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    assertThat(
        provideCallContextForRouting.maybeCallContextFromChannelVars(),
        is(Option.some("aCallContext")));

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> completedRecord =
        kafkaProducerProbe.expectMessageClass(ProducerRecord.class);
    assertThat(completedRecord.topic(), is("none"));
    assertThat(completedRecord.value(), is("completed"));

    callContextProviderProbe.expectNoMessage();
    kafkaProducerProbe.expectNoMessage();
    shutdownRequestedProbe.expectNoMessage();
  }

  @Test
  @DisplayName(
      "A StasisStart without call context shall be converted into a kafka producer record while"
          + " also recording metrics for StreamedMessage")
  void verifyProcessingPipelineWorksAsExpectedForStasisStartWithCallContextForStreamedMessage()
      throws Exception {
    final String resourceId = "1532965104.0";
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
                          .tell(
                              StatusReply.success(
                                  new CallContextProvided(CALL_CONTEXT_PROVIDED.callContext())));

                      return Behaviors.same();
                    })
                .build());
    final TestProbe<Object> kafkaProducerProbe = testKit.createTestProbe();
    final TestProbe<String> shutdownRequestedProbe = testKit.createTestProbe();

    final TextMessage.Streamed stasisStartEvent =
        new TextMessage.Streamed(
            akka.stream.scaladsl.Source.fromJavaStream(
                () -> Stream.of(StasisEvents.stasisStartEventWithCallContext)));
    final Source<Message, NotUsed> source = Source.single(stasisStartEvent);

    final Sink<ProducerRecord<String, String>, NotUsed> sink =
        Sink.actorRef(
            Adapter.toClassic(kafkaProducerProbe.getRef()),
            new ProducerRecord<String, String>("none", "completed"));

    WebsocketMessageToProducerRecordTranslator.eventProcessing(
            testKit.system(),
            callContextProvider,
            source,
            sink,
            () -> shutdownRequestedProbe.getRef().tell("Application replaced"))
        .run(testKit.system());

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> record =
        kafkaProducerProbe.expectMessageClass(ProducerRecord.class);
    assertThat(record.topic(), is("eventsAndResponsesTopic"));
    assertThat(record.key(), is(CALL_CONTEXT_PROVIDED.callContext()));
    assertThat(
        OBJECT_MAPPER.readTree(record.value()),
        equalTo(
            OBJECT_MAPPER.readTree(
                loadJsonAsString("messages/events/stasisStartEventWithCallContext.json"))));

    final ProvideCallContext provideCallContextForRouting =
        callContextProviderProbe.expectMessageClass(ProvideCallContext.class);
    assertThat(provideCallContextForRouting.resourceId(), is(resourceId));
    assertThat(provideCallContextForRouting.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
    assertThat(
        provideCallContextForRouting.maybeCallContextFromChannelVars(),
        is(Option.some("aCallContext")));

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> completedRecord =
        kafkaProducerProbe.expectMessageClass(ProducerRecord.class);
    assertThat(completedRecord.topic(), is("none"));
    assertThat(completedRecord.value(), is("completed"));

    callContextProviderProbe.expectNoMessage();
    kafkaProducerProbe.expectNoMessage();
    shutdownRequestedProbe.expectNoMessage();
  }

  private static String loadJsonAsString(final String fileName) throws IOException {
    final ClassLoader classLoader =
        WebsocketMessageToProducerRecordTranslatorITCase.class.getClassLoader();
    final File file = new File(classLoader.getResource(fileName).getFile());
    return new String(Files.readAllBytes(file.toPath()));
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }
}

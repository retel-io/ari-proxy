package io.retel.ariproxy.boundary.commandsandresponses;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.Done;
import akka.NotUsed;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.kafka.ConsumerMessage;
import akka.kafka.ConsumerMessage.CommittableMessage;
import akka.kafka.ProducerMessage;
import akka.kafka.javadsl.Consumer;
import akka.kafka.testkit.ConsumerResultFactory;
import akka.kafka.testkit.ProducerResultFactory;
import akka.stream.StreamTcpException;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.TestableCallContextProvider;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class AriCommandResponseKafkaProcessorTest {

  private static final ActorTestKit testKit =
      ActorTestKit.create("testKit", ConfigFactory.defaultApplication());
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test()
  void properlyHandleInvalidCommandMessage() {
    final TestProbe<ProducerRecord<String, String>> kafkaProducer = testKit.createTestProbe();
    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);

    final int offset = 123;
    final int partition = 456;

    final ConsumerRecord<String, String> consumerRecord =
        new ConsumerRecord<>("topic", partition, offset, "key", "NOT JSON");
    final Source<CommittableMessage<String, String>, Supplier<Consumer.Control>> source =
        Source.single(
                new CommittableMessage<String, String>(
                    consumerRecord,
                    ConsumerResultFactory.committableOffset(
                        "groupId", "topic", partition, offset, "metadata")))
            .mapMaterializedValue(ignored -> Consumer::createNoopControl);
    final TestConsumerCommitterSink testSink = TestConsumerCommitterSink.create(testKit);

    final Flow<
            ProducerMessage.Envelope<String, String, ConsumerMessage.CommittableOffset>,
            ProducerMessage.Results<String, String, ConsumerMessage.CommittableOffset>,
            NotUsed>
        mockedFlow = producerFlow(kafkaProducer.getRef());

    AriCommandResponseKafkaProcessor.commandResponseProcessing(
            testKit.system(),
            requestAndContext -> Http.get(testKit.system()).singleRequest(requestAndContext._1),
            callContextProvider.ref(),
            source,
            mockedFlow,
            testSink.sink())
        .run(testKit.system());

    kafkaProducer.expectNoMessage();

    final ConsumerMessage.CommittableOffset committableOffset =
        testSink.probe().expectMessageClass(ConsumerMessage.CommittableOffset.class);
    assertEquals(offset, committableOffset.partitionOffset().offset());
    assertEquals(partition, committableOffset.partitionOffset().key().partition());
  }

  @ParameterizedTest
  @ArgumentsSource(ResponseArgumentsProvider.class)
  void testCommandResponseProcessing(
      final String commandJsonFilename,
      final CompletionStage<HttpResponse> asteriskResponse,
      final String expectedResponseJsonFilename,
      final String resourceIdExpectedToRegisterInCallContext)
      throws Exception {
    final TestProbe<ProducerRecord<String, String>> kafkaProducer = testKit.createTestProbe();
    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);

    final int offset = 2132;
    final int partition = 29375;

    final String inputString = loadJsonAsString(commandJsonFilename);
    final ConsumerRecord<String, String> consumerRecord =
        new ConsumerRecord<>("topic", partition, offset, "none", inputString);

    final CommittableMessage<String, String> committableMessage =
        ConsumerResultFactory.committableMessage(
            consumerRecord,
            ConsumerResultFactory.committableOffset(
                "theGroupId", consumerRecord.topic(), partition, offset, "metadata"));
    final Source<CommittableMessage<String, String>, Supplier<Consumer.Control>> source =
        Source.single(committableMessage)
            .mapMaterializedValue(ignored -> Consumer::createNoopControl);

    final TestConsumerCommitterSink testSink = TestConsumerCommitterSink.create(testKit);

    AriCommandResponseKafkaProcessor.commandResponseProcessing(
            testKit.system(),
            requestAndContext -> {
              validateRequest(requestAndContext._1(), inputString);
              return asteriskResponse;
            },
            callContextProvider.ref(),
            source,
            producerFlow(kafkaProducer.getRef()),
            testSink.sink())
        .run(testKit.system());

    if (resourceIdExpectedToRegisterInCallContext != null) {
      final RegisterCallContext registerCallContext =
          callContextProvider.probe().expectMessageClass(RegisterCallContext.class);
      assertThat(registerCallContext.callContext(), is("CALL_CONTEXT"));
      assertThat(registerCallContext.resourceId(), is(resourceIdExpectedToRegisterInCallContext));
    }

    @SuppressWarnings("unchecked")
    final ProducerRecord<String, String> responseRecord =
        kafkaProducer.expectMessageClass(ProducerRecord.class);
    assertThat(responseRecord.topic(), is("eventsAndResponsesTopic"));
    assertThat(responseRecord.key(), is("CALL_CONTEXT"));
    assertEquals(
        OBJECT_MAPPER.readTree(loadJsonAsString(expectedResponseJsonFilename)),
        OBJECT_MAPPER.readTree(responseRecord.value()));

    kafkaProducer.expectNoMessage();

    final ConsumerMessage.CommittableOffset committableOffset =
        testSink.probe().expectMessageClass(ConsumerMessage.CommittableOffset.class);
    assertEquals(offset, committableOffset.partitionOffset().offset());
    assertEquals(partition, committableOffset.partitionOffset().key().partition());
  }

  @Test
  void testCommandResponseProcessingProducerFails() {
    final TestProbe<ProducerRecord<String, String>> kafkaProducer = testKit.createTestProbe();
    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);

    final int offset = 2132;
    final int partition = 29375;

    final String inputString =
        loadJsonAsString("messages/commands/bridgeCreateCommandWithBody.json");
    final ConsumerRecord<String, String> consumerRecord =
        new ConsumerRecord<>("topic", partition, offset, "none", inputString);

    final CommittableMessage<String, String> committableMessage =
        ConsumerResultFactory.committableMessage(
            consumerRecord,
            ConsumerResultFactory.committableOffset(
                "theGroupId", consumerRecord.topic(), partition, offset, "metadata"));
    final Source<CommittableMessage<String, String>, Supplier<Consumer.Control>> source =
        Source.single(committableMessage)
            .mapMaterializedValue(ignored -> Consumer::createNoopControl);

    final TestConsumerCommitterSink testSink = TestConsumerCommitterSink.create(testKit);

    AriCommandResponseKafkaProcessor.commandResponseProcessing(
            testKit.system(),
            requestAndContext ->
                CompletableFuture.completedFuture(
                    HttpResponse.create()
                        .withStatus(StatusCodes.OK)
                        .withEntity(
                            loadJsonAsString("messages/ari/responses/bridgeCreateResponse.json"))),
            callContextProvider.ref(),
            source,
            brokenProducerFlow(),
            testSink.sink())
        .run(testKit.system());

    kafkaProducer.expectNoMessage();
    testSink.probe().expectNoMessage();
  }

  private void validateRequest(final HttpRequest actualHttpRequest, final String inputString) {
    try {
      final JsonNode actualBody =
          OBJECT_MAPPER.readTree(
              actualHttpRequest
                  .entity()
                  .toStrict(1000L, testKit.system())
                  .toCompletableFuture()
                  .get()
                  .getData()
                  .utf8String());
      final JsonNode expectedBody =
          OBJECT_MAPPER.readTree(inputString).get("ariCommand").get("body");
      if (expectedBody == null) {
        assertTrue(actualBody instanceof MissingNode);
      } else {
        assertEquals(expectedBody, actualBody);
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static class ResponseArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "messages/commands/bridgeCreateCommandWithBody.json",
              CompletableFuture.completedFuture(
                  HttpResponse.create()
                      .withStatus(StatusCodes.OK)
                      .withEntity(
                          loadJsonAsString("messages/ari/responses/bridgeCreateResponse.json"))),
              "messages/responses/bridgeCreateResponseWithBody.json",
              "BRIDGE_ID"),
          Arguments.of(
              "messages/commands/channelPlaybackCommand.json",
              CompletableFuture.completedFuture(
                  HttpResponse.create()
                      .withStatus(StatusCodes.OK)
                      .withEntity("{ \"key\":\"value\" }")),
              "messages/responses/channelPlaybackResponse.json",
              "c4958563-1ba4-4f2f-a60f-626a624bf0e6"),
          Arguments.of(
              "messages/commands/channelAnswerCommand.json",
              CompletableFuture.completedFuture(
                  HttpResponse.create().withStatus(StatusCodes.NO_CONTENT)),
              "messages/responses/channelAnswerResponse.json",
              null),
          Arguments.of(
              "messages/commands/channelAnswerCommandWithoutCommandId.json",
              CompletableFuture.completedFuture(
                  HttpResponse.create().withStatus(StatusCodes.NO_CONTENT)),
              "messages/responses/channelAnswerResponseWithoutCommandId.json",
              null),
          Arguments.of(
              "messages/commands/bridgeCreateCommandWithBody.json",
              CompletableFuture.supplyAsync(
                  () -> {
                    throw new IllegalStateException("http request failed");
                  }),
              "messages/responses/bridgeCreateRequestFailedResponse.json",
              "BRIDGE_ID"),
          Arguments.of(
              "messages/commands/bridgeCreateCommandWithBody.json",
              CompletableFuture.supplyAsync(
                  () -> {
                    throw new StreamTcpException(
                        "Tcp command [Connect(api.example.com:443,None,List(),Some(10 milliseconds),true)] failed because of akka.io.TcpOutgoingConnection$$anon$2: Connect timeout of Some(10 milliseconds) expired");
                  }),
              "messages/responses/bridgeCreateRequestFailedResponse.json",
              "BRIDGE_ID"));
    }
  }

  private static String loadJsonAsString(final String fileName) {
    final ClassLoader classLoader = AriCommandResponseKafkaProcessorTest.class.getClassLoader();
    final File file = new File(classLoader.getResource(fileName).getFile());
    try {
      return new String(Files.readAllBytes(file.toPath()));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load file " + fileName, e);
    }
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }

  private Flow<
          ProducerMessage.Envelope<String, String, ConsumerMessage.CommittableOffset>,
          ProducerMessage.Results<String, String, ConsumerMessage.CommittableOffset>,
          NotUsed>
      producerFlow(final ActorRef<ProducerRecord<String, String>> kafkaProducer) {
    return Flow
        .<ProducerMessage.Envelope<String, String, ConsumerMessage.CommittableOffset>>create()
        .map(
            msg -> {
              if (msg
                  instanceof
                  ProducerMessage.Message<String, String, ConsumerMessage.CommittableOffset>
                  message) {
                kafkaProducer.tell(message.record());
                return ProducerResultFactory.result(message);
              } else throw new RuntimeException("unexpected element: " + msg);
            });
  }

  private Flow<
          ProducerMessage.Envelope<String, String, ConsumerMessage.CommittableOffset>,
          ProducerMessage.Results<String, String, ConsumerMessage.CommittableOffset>,
          NotUsed>
      brokenProducerFlow() {
    return Flow.fromFunction(
        msg -> {
          throw new RuntimeException("Test Exception. Simulating failure during producer stage.");
        });
  }

  public record TestConsumerCommitterSink(
      TestProbe<ConsumerMessage.CommittableOffset> probe,
      Sink<ConsumerMessage.CommittableOffset, CompletionStage<Done>> sink) {

    private static final CompletableFuture<Done> completableFuture = new CompletableFuture<>();

    public static TestConsumerCommitterSink create(final ActorTestKit testKit) {
      interface CommitterMessage {}
      record Offset(ConsumerMessage.CommittableOffset payload) implements CommitterMessage {}
      record StreamCompleted() implements CommitterMessage {}

      final TestProbe<ConsumerMessage.CommittableOffset> probe =
          testKit.createTestProbe(ConsumerMessage.CommittableOffset.class);

      final ActorRef<Object> adapter =
          testKit.spawn(
              Behaviors.receive(Object.class)
                  .onMessage(
                      Offset.class,
                      offset -> {
                        testKit
                            .system()
                            .log()
                            .warn("TestConsumerCommitterSink committing {}", offset.payload());
                        probe.ref().tell(offset.payload());
                        return Behaviors.same();
                      })
                  .onMessage(
                      StreamCompleted.class,
                      offset -> {
                        testKit.system().log().warn("TestConsumerCommitterSink stopped");
                        completableFuture.complete(Done.done());
                        return Behaviors.same();
                      })
                  .onAnyMessage(
                      param -> {
                        testKit
                            .system()
                            .log()
                            .warn("TestConsumerCommitterSink received message {}", param);
                        return Behaviors.same();
                      })
                  .build());

      final Sink<ConsumerMessage.CommittableOffset, CompletionStage<Done>> sink =
          Flow.<ConsumerMessage.CommittableOffset, CommitterMessage>fromFunction(Offset::new)
              .to(Sink.actorRef(Adapter.toClassic(adapter), new StreamCompleted()))
              .mapMaterializedValue(param -> completableFuture);

      return new TestConsumerCommitterSink(probe, sink);
    }
  }
}

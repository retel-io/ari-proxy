package io.retel.ariproxy.boundary.commandsandresponses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.Done;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.pattern.StatusReply;
import akka.stream.StreamTcpException;
import akka.stream.javadsl.Sink;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.AriCommandMessage;
import io.retel.ariproxy.boundary.callcontext.TestableCallContextProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class AriCommandResponseProcessorTest {

  private static ActorTestKit testKit;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @BeforeEach
  void setup() {
    testKit = ActorTestKit.create("testKit", ConfigFactory.defaultApplication());
  }

  @AfterEach
  void tearDown() {
    testKit.shutdownTestKit();
  }

  @Test
  public void handleBrokenMessage() {

    final Sink<ProducerRecord<String, String>, CompletionStage<Done>> ignore =
        Sink.foreach(param -> assertEquals(param, new Object()));

    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);

    final ActorRef<AriCommandMessage> ariCommandResponseProcessor =
        testKit.spawn(
            AriCommandResponseProcessor.create(
                requestAndContext ->
                    CompletableFuture.completedFuture(
                        HttpResponse.create().withStatus(StatusCodes.OK).withEntity("")),
                callContextProvider.ref(),
                ignore),
            "ari-command-response-processor");

    final TestProbe<StatusReply<Void>> replyProbe = testKit.createTestProbe("replyProbe");
    ariCommandResponseProcessor.tell(new AriCommandMessage("", replyProbe.getRef()));

    final StatusReply<Void> statusReply = replyProbe.expectMessageClass(StatusReply.class);
    assertTrue(statusReply.isError());
  }

  @Test
  public void handleTCPStreamException() throws IOException {

    final String inputString =
        loadJsonAsString("messages/commands/bridgeCreateCommandWithBody.json");
    final String outputString =
        loadJsonAsString("messages/responses/bridgeCreateRequestFailedResponse.json");

    final List<ProducerRecord<String, String>> producedRecords = new ArrayList<>();
    final Sink<ProducerRecord<String, String>, CompletionStage<Done>> ignore =
        Sink.foreach(producedRecords::add);

    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);

    final ActorRef<AriCommandMessage> ariCommandResponseProcessor =
        testKit.spawn(
            AriCommandResponseProcessor.create(
                (request) ->
                    CompletableFuture.supplyAsync(
                        () -> {
                          throw new StreamTcpException(
                              "Tcp command [Connect(api.example.com:443,None,List(),Some(10"
                                  + " milliseconds),true)] failed because of"
                                  + " akka.io.TcpOutgoingConnection$$anon$2: Connect timeout of"
                                  + " Some(10 milliseconds) expired");
                        }),
                callContextProvider.ref(),
                ignore),
            "ari-command-response-processor");

    final TestProbe<StatusReply<Void>> replyProbe = testKit.createTestProbe("replyProbe");
    ariCommandResponseProcessor.tell(new AriCommandMessage(inputString, replyProbe.getRef()));

    final StatusReply<Void> statusReply = replyProbe.expectMessageClass(StatusReply.class);
    assertTrue(statusReply.isSuccess());

    replyProbe.expectNoMessage();

    assertEquals(1, producedRecords.size(), "expected 1 produced record");
    assertEquals(
        OBJECT_MAPPER.readTree(outputString),
        OBJECT_MAPPER.readTree(producedRecords.get(0).value()));
  }

  @ParameterizedTest()
  @ArgumentsSource(ResponseArgumentsProvider.class)
  public void handleBridgeCreationFailed(
      final String inputFile,
      final String outputFile,
      final StatusCode statusCode,
      final String responseBody)
      throws IOException {

    final String inputString = loadJsonAsString(inputFile);
    final String outputString = loadJsonAsString(outputFile);

    final List<ProducerRecord<String, String>> producedRecords = new ArrayList<>();
    final Sink<ProducerRecord<String, String>, CompletionStage<Done>> ignore =
        Sink.foreach(producedRecords::add);

    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);

    final ActorRef<AriCommandMessage> ariCommandResponseProcessor =
        testKit.spawn(
            AriCommandResponseProcessor.create(
                requestAndContext ->
                    CompletableFuture.completedFuture(
                        HttpResponse.create().withStatus(statusCode).withEntity(responseBody)),
                callContextProvider.ref(),
                ignore),
            "ari-command-response-processor");

    final TestProbe<StatusReply<Void>> replyProbe = testKit.createTestProbe("replyProbe");
    ariCommandResponseProcessor.tell(new AriCommandMessage(inputString, replyProbe.getRef()));

    final StatusReply<Void> statusReply = replyProbe.expectMessageClass(StatusReply.class);
    assertTrue(statusReply.isSuccess(), "StatusReply is successful");

    replyProbe.expectNoMessage();

    assertEquals(1, producedRecords.size(), "expected 1 produced record");
    assertEquals(
        OBJECT_MAPPER.readTree(outputString),
        OBJECT_MAPPER.readTree(producedRecords.get(0).value()));
  }

  static class ResponseArgumentsProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "messages/commands/bridgeCreateCommandWithBody.json",
              "messages/responses/bridgeCreateResponseWithBody.json",
              StatusCodes.OK,
              loadJsonAsString("messages/ari/responses/bridgeCreateResponse.json")),
          Arguments.of(
              "messages/commands/channelPlaybackCommand.json",
              "messages/responses/channelPlaybackResponse.json",
              StatusCodes.OK,
              "{ \"key\":\"value\" }"),
          Arguments.of(
              "messages/commands/channelAnswerCommand.json",
              "messages/responses/channelAnswerResponse.json",
              StatusCodes.NO_CONTENT,
              ""),
          Arguments.of(
              "messages/commands/channelDeleteWithReasonCommand.json",
              "messages/responses/channelDeleteWithReasonResponse.json",
              StatusCodes.NO_CONTENT,
              ""),
          Arguments.of(
              "messages/commands/channelAnswerCommandWithoutCommandId.json",
              "messages/responses/channelAnswerResponseWithoutCommandId.json",
              StatusCodes.NO_CONTENT,
              ""));
    }
  }

  private static String loadJsonAsString(final String fileName) {
    final ClassLoader classLoader = AriCommandResponseProcessorTest.class.getClassLoader();
    final File file = new File(classLoader.getResource(fileName).getFile());
    try {
      return new String(Files.readAllBytes(file.toPath()));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load file " + fileName, e);
    }
  }
}

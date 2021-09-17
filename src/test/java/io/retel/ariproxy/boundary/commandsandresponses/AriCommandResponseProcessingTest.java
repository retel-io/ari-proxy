package io.retel.ariproxy.boundary.commandsandresponses;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.Done;
import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.TestableCallContextProvider;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProviderMessage;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.vavr.control.Try;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class AriCommandResponseProcessingTest {

  private static final ActorTestKit testKit =
      ActorTestKit.create("testKit", ConfigFactory.defaultApplication());
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final ObjectReader ariCommandReader = mapper.readerFor(AriCommand.class);

  private static final String CALL_CONTEXT = "theCallContext";

  @Test
  void registerCallContextDoesNothingWhenItShouldnt() {
    final TestProbe<CallContextProviderMessage> callContextProviderProbe =
        testKit.createTestProbe(CallContextProviderMessage.class);

    final Try<Done> result =
        AriCommandResponseProcessing.registerCallContext(
            callContextProviderProbe.getRef(),
            CALL_CONTEXT,
            new AriCommand(null, "/channels/CHANNEL_ID/answer", null),
            testKit.system());

    assertTrue(result.isSuccess());
    callContextProviderProbe.expectNoMessage(Duration.ofMillis(500));
  }

  @Test
  void registerCallContextRegistersANewCallContextIfTheAriCommandTypeNecessitatesIt() {
    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);

    final Try<Done> result =
        AriCommandResponseProcessing.registerCallContext(
            callContextProvider.ref(),
            CALL_CONTEXT,
            new AriCommand(null, "/channels/CHANNEL_ID/play/PLAYBACK_ID", null),
            testKit.system());

    assertTrue(result.isSuccess());
    final RegisterCallContext registerCallContext =
        callContextProvider.probe().expectMessageClass(RegisterCallContext.class);
    assertThat(registerCallContext.resourceId(), is("PLAYBACK_ID"));
    assertThat(registerCallContext.callContext(), is(CALL_CONTEXT));
  }

  @Test
  void registerCallContextThrowsARuntimeExceptionIfTheAriCommandIsMalformed() {
    final TestProbe<CallContextProviderMessage> callContextProviderProbe =
        testKit.createTestProbe(CallContextProviderMessage.class);

    final Try<Done> result =
        AriCommandResponseProcessing.registerCallContext(
            callContextProviderProbe.ref(),
            null,
            new AriCommand(null, "/channels", null),
            testKit.system());

    assertTrue(result.isFailure());
  }

  @Test
  void ensureFallBackToBodyExtractorWorksAsExpected() throws IOException {
    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);
    final String json =
        "{ \"method\":\"POST\", \"url\":\"/channels/CHANNEL_ID/record\", \"body\":{\"name\":\"RECORD_NAME\"}}";
    final AriCommand ariCommand = ariCommandReader.readValue(json);

    final Try<Done> result =
        AriCommandResponseProcessing.registerCallContext(
            callContextProvider.ref(), CALL_CONTEXT, ariCommand, testKit.system());

    assertTrue(result.isSuccess());
    final RegisterCallContext registerCallContext =
        callContextProvider.probe().expectMessageClass(RegisterCallContext.class);
    assertEquals("RECORD_NAME", registerCallContext.resourceId());
    assertEquals(CALL_CONTEXT, registerCallContext.callContext());
  }

  @Test
  void ensureFallBackToBodyExtractorWorksAsExpectedForChannelCreate() throws IOException {
    final TestableCallContextProvider callContextProvider =
        new TestableCallContextProvider(testKit);
    final String json =
        "{ \"method\":\"POST\", \"url\":\"/channels/create\", \"body\":{\"channelId\":\"channel-Id\"}}";
    final AriCommand ariCommand = ariCommandReader.readValue(json);

    final Try<Done> res =
        AriCommandResponseProcessing.registerCallContext(
            callContextProvider.ref(), CALL_CONTEXT, ariCommand, testKit.system());

    assertTrue(res.isSuccess());

    final RegisterCallContext registerCallContext =
        callContextProvider.probe().expectMessageClass(RegisterCallContext.class);
    assertThat(registerCallContext.resourceId(), is("channel-Id"));
    assertThat(registerCallContext.callContext(), is(CALL_CONTEXT));
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }
}

package io.retel.ariproxy.boundary.commandsandresponses;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.vavr.control.Try;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AriCommandResponseProcessingTest {

  private final String TEST_SYSTEM = this.getClass().getSimpleName();
  private ActorSystem system;
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final ObjectReader ariCommandReader = mapper.readerFor(AriCommand.class);

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
  void registerCallContextDoesNothingWhenItShouldnt() {
    final TestKit callContextProvider = new TestKit(system);
    final AriCommand ariCommand = new AriCommand(null, "/channels/CHANNEL_ID/answer", null);

    final Try<Void> res =
        AriCommandResponseProcessing.registerCallContext(
            callContextProvider.getRef(), "CALL_CONTEXT", ariCommand);

    assertTrue(res.isSuccess());
    callContextProvider.expectNoMessage(Duration.ofMillis(500));
  }

  @Test
  void registerCallContextRegistersANewCallContextIfTheAriCommandTypeNecessitatesIt() {
    final TestKit callContextProvider = new TestKit(system);
    final AriCommand ariCommand =
        new AriCommand(null, "/channels/CHANNEL_ID/play/PLAYBACK_ID", null);

    final Try<Void> res =
        AriCommandResponseProcessing.registerCallContext(
            callContextProvider.getRef(), "CALL_CONTEXT", ariCommand);

    assertTrue(res.isSuccess());

    final RegisterCallContext registerCallContext =
        callContextProvider.expectMsgClass(RegisterCallContext.class);
    assertThat(registerCallContext.resourceId(), is("PLAYBACK_ID"));
    assertThat(registerCallContext.callContext(), is("CALL_CONTEXT"));
  }

  @Test
  void registerCallContextThrowsARuntimeExceptionIfTheAriCommandIsMalformed() {
    final AriCommand ariCommand = new AriCommand(null, "/channels", null);
    final Try<Void> res = AriCommandResponseProcessing.registerCallContext(null, null, ariCommand);

    assertTrue(res.isFailure());
  }

  @Test
  void ensureFallBackToBodyExtractorWorksAsExpected() throws IOException {
    final TestKit callContextProvider = new TestKit(system);
    final String json =
        "{ \"method\":\"POST\", \"url\":\"/channels/CHANNEL_ID/record\", \"body\":{\"name\":\"RECORD_NAME\"}}";
    final AriCommand ariCommand = ariCommandReader.readValue(json);

    final Try<Void> res =
        AriCommandResponseProcessing.registerCallContext(
            callContextProvider.getRef(), "CALL_CONTEXT", ariCommand);

    assertTrue(res.isSuccess());

    final RegisterCallContext registerCallContext =
        callContextProvider.expectMsgClass(RegisterCallContext.class);
    assertEquals("RECORD_NAME", registerCallContext.resourceId());
    assertEquals("CALL_CONTEXT", registerCallContext.callContext());
  }

  @Test
  void ensureFallBackToBodyExtractorWorksAsExpectedForChannelCreate() throws IOException {
    final TestKit callContextProvider = new TestKit(system);
    final String json =
        "{ \"method\":\"POST\", \"url\":\"/channels/create\", \"body\":{\"channelId\":\"channel-Id\"}}";
    final AriCommand ariCommand = ariCommandReader.readValue(json);

    System.out.println(ariCommand);

    final Try<Void> res =
        AriCommandResponseProcessing.registerCallContext(
            callContextProvider.getRef(), "CALL_CONTEXT", ariCommand);

    assertTrue(res.isSuccess());

    final RegisterCallContext registerCallContext =
        callContextProvider.expectMsgClass(RegisterCallContext.class);
    assertThat(registerCallContext.resourceId(), is("channel-Id"));
    assertThat(registerCallContext.callContext(), is("CALL_CONTEXT"));
  }
}

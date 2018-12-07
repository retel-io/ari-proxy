package io.retel.ariproxy.boundary.commandsandresponses;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.vavr.control.Either;
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
		final AriCommand ariCommand = mock(AriCommand.class);
		doReturn("/channels/CHANNEL_ID/answer").when(ariCommand).getUrl();
		doReturn(mock(JsonNode.class)).when(ariCommand).getBody();

		final Either<RuntimeException, Runnable> res = AriCommandResponseProcessing
				.registerCallContext(callContextProvider.getRef(), "CALL_CONTEXT", ariCommand);

		res.get().run();

		callContextProvider.expectNoMessage(Duration.ofMillis(500));
	}

	@Test
	void registerCallContextRegisteresANewCallContextIfTheAriCommandTypeNecessitatesIt() {
		final TestKit callContextProvider = new TestKit(system);
		final AriCommand ariCommand = mock(AriCommand.class);
		doReturn("/channels/CHANNEL_ID/play/PLAYBACK_ID").when(ariCommand).getUrl();
		doReturn(mock(JsonNode.class)).when(ariCommand).getBody();

		final Either<RuntimeException, Runnable> res = AriCommandResponseProcessing
				.registerCallContext(callContextProvider.getRef(), "CALL_CONTEXT", ariCommand);

		res.get().run();

		final RegisterCallContext registerCallContext = callContextProvider.expectMsgClass(RegisterCallContext.class);
		assertThat(registerCallContext.resourceId(), is("PLAYBACK_ID"));
		assertThat(registerCallContext.callContext(), is("CALL_CONTEXT"));
	}

	@Test
	void registerCallContextThrowsARuntimeExceptionIfTheAriCommandIsMalformed() {
		final AriCommand ariCommand = mock(AriCommand.class);
		doReturn("/channels").when(ariCommand).getUrl();
		doReturn(null).when(ariCommand).getBody();

		final Either<RuntimeException, Runnable> res = AriCommandResponseProcessing.registerCallContext(null, null, ariCommand);

		assertThat(res.getLeft(), instanceOf(RuntimeException.class));
		assertThat(res.getLeft().getMessage(), containsString("Failed to extract resourceId from both"));
	}

	@Test
	void ensureFallBackToBodyExtractorWorksAsExpected() throws IOException {
		final TestKit callContextProvider = new TestKit(system);
		final String json = "{ \"method\":\"POST\", \"url\":\"/channels/CHANNEL_ID/record\", \"body\":{\"name\":\"RECORD_NAME\"}}";
		final AriCommand ariCommand = ariCommandReader.readValue(json);

		System.out.println(ariCommand);

		final Either<RuntimeException, Runnable> res = AriCommandResponseProcessing
				.registerCallContext(callContextProvider.getRef(), "CALL_CONTEXT", ariCommand);

		res.get().run();

		final RegisterCallContext registerCallContext = callContextProvider.expectMsgClass(RegisterCallContext.class);
		assertThat(registerCallContext.resourceId(), is("RECORD_NAME"));
		assertThat(registerCallContext.callContext(), is("CALL_CONTEXT"));

	}
}

package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static io.vavr.API.None;
import static io.vavr.API.Some;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.vavr.control.Try;
import java.io.IOException;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;

// see: https://wiki.asterisk.org/wiki/display/AST/Asterisk+15+REST+Data+Models#Asterisk15RESTDataModels-Message
class AriMessageTypeTest {

	private static final ObjectReader reader = new ObjectMapper().reader();

	private static final String BRIDGE_ID = "BRIDGE_ID";
	private static final String CHANNEL_ID = "CHANNEL_ID";
	private static final String PEER_ID = "SNOOP_ID";
	private static final String PLAYBACK_ID = "PLAYBACK_ID";
	private static final String RECORDING_NAME = "RECORDING_NAME";

	private static final String BODY_WITH_BRIDGE_ID = String.format("{ \"bridge\":  { \"id\": \"%s\" }  }", BRIDGE_ID);
	private static final String BODY_WITH_CHANNEL_ID = String.format("{ \"channel\": { \"id\": \"%s\" } }", CHANNEL_ID);
	private static final String BODY_WITH_PEER_ID = String.format("{ \"peer\": { \"id\": \"%s\" } }", PEER_ID);
	private static final String BODY_WITH_PLAYBACK_ID = String.format("{ \"playback\": { \"id\": \"%s\" } }", PLAYBACK_ID);
	private static final String BODY_WITH_RECORDING_NAME = String.format("{ \"recording\": { \"name\": \"%s\" } }", RECORDING_NAME);

	@ParameterizedTest
	@MethodSource("messageBodyProvider")
	void ensureExtractResourceIdFromBodyWorksForAnyType(String type, String body, String expectedResourceId) throws IOException {
		assertThat(AriMessageType.fromType(type).extractResourceIdFromBody(reader.readTree(body)), is(Some(Try.success(expectedResourceId))));
	}

	@ParameterizedTest
	@EnumSource(value = AriMessageType.class, mode = Mode.INCLUDE, names = { "APPLICATION_REPLACED" })
	void ensureMessageTypesWithoutAnExtractorResultInANone(AriMessageType type) {
		assertThat(type.extractResourceIdFromBody(mock(JsonNode.class)), is(None()));
	}

	@Test
	void ensureUnknownMessageResultsInRuntimeException() {
		MatcherAssert.assertThat(AriMessageType.UNKNOWN.extractResourceIdFromBody(mock(JsonNode.class)).get().getCause(), instanceOf(RuntimeException.class));
	}

	private static Stream<Arguments> messageBodyProvider() {
		return Stream.of(
				Arguments.of("BridgeCreated", BODY_WITH_BRIDGE_ID, BRIDGE_ID),
				Arguments.of("BridgeDestroyed", BODY_WITH_BRIDGE_ID, BRIDGE_ID),
				Arguments.of("BridgeMerged", BODY_WITH_BRIDGE_ID, BRIDGE_ID),
				Arguments.of("BridgeVideoSourceChanged", BODY_WITH_BRIDGE_ID, BRIDGE_ID),
				Arguments.of("ChannelEnteredBridge", BODY_WITH_BRIDGE_ID, BRIDGE_ID),
				Arguments.of("ChannelLeftBridge", BODY_WITH_BRIDGE_ID, BRIDGE_ID),

				Arguments.of("ChannelCallerId", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelConnectedLine", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelCreated", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelDestroyed", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelDtmfReceived", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelHangupRequest", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelHold", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelStateChange", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelTalkingFinished", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelTalkingStarted", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("ChannelUnhold", BODY_WITH_CHANNEL_ID, CHANNEL_ID),

				Arguments.of("Dial", BODY_WITH_PEER_ID, PEER_ID),

				Arguments.of("PlaybackContinuing", BODY_WITH_PLAYBACK_ID, PLAYBACK_ID),
				Arguments.of("PlaybackFinished", BODY_WITH_PLAYBACK_ID, PLAYBACK_ID),
				Arguments.of("PlaybackStarted", BODY_WITH_PLAYBACK_ID, PLAYBACK_ID),

				Arguments.of("RecordingFailed", BODY_WITH_RECORDING_NAME, RECORDING_NAME),
				Arguments.of("RecordingFinished", BODY_WITH_RECORDING_NAME, RECORDING_NAME),
				Arguments.of("RecordingStarted", BODY_WITH_RECORDING_NAME, RECORDING_NAME),

				Arguments.of("StasisEnd", BODY_WITH_CHANNEL_ID, CHANNEL_ID),
				Arguments.of("StasisStart", BODY_WITH_CHANNEL_ID, CHANNEL_ID)
		);
	}
}
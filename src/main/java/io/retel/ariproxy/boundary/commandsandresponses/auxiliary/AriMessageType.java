package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static io.vavr.API.None;
import static io.vavr.API.Set;
import static io.vavr.API.Some;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

// Supported event types
public enum AriMessageType {
	APPLICATION_REPLACED("ApplicationReplaced", body -> None()),
	BRIDGE_CREATED("BridgeCreated", resourceIdFromBody("/bridge/id")),
	BRIDGE_DESTROYED("BridgeDestroyed", resourceIdFromBody("/bridge/id")),
	BRIDGE_MERGED("BridgeMerged", resourceIdFromBody("/bridge/id")),
	BRIDGE_VIDEOSOURCE_CHANGED("BridgeVideoSourceChanged", resourceIdFromBody("/bridge/id")),
	CHANNEL_CALLERID("ChannelCallerId", resourceIdFromBody("/channel/id")),
	CHANNEL_CONNECTED_LINE("ChannelConnectedLine", resourceIdFromBody("/channel/id")),
	CHANNEL_CREATED("ChannelCreated", resourceIdFromBody("/channel/id")),
	CHANNEL_DESTROYED("ChannelDestroyed", resourceIdFromBody("/channel/id")),
	CHANNEL_DIALPLAN("ChannelDialplan", resourceIdFromBody("/channel/id")),
	CHANNEL_DTMF_RECEIVED("ChannelDtmfReceived", resourceIdFromBody("/channel/id")),
	CHANNEL_ENTERED_BRIDGE("ChannelEnteredBridge", resourceIdFromBody("/bridge/id")),
	CHANNEL_HANGUP_REQUEST("ChannelHangupRequest", resourceIdFromBody("/channel/id")),
	CHANNEL_HOLD("ChannelHold", resourceIdFromBody("/channel/id")),
	CHANNEL_LEFT_BRIDGE("ChannelLeftBridge", resourceIdFromBody("/bridge/id")),
	CHANNEL_STATE_CHANGE("ChannelStateChange", resourceIdFromBody("/channel/id")),
	CHANNEL_TALKING_FINISHED("ChannelTalkingFinished", resourceIdFromBody("/channel/id")),
	CHANNEL_TALKING_STARTED("ChannelTalkingStarted", resourceIdFromBody("/channel/id")),
	CHANNEL_UNHOLD("ChannelUnhold", resourceIdFromBody("/channel/id")),
	DIAL("Dial", resourceIdFromBody("/peer/id")),
	PLAYBACK_CONTINUING("PlaybackContinuing", resourceIdFromBody("/playback/id")),
	PLAYBACK_FINISHED("PlaybackFinished", resourceIdFromBody("/playback/id")),
	PLAYBACK_STARTED("PlaybackStarted", resourceIdFromBody("/playback/id")),
	RECORDING_FAILED("RecordingFailed", resourceIdFromBody("/recording/name")),
	RECORDING_FINISHED("RecordingFinished", resourceIdFromBody("/recording/name")),
	RECORDING_STARTED("RecordingStarted", resourceIdFromBody("/recording/name")),
	STASIS_END("StasisEnd", resourceIdFromBody("/channel/id")),
	STASIS_START("StasisStart", resourceIdFromBody("/channel/id")),
	RESPONSE("AriResponse", body -> None()),
	UNKNOWN("UnknownAriMessage", body -> Some(Try.failure(new RuntimeException(String.format("Failed to extract resourceId from body=%s", body)))));

	private final String typeName;
	private final Function<String, Option<Try<String>>> resourceIdExtractor;

	AriMessageType(final String typeName, final Function<String, Option<Try<String>>> resourceIdExtractor) {
		this.typeName = typeName;
		this.resourceIdExtractor = resourceIdExtractor;
	}

	public static AriMessageType fromType(final String candidateType) {
		return Set(AriMessageType.values())
				.find(ariMessageType -> ariMessageType.typeName.equals(candidateType))
				.getOrElse(UNKNOWN);
	}

	public Option<Try<String>> extractResourceIdFromBody(final String body) {
		return resourceIdExtractor.apply(body);
	}

	private static Function<String, Option<Try<String>>> resourceIdFromBody(final String resourceIdXPath) {
		return body -> Some(Try.of(() -> new ObjectMapper().readTree(body))
				.toOption()
				.flatMap(root -> Option.of(root.at(resourceIdXPath)))
				.map(JsonNode::asText)
				.flatMap(type -> StringUtils.isBlank(type) ? None() : Some(type))
				.toTry(() -> new Throwable(String.format("Failed to extract resourceId at path=%s from body=%s", resourceIdXPath, body))));
	}
}

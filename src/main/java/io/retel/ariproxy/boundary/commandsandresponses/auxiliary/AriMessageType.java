package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static io.vavr.API.None;
import static io.vavr.API.Set;
import static io.vavr.API.Some;

import com.fasterxml.jackson.databind.JsonNode;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

// Supported event types
public enum AriMessageType {
	APPLICATION_REPLACED("ApplicationReplaced", body -> None()),
	BRIDGE_CREATED("BridgeCreated", resourceIdFromBody(XPaths.BRIDGE_ID)),
	BRIDGE_DESTROYED("BridgeDestroyed", resourceIdFromBody(XPaths.BRIDGE_ID)),
	BRIDGE_MERGED("BridgeMerged", resourceIdFromBody(XPaths.BRIDGE_ID)),
	BRIDGE_VIDEOSOURCE_CHANGED("BridgeVideoSourceChanged", resourceIdFromBody(XPaths.BRIDGE_ID)),
	CHANNEL_CALLERID("ChannelCallerId", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_CONNECTED_LINE("ChannelConnectedLine", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_CREATED("ChannelCreated", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_DESTROYED("ChannelDestroyed", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_DIALPLAN("ChannelDialplan", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_DTMF_RECEIVED("ChannelDtmfReceived", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_ENTERED_BRIDGE("ChannelEnteredBridge", resourceIdFromBody(XPaths.BRIDGE_ID)),
	CHANNEL_HANGUP_REQUEST("ChannelHangupRequest", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_HOLD("ChannelHold", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_LEFT_BRIDGE("ChannelLeftBridge", resourceIdFromBody(XPaths.BRIDGE_ID)),
	CHANNEL_STATE_CHANGE("ChannelStateChange", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_TALKING_FINISHED("ChannelTalkingFinished", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_TALKING_STARTED("ChannelTalkingStarted", resourceIdFromBody(XPaths.CHANNEL_ID)),
	CHANNEL_UNHOLD("ChannelUnhold", resourceIdFromBody(XPaths.CHANNEL_ID)),
	DIAL("Dial", resourceIdFromBody(XPaths.PEER_ID)),
	PLAYBACK_CONTINUING("PlaybackContinuing", resourceIdFromBody(XPaths.PLAYBACK_ID)),
	PLAYBACK_FINISHED("PlaybackFinished", resourceIdFromBody(XPaths.PLAYBACK_ID)),
	PLAYBACK_STARTED("PlaybackStarted", resourceIdFromBody(XPaths.PLAYBACK_ID)),
	RECORDING_FAILED("RecordingFailed", resourceIdFromBody(XPaths.RECORDING_NAME)),
	RECORDING_FINISHED("RecordingFinished", resourceIdFromBody(XPaths.RECORDING_NAME)),
	RECORDING_STARTED("RecordingStarted", resourceIdFromBody(XPaths.RECORDING_NAME)),
	STASIS_END("StasisEnd", resourceIdFromBody(XPaths.CHANNEL_ID)),
	STASIS_START("StasisStart", resourceIdFromBody(XPaths.CHANNEL_ID)),
	RESPONSE("AriResponse", body -> None()),
	CHANNELVARSET("ChannelVarset", resourceIdFromBody(XPaths.CHANNEL_ID)),
	UNKNOWN("UnknownAriMessage", body -> Some(Try.failure(new RuntimeException(String.format("Failed to extract callContext from body=%s", body)))));

	private final String typeName;
	private final Function<JsonNode, Option<Try<String>>> resourceIdExtractor;

	AriMessageType(final String typeName, final Function<JsonNode, Option<Try<String>>> resourceIdExtractor) {
		this.typeName = typeName;
		this.resourceIdExtractor = resourceIdExtractor;
	}

	public static AriMessageType fromType(final String candidateType) {
		return Set(AriMessageType.values())
				.find(ariMessageType -> ariMessageType.typeName.equals(candidateType))
				.getOrElse(UNKNOWN);
	}

	public Option<Try<String>> extractResourceIdFromBody(final JsonNode body) {
		return resourceIdExtractor.apply(body);
	}

	private static Function<JsonNode, Option<Try<String>>> resourceIdFromBody(final String resourceIdXPath) {
		return body -> Some(
				Option.of(body.at(resourceIdXPath))
						.map(JsonNode::asText)
						.flatMap(type -> StringUtils.isBlank(type) ? None() : Some(type))
						.toTry(() -> new Throwable(String.format("Failed to extract callContext at path=%s from body=%s", resourceIdXPath, body)))
		);
	}

	private static class XPaths {
		static final String BRIDGE_ID = "/bridge/id";
		static final String CHANNEL_ID = "/channel/id";
		static final String PLAYBACK_ID = "/playback/id";
		static final String RECORDING_NAME = "/recording/name";
		static final String PEER_ID = "/peer/id";
	}
}

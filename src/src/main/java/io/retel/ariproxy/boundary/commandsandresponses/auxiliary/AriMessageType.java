package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResourceType.*;
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
  APPLICATION_REPLACED("ApplicationReplaced", null, body -> None()),
  BRIDGE_BLIND_TRANSFER("BridgeBlindTransfer", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  BRIDGE_CREATED("BridgeCreated", BRIDGE, resourceIdFromBody(XPaths.BRIDGE_ID)),
  BRIDGE_DESTROYED("BridgeDestroyed", BRIDGE, resourceIdFromBody(XPaths.BRIDGE_ID)),
  BRIDGE_MERGED("BridgeMerged", BRIDGE, resourceIdFromBody(XPaths.BRIDGE_ID)),
  BRIDGE_VIDEOSOURCE_CHANGED(
      "BridgeVideoSourceChanged", BRIDGE, resourceIdFromBody(XPaths.BRIDGE_ID)),
  CHANNEL_CALLERID("ChannelCallerId", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_CONNECTED_LINE("ChannelConnectedLine", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_CREATED("ChannelCreated", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_DESTROYED("ChannelDestroyed", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_DIALPLAN("ChannelDialplan", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_DTMF_RECEIVED("ChannelDtmfReceived", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_ENTERED_BRIDGE("ChannelEnteredBridge", BRIDGE, resourceIdFromBody(XPaths.BRIDGE_ID)),
  CHANNEL_HANGUP_REQUEST("ChannelHangupRequest", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_HOLD("ChannelHold", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_LEFT_BRIDGE("ChannelLeftBridge", BRIDGE, resourceIdFromBody(XPaths.BRIDGE_ID)),
  CHANNEL_STATE_CHANGE("ChannelStateChange", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_TALKING_FINISHED(
      "ChannelTalkingFinished", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_TALKING_STARTED("ChannelTalkingStarted", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  CHANNEL_UNHOLD("ChannelUnhold", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  DIAL("Dial", null, resourceIdFromBody(XPaths.PEER_ID)),
  PLAYBACK_CONTINUING("PlaybackContinuing", PLAYBACK, resourceIdFromBody(XPaths.PLAYBACK_ID)),
  PLAYBACK_FINISHED("PlaybackFinished", PLAYBACK, resourceIdFromBody(XPaths.PLAYBACK_ID)),
  PLAYBACK_STARTED("PlaybackStarted", PLAYBACK, resourceIdFromBody(XPaths.PLAYBACK_ID)),
  RECORDING_FAILED("RecordingFailed", RECORDING, resourceIdFromBody(XPaths.RECORDING_NAME)),
  RECORDING_FINISHED("RecordingFinished", RECORDING, resourceIdFromBody(XPaths.RECORDING_NAME)),
  RECORDING_STARTED("RecordingStarted", RECORDING, resourceIdFromBody(XPaths.RECORDING_NAME)),
  STASIS_END("StasisEnd", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  STASIS_START("StasisStart", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  RESPONSE("AriResponse", null, body -> None()),
  CHANNELVARSET("ChannelVarset", CHANNEL, resourceIdFromBody(XPaths.CHANNEL_ID)),
  UNKNOWN(
      "UnknownAriMessage",
      null,
      body ->
          Some(
              Try.failure(
                  new RuntimeException(
                      String.format("Failed to extract resourceId from body=%s", body)))));

  private final String typeName;
  private final AriResourceType resourceType;
  private final Function<JsonNode, Option<Try<String>>> resourceIdExtractor;

  AriMessageType(
      final String typeName,
      final AriResourceType resourceType,
      final Function<JsonNode, Option<Try<String>>> resourceIdExtractor) {
    this.typeName = typeName;
    this.resourceType = resourceType;
    this.resourceIdExtractor = resourceIdExtractor;
  }

  public static AriMessageType fromType(final String candidateType) {
    return Set(AriMessageType.values())
        .find(ariMessageType -> ariMessageType.typeName.equals(candidateType))
        .getOrElse(UNKNOWN);
  }

  public Option<AriResourceType> getResourceType() {
    return Option.of(resourceType);
  }

  public Option<Try<String>> extractResourceIdFromBody(final JsonNode body) {
    return resourceIdExtractor.apply(body);
  }

  private static Function<JsonNode, Option<Try<String>>> resourceIdFromBody(
      final String resourceIdXPath) {
    return body ->
        Some(
            Option.of(body.at(resourceIdXPath))
                .map(JsonNode::asText)
                .flatMap(type -> StringUtils.isBlank(type) ? None() : Some(type))
                .toTry(
                    () ->
                        new Throwable(
                            String.format(
                                "Failed to extract resourceId at path=%s from body=%s",
                                resourceIdXPath, body))));
  }

  private static class XPaths {
    static final String BRIDGE_ID = "/bridge/id";
    static final String CHANNEL_ID = "/channel/id";
    static final String PLAYBACK_ID = "/playback/id";
    static final String RECORDING_NAME = "/recording/name";
    static final String PEER_ID = "/peer/id";
  }
}

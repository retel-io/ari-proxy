package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static io.retel.ariproxy.boundary.commandsandresponses.auxiliary.Companion.RESOURCE_ID_POSITION;
import static io.retel.ariproxy.boundary.commandsandresponses.auxiliary.Companion.RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE;
import static io.vavr.API.None;
import static io.vavr.API.Some;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.vavr.Value;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

public enum AriCommandType {
  BRIDGE_CREATION(
      AriResourceType.BRIDGE,
      true,
      resourceIdFromUri(RESOURCE_ID_POSITION),
      resourceIdFromBody("/bridgeId")),
  BRIDGE(
      AriResourceType.BRIDGE,
      false,
      resourceIdFromUri(RESOURCE_ID_POSITION),
      resourceIdFromBody("/bridgeId")),
  CHANNEL_CREATION(
      AriResourceType.CHANNEL,
      true,
      resourceIdFromUri(RESOURCE_ID_POSITION),
      resourceIdFromBody("/channelId")),
  CHANNEL(
      AriResourceType.CHANNEL,
      false,
      resourceIdFromUri(RESOURCE_ID_POSITION),
      resourceIdFromBody("/channelId")),
  PLAYBACK_CREATION(
      AriResourceType.PLAYBACK,
      true,
      resourceIdFromUri(RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE),
      resourceIdFromBody("/playbackId")),
  PLAYBACK(
      AriResourceType.PLAYBACK,
      false,
      resourceIdFromUri(RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE),
      resourceIdFromBody("/playbackId")),
  RECORDING_CREATION(
      AriResourceType.RECORDING, true, AriCommandType::notAvailable, resourceIdFromBody("/name")),
  RECORDING(
      AriResourceType.RECORDING, false, AriCommandType::notAvailable, resourceIdFromBody("/name")),
  SNOOPING_CREATION(
      AriResourceType.SNOOPING,
      true,
      resourceIdFromUri(RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE),
      resourceIdFromBody("/snoopId")),
  SNOOPING(
      AriResourceType.SNOOPING,
      false,
      resourceIdFromUri(RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE),
      resourceIdFromBody("/snoopId")),
  UNKNOWN(AriResourceType.UNKNOWN, false, uri -> None(), body -> None());

  private static final Map<String, AriCommandType> pathsToCommandTypes = new HashMap<>();

  static {
    pathsToCommandTypes.put("/channels", CHANNEL_CREATION);
    pathsToCommandTypes.put("/channels/create", CHANNEL_CREATION);
    pathsToCommandTypes.put("/channels/{channelId}", CHANNEL_CREATION);
    pathsToCommandTypes.put("/channels/{channelId}/continue", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/move", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/redirect", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/answer", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/ring", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/dtmf", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/mute", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/hold", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/moh", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/silence", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/play", PLAYBACK_CREATION);
    pathsToCommandTypes.put("/channels/{channelId}/play/{playbackId}", PLAYBACK_CREATION);
    pathsToCommandTypes.put("/channels/{channelId}/record", RECORDING_CREATION);
    pathsToCommandTypes.put("/channels/{channelId}/variable", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/snoop", SNOOPING_CREATION);
    pathsToCommandTypes.put("/channels/{channelId}/snoop/{snoopId}", SNOOPING_CREATION);
    pathsToCommandTypes.put("/channels/{channelId}/dial", CHANNEL);
    pathsToCommandTypes.put("/channels/{channelId}/rtp_statistics", CHANNEL);
    pathsToCommandTypes.put("/channels/externalMedia", CHANNEL);

    pathsToCommandTypes.put("/bridges", BRIDGE_CREATION);
    pathsToCommandTypes.put("/bridges/{bridgeId}", BRIDGE_CREATION);
    pathsToCommandTypes.put("/bridges/{bridgeId}/addChannel", BRIDGE);
    pathsToCommandTypes.put("/bridges/{bridgeId}/removeChannel", BRIDGE);
    pathsToCommandTypes.put("/bridges/{bridgeId}/videoSource/{channelId}", BRIDGE);
    pathsToCommandTypes.put("/bridges/{bridgeId}/videoSource", BRIDGE);
    pathsToCommandTypes.put("/bridges/{bridgeId}/moh", BRIDGE);
    pathsToCommandTypes.put("/bridges/{bridgeId}/play", PLAYBACK_CREATION);
    pathsToCommandTypes.put("/bridges/{bridgeId}/play/{playbackId}", PLAYBACK_CREATION);
    pathsToCommandTypes.put("/bridges/{bridgeId}/record", RECORDING_CREATION);

    pathsToCommandTypes.put("/playbacks/{playbackId}", PLAYBACK);
    pathsToCommandTypes.put("/playbacks/{playbackId}/control", PLAYBACK);

    pathsToCommandTypes.put("/recordings/stored", RECORDING);
    pathsToCommandTypes.put("/recordings/stored/{recordingName}", RECORDING);
    pathsToCommandTypes.put("/recordings/stored/{recordingName}/file", RECORDING);
    pathsToCommandTypes.put("/recordings/stored/{recordingName}/copy", RECORDING);
    pathsToCommandTypes.put("/recordings/live/{recordingName}", RECORDING);
    pathsToCommandTypes.put("/recordings/live/{recordingName}/stop", RECORDING);
    pathsToCommandTypes.put("/recordings/live/{recordingName}/pause", RECORDING);
    pathsToCommandTypes.put("/recordings/live/{recordingName}/mute", RECORDING);
  }

  private static final ObjectReader reader = new ObjectMapper().reader();

  private final AriResourceType resourceType;
  private final boolean isCreationCommand;
  private final Function<String, Option<Try<String>>> resourceIdUriExtractor;
  private final Function<String, Option<Try<String>>> resourceIdBodyExtractor;

  AriCommandType(
      final AriResourceType resourceType,
      final boolean isCreationCommand,
      final Function<String, Option<Try<String>>> resourceIdUriExtractor,
      final Function<String, Option<Try<String>>> resourceIdBodyExtractor) {
    this.resourceType = resourceType;
    this.isCreationCommand = isCreationCommand;
    this.resourceIdUriExtractor = resourceIdUriExtractor;
    this.resourceIdBodyExtractor = resourceIdBodyExtractor;
  }

  public static AriCommandType fromRequestUri(final String uri) {
    final Optional<AriCommandType> ariCommandType =
        pathsToCommandTypes.entrySet().stream()
            .filter(
                entry -> {
                  final String path = entry.getKey();
                  final String pathRegexWithWildcardsForPlaceholders =
                      path.replaceAll("\\{[^}]+}", "[^\\\\/]+");
                  return uri.matches(pathRegexWithWildcardsForPlaceholders);
                })
            .findFirst()
            .map(Map.Entry::getValue);

    return ariCommandType.orElse(UNKNOWN);
  }

  public AriResourceType getResourceType() {
    return resourceType;
  }

  public boolean isCreationCommand() {
    return isCreationCommand;
  }

  public Option<String> extractResourceIdFromUri(final String uri) {
    if (this == UNKNOWN) {
      return Option.none();
    }

    return this.resourceIdUriExtractor.apply(uri).flatMap(Value::toOption);
  }

  public Option<Try<String>> extractResourceIdFromBody(final String body) {
    return resourceIdBodyExtractor.apply(body);
  }

  private static Function<String, Option<Try<String>>> resourceIdFromUri(
      final int resourceIdPosition) {
    return uri -> {
      if ("/channels/create".equals(uri)) {
        return Some(Try.failure(new Throwable("No ID present in URI")));
      }
      return Some(Try.of(() -> List.of(uri.split("/")).get(resourceIdPosition)));
    };
  }

  private static Function<String, Option<Try<String>>> resourceIdFromBody(
      final String resourceIdXPath) {
    return body ->
        Some(
            Try.of(() -> reader.readTree(body))
                .flatMap(root -> Option.of(root).toTry())
                .toOption()
                .flatMap(root -> Option.of(root.at(resourceIdXPath)))
                .map(JsonNode::asText)
                .flatMap(type -> StringUtils.isBlank(type) ? None() : Some(type))
                .toTry(
                    () ->
                        new Throwable(
                            String.format(
                                "Failed to extract resourceId at path=%s from body=%s",
                                resourceIdXPath, body))));
  }

  private static Option<Try<String>> notAvailable(final String bodyOrUri) {
    return Some(Try.failure(new ExtractorNotAvailable(bodyOrUri)));
  }
}

class Companion {
  static final int RESOURCE_ID_POSITION = 2;
  static final int RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE = 4;
}

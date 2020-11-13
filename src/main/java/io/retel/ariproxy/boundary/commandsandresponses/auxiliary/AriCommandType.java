package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static io.vavr.API.None;
import static io.vavr.API.Some;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public enum AriCommandType {
  BRIDGE_CREATION(AriResourceType.BRIDGE, true, resourceIdFromBody("/bridgeId")),
  BRIDGE(AriResourceType.BRIDGE, false, resourceIdFromBody("/bridgeId")),
  CHANNEL_CREATION(AriResourceType.CHANNEL, true, resourceIdFromBody("/channelId")),
  CHANNEL(AriResourceType.CHANNEL, false, resourceIdFromBody("/channelId")),
  PLAYBACK_CREATION(AriResourceType.PLAYBACK, true, resourceIdFromBody("/playbackId")),
  PLAYBACK(AriResourceType.PLAYBACK, false, resourceIdFromBody("/playbackId")),
  RECORDING_CREATION(AriResourceType.RECORDING, true, resourceIdFromBody("/name")),
  RECORDING(AriResourceType.RECORDING, false, resourceIdFromBody("/name")),
  SNOOPING_CREATION(AriResourceType.SNOOPING, true, resourceIdFromBody("/snoopId")),
  UNKNOWN(AriResourceType.UNKNOWN, false, body -> None());

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

  private static final ObjectReader READER = new ObjectMapper().reader();

  private final AriResourceType resourceType;
  private final boolean isCreationCommand;
  private final Function<String, Option<Try<String>>> resourceIdBodyExtractor;

  AriCommandType(
      final AriResourceType resourceType,
      final boolean isCreationCommand,
      final Function<String, Option<Try<String>>> resourceIdBodyExtractor) {
    this.resourceType = resourceType;
    this.isCreationCommand = isCreationCommand;
    this.resourceIdBodyExtractor = resourceIdBodyExtractor;
  }

  public static AriCommandType fromRequestUri(final String uri) {
    final Optional<AriCommandType> ariCommandType =
        pathsToCommandTypes.entrySet().stream()
            .filter(entry -> pathMatchesPathTemplateWithPlaceholders(uri, entry.getKey()))
            .findFirst()
            .map(Map.Entry::getValue);

    return ariCommandType.orElse(UNKNOWN);
  }

  private static boolean pathMatchesPathTemplateWithPlaceholders(
      final String path, final String pathTemplate) {
    final String regexWithWildcardsForPlaceholders =
        pathTemplate.replaceAll("\\{[^}]+}", "[^\\\\/]+");
    return path.matches(regexWithWildcardsForPlaceholders);
  }

  public AriResourceType getResourceType() {
    return resourceType;
  }

  public boolean isCreationCommand() {
    return isCreationCommand;
  }

  public Option<String> extractResourceIdFromUri(final String uri) {
    if (this == UNKNOWN || uri.equals("/channels/create")) {
      return Option.none();
    }

    final Optional<String> maybePathTemplate =
        pathsToCommandTypes.entrySet().stream()
            .filter(entry -> pathMatchesPathTemplateWithPlaceholders(uri, entry.getKey()))
            .findFirst()
            .map(Map.Entry::getKey);

    if (!maybePathTemplate.isPresent()) {
      return Option.none();
    }

    final String pathTemplate = maybePathTemplate.get();
    final String identifierPlaceholder =
        this.getResourceType().getPathResourceIdentifierPlaceholder();

    final String regex = "(\\{[a-zA-Z]+\\})";
    final java.util.List<String> matches = findAllMatchingGroups(pathTemplate, regex);

    if (matches.isEmpty()) {
      return Option.none();
    }

    final int desiredPosition = matches.indexOf(identifierPlaceholder);
    if (desiredPosition < 0) {
      return Option.none();
    }

    final String placeholderRegex = pathTemplate.replaceAll("\\{[^}]+}", "([^\\\\/]+)");
    final java.util.List<String> uriMatches = findAllMatchingGroups(uri, placeholderRegex);
    if (uriMatches.isEmpty()) {
      return Option.none();
    }

    return Option.of(uriMatches.get(desiredPosition));
  }

  public Option<Try<String>> extractResourceIdFromBody(final String body) {
    return resourceIdBodyExtractor.apply(body);
  }

  private static java.util.List<String> findAllMatchingGroups(final String str, final String regex) {
    final Pattern pattern = Pattern.compile(regex);
    final Matcher matcher = pattern.matcher(str);

    final java.util.List<String> matches = new ArrayList<>();
    while (matcher.find()) {
      for (int i = 1; i <= matcher.groupCount(); i++) {
        matches.add(matcher.group(i));
      }
    }

    return matches;
  }

  private static Function<String, Option<Try<String>>> resourceIdFromBody(
      final String resourceIdXPath) {
    return body ->
        Some(
            Try.of(() -> READER.readTree(body))
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
}

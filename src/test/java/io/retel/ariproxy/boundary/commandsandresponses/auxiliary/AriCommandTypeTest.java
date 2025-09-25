package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommandType.*;
import static io.vavr.API.None;
import static io.vavr.API.Some;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import io.vavr.control.Try;
import io.vavr.control.Try.Failure;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;

// see:
// https://docs.asterisk.org/Asterisk_22_Documentation/API_Documentation/Asterisk_REST_Interface/
class AriCommandTypeTest {

  private static final String BRIDGE_ID = "BRIDGE_ID";
  private static final String CHANNEL_ID = "CHANNEL_ID";
  private static final String PLAYBACK_ID = "PLAYBACK_ID";
  private static final String RECORDING_NAME = "RECORDING_NAME";
  private static final String SNOOP_ID = "SNOOP_ID";

  private static final String BODY_WITH_BRIDGE_ID =
      String.format("{ \"bridgeId\": \"%s\" }", BRIDGE_ID);
  private static final String BODY_WITH_CHANNEL_ID =
      String.format("{ \"channelId\": \"%s\" }", CHANNEL_ID);
  private static final String BODY_WITH_PLAYBACK_ID =
      String.format("{ \"playbackId\": \"%s\" }", PLAYBACK_ID);
  private static final String BODY_WITH_RECORDING_NAME =
      String.format("{ \"name\": \"%s\" }", RECORDING_NAME);
  private static final String BODY_WITH_SNOOP_ID =
      String.format("{ \"snoopId\": \"%s\" }", SNOOP_ID);

  private static final String BRIDGE_CREATION_URI = "/bridges";
  private static final String BRIDGE_CREATION_URI_WITH_ID = String.format("/bridges/%s", BRIDGE_ID);
  private static final String PLAYBACK_ON_BRIDGE_URI = String.format("/bridges/%s/play", BRIDGE_ID);
  private static final String PLAYBACK_ON_BRIDGE_URI_WITH_ID =
      String.format("/bridges/%s/play/%s", BRIDGE_ID, PLAYBACK_ID);
  private static final String RECORDING_ON_BRIDGE_URI =
      String.format("/bridges/%s/record", BRIDGE_ID);

  private static final String CHANNEL_CREATION_URI = "/channels";
  private static final String CHANNEL_CREATION_URI_ALT = "/channels/create";
  private static final String CHANNEL_CREATION_URI_WITH_ID =
      String.format("/channels/%s", CHANNEL_ID);
  private static final String CHANNEL_ANSWER_URI_WITH_ID =
      String.format("/channels/%s/answer", CHANNEL_ID);
  private static final String PLAYBACK_ON_CHANNEL_URI =
      String.format("/channels/%s/play", CHANNEL_ID);
  private static final String PLAYBACK_ON_CHANNEL_URI_WIH_ID =
      String.format("/channels/%s/play/%s", CHANNEL_ID, PLAYBACK_ID);
  private static final String RECORDING_ON_CHANNEL_URI =
      String.format("/channels/%s/record", CHANNEL_ID);
  private static final String SNOOPING_ON_CHANNEL = String.format("/channels/%s/snoop", CHANNEL_ID);
  private static final String SNOOPING_ON_CHANNEL_WITH_ID =
      String.format("/channels/%s/snoop/%s", CHANNEL_ID, SNOOP_ID);
  private static final String TRANSFER_PROGRESS_URI_WITH_ID =
      String.format("/channels/%s/transfer_progress", CHANNEL_ID);

  private static final String INVALID_COMMAND_URI = "/invalid-command-uri";
  private static final String INVALID_COMMAND_BODY = "INVALID JSON";

  @ParameterizedTest
  @MethodSource("commandUriProvider")
  void ensureTheCorrectTypeIsInferredFromTheCommandUri(AriCommandType type, String uri) {
    assertSame(type, AriCommandType.fromRequestUri(uri));
  }

  @ParameterizedTest
  @MethodSource("commandUriWithIdProvider")
  void ensureExtractResourceIdFromUriWorksForAnyType(String uri, String expectedResourceId) {
    assertThat(
        AriCommandType.fromRequestUri(uri).extractResourceIdFromUri(uri),
        is(Some(expectedResourceId)));
  }

  @ParameterizedTest
  @MethodSource("commandBodyProvider")
  void ensureExtractResourceIdFromBodyWorksForAnyType(
      AriCommandType type, String body, String expectedResourceId) {
    assertThat(type.extractResourceIdFromBody(body), is(Some(Try.success(expectedResourceId))));
  }

  @ParameterizedTest
  @EnumSource(
      value = AriCommandType.class,
      mode = Mode.EXCLUDE,
      names = {"UNKNOWN"})
  void ensureInvalidUriAndBodyResultInAFailure(AriCommandType type) {
    assertAll(
        String.format("Extractors for type=%s", type),
        () -> assertEquals(Option.none(), type.extractResourceIdFromUri(INVALID_COMMAND_URI)),
        () ->
            assertThat(
                type.extractResourceIdFromBody(INVALID_COMMAND_BODY).get(),
                instanceOf(Failure.class)));
  }

  @Test
  void ensureCommandsNotCreatingANewResourceResultInANone() {
    assertAll(
        () -> assertThat(UNKNOWN.extractResourceIdFromUri(INVALID_COMMAND_URI), is(None())),
        () -> assertThat(UNKNOWN.extractResourceIdFromBody(INVALID_COMMAND_BODY), is(None())));
  }

  private static Stream<Arguments> commandUriProvider() {
    return Stream.of(
        Arguments.of(BRIDGE_CREATION, BRIDGE_CREATION_URI),
        Arguments.of(BRIDGE_CREATION, BRIDGE_CREATION_URI_WITH_ID),
        Arguments.of(CHANNEL_CREATION, CHANNEL_CREATION_URI),
        Arguments.of(CHANNEL_CREATION, CHANNEL_CREATION_URI_ALT),
        Arguments.of(CHANNEL_CREATION, CHANNEL_CREATION_URI_WITH_ID),
        Arguments.of(CHANNEL, CHANNEL_ANSWER_URI_WITH_ID),
        Arguments.of(CHANNEL, TRANSFER_PROGRESS_URI_WITH_ID),
        Arguments.of(PLAYBACK_CREATION, PLAYBACK_ON_BRIDGE_URI),
        Arguments.of(PLAYBACK_CREATION, PLAYBACK_ON_BRIDGE_URI_WITH_ID),
        Arguments.of(PLAYBACK_CREATION, PLAYBACK_ON_CHANNEL_URI),
        Arguments.of(PLAYBACK_CREATION, PLAYBACK_ON_CHANNEL_URI_WIH_ID),
        Arguments.of(RECORDING_CREATION, RECORDING_ON_CHANNEL_URI),
        Arguments.of(RECORDING_CREATION, RECORDING_ON_BRIDGE_URI),
        Arguments.of(SNOOPING_CREATION, SNOOPING_ON_CHANNEL),
        Arguments.of(SNOOPING_CREATION, SNOOPING_ON_CHANNEL_WITH_ID),
        Arguments.of(UNKNOWN, INVALID_COMMAND_URI));
  }

  private static Stream<Arguments> commandUriWithIdProvider() {
    return Stream.of(
        Arguments.of(BRIDGE_CREATION_URI_WITH_ID, BRIDGE_ID),
        Arguments.of(CHANNEL_CREATION_URI_WITH_ID, CHANNEL_ID),
        Arguments.of(PLAYBACK_ON_BRIDGE_URI_WITH_ID, PLAYBACK_ID),
        Arguments.of(PLAYBACK_ON_CHANNEL_URI_WIH_ID, PLAYBACK_ID),
        Arguments.of(SNOOPING_ON_CHANNEL_WITH_ID, SNOOP_ID));
  }

  private static Stream<Arguments> commandBodyProvider() {
    return Stream.of(
        Arguments.of(BRIDGE, BODY_WITH_BRIDGE_ID, BRIDGE_ID),
        Arguments.of(CHANNEL, BODY_WITH_CHANNEL_ID, CHANNEL_ID),
        Arguments.of(PLAYBACK, BODY_WITH_PLAYBACK_ID, PLAYBACK_ID),
        Arguments.of(RECORDING_CREATION, BODY_WITH_RECORDING_NAME, RECORDING_NAME),
        Arguments.of(SNOOPING_CREATION, BODY_WITH_SNOOP_ID, SNOOP_ID));
  }
}

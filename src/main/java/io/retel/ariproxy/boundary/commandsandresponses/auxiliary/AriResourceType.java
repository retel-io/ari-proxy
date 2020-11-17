package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import java.util.Arrays;
import java.util.Optional;

public enum AriResourceType {
  BRIDGE("{bridgeId}"),
  CHANNEL("{channelId}"),
  PLAYBACK("{playbackId}"),
  RECORDING("{recordingName}"),
  SNOOPING("{snoopId}"),
  UNKNOWN(null);

  private final String pathResourceIdPlaceholder;

  AriResourceType(final String pathResourceIdPlaceholder) {
    this.pathResourceIdPlaceholder = pathResourceIdPlaceholder;
  }

  public static Optional<AriResourceType> of(final String pathResourceIdentifierPlaceholder) {
    return Arrays.stream(values())
        .filter(
            item ->
                item.getPathResourceIdentifierPlaceholder()
                    .equals(pathResourceIdentifierPlaceholder))
        .findFirst();
  }

  public String getPathResourceIdentifierPlaceholder() {
    return pathResourceIdPlaceholder;
  }
}

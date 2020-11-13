package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

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

  public String getPathResourceIdentifierPlaceholder() {
    return pathResourceIdPlaceholder;
  }
}

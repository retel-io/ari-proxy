package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AriCommandEnvelope {

  private AriCommand ariCommand;
  private String callContext;
  private String commandId;

  private AriCommandEnvelope() {}

  public AriCommandEnvelope(AriCommand ariCommand, String callContext, String commandId) {
    this.ariCommand = ariCommand;
    this.callContext = callContext;
    this.commandId = commandId;
  }

  public AriCommand getAriCommand() {
    return ariCommand;
  }

  public String getCallContext() {
    return callContext;
  }

  public String getCommandId() {
    return commandId;
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}

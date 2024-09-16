package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

public class CommandRequest {
  private final String method;
  private final String url;

  public CommandRequest(String method, String url) {
    this.method = method;
    this.url = url;
  }

  public String getMethod() {
    return method;
  }

  public String getUrl() {
    return url;
  }

  @Override
  public String toString() {
    return reflectionToString(this, SHORT_PREFIX_STYLE);
  }
}

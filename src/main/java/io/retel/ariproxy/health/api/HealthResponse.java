package io.retel.ariproxy.health.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class HealthResponse {

  private final List<String> errors;
  private final boolean isOk;

  private HealthResponse(boolean isOk, List<String> errors) {
    this.isOk = isOk;
    this.errors = errors;
  }

  @JsonProperty
  public boolean isOk() {
    return isOk;
  }

  @JsonProperty
  public List<String> errors() {
    return errors;
  }

  public static HealthResponse fromErrors(List<String> errors) {
    return new HealthResponse(errors.size() == 0, errors);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}

package io.retel.ariproxy.health.api;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import io.vavr.collection.List;

public class HealthReport {
  private List<String> errors;

  public static HealthReport empty() {
    return new HealthReport(List.empty());
  }

  public static HealthReport ok() {
    return empty();
  }

  public static HealthReport error(final String error) {
    return new HealthReport(List.of(error));
  }

  public static HealthReport error(final Class<?> checkClass, final String error) {
    return new HealthReport(List.of("%s: %s".formatted(checkClass.getSimpleName(), error)));
  }

  private HealthReport(final List<String> errors) {
    this.errors = errors;
  }

  public List<String> errors() {
    return errors;
  }

  public HealthReport merge(HealthReport other) {
    if (other != null) {
      return new HealthReport(errors.appendAll(other.errors()));
    }
    return this;
  }

  @Override
  public String toString() {
    return reflectionToString(this, SHORT_PREFIX_STYLE);
  }

  @Override
  public boolean equals(final Object o) {
    return reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return reflectionHashCode(this);
  }
}

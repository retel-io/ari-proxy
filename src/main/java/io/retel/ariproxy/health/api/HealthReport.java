package io.retel.ariproxy.health.api;

import io.vavr.collection.List;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class HealthReport {
	private List<String> errors;

	public static HealthReport empty() {
		return new HealthReport(List.empty());
	}

	public static HealthReport ok() {
		return empty();
	}

	public static HealthReport error(String error) {
		return new HealthReport(List.of(error));
	}

	private HealthReport(List<String> errors) {
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
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}

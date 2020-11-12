package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

public class AriCommandResourceDto {
	private final String type;
	private final String id;

	AriCommandResourceDto(final String type, final String id) {
		this.type = type;
		this.id = id;
	}

	public String getType() {
		return type;
	}

	public String getId() {
		return id;
	}

	public static AriCommandResourceDto of(
			final AriCommandResource commandResource
	) {
		return new AriCommandResourceDto(
				commandResource.getType().name().replace("_CREATION", ""), // TODO: do not clean with replace
				commandResource.getId().orElse(null)
		);
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

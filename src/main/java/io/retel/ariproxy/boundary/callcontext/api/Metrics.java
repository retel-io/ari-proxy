package io.retel.ariproxy.boundary.callcontext.api;

import java.io.Serializable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Metrics implements Serializable {

	private final int maxSize;
	private final int currentSize;

	public Metrics(int maxSize, int currentSize) {
		this.maxSize = maxSize;
		this.currentSize = currentSize;
	}

	public int maxSize() {
		return maxSize;
	}

	public int currentSize() {
		return currentSize;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}

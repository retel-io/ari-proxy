package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class AriCommandEnvelope {

	private AriCommand ariCommand;
	private String resourceId;

	private AriCommandEnvelope() {}

	public AriCommandEnvelope(AriCommand ariCommand, String resourceId) {
		this.ariCommand = ariCommand;
		this.resourceId = resourceId;
	}

	public AriCommand getAriCommand() {
		return ariCommand;
	}

	/*

	 * represents asterisk resource
	 *
	 * e.g. ChannelId, PlaybackID, etc
	 *
	 */
	public String getResourceId() {
		return resourceId;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}

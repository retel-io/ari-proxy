package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

public class AriCommand {

	private String method = null;
	private String url = null;
	private String body = null;

	public AriCommand() {
	}

	public AriCommand(final String method, final String url, final String body) {
		this.method = method;
		this.url = url;
		this.body = body;
	}

	public String getMethod() {
		return method;
	}

	public String getUrl() {
		return url;
	}

	public String getBody() {
		return body;
	}

	@Override
	public String toString() {
		return reflectionToString(this, SHORT_PREFIX_STYLE);
	}
}

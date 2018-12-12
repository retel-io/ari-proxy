package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.fasterxml.jackson.databind.JsonNode;

public class AriCommand {

	private String method = null;
	private String url = null;
	private JsonNode body = null;

	public AriCommand() {
	}

	public AriCommand(final String method, final String url, final JsonNode body) {
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

	public JsonNode getBody() {
		return body;
	}

	@Override
	public String toString() {
		return reflectionToString(this, SHORT_PREFIX_STYLE);
	}
}

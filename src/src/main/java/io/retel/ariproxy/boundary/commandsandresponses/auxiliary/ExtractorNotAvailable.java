package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

public class ExtractorNotAvailable extends Throwable {

	private final String bodyOrUri;

	public ExtractorNotAvailable(final String bodyOrUri) {
		this.bodyOrUri = bodyOrUri;
	}

	@Override
	public String getMessage() {
		return String.format("No extractor defined for body/uri=%s", bodyOrUri);
	}
}

package io.retel.ariproxy.boundary.callcontext.api;

public class CallContextLookupError extends Exception {

  private final String message;

  public CallContextLookupError(final String message) {
    this.message = message;
  }

  @Override
  public String getMessage() {
    return message;
  }
}

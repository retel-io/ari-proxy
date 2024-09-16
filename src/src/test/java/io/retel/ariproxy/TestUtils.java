package io.retel.ariproxy;

public class TestUtils {

  private static final String CALL_CONTEXT_KEY_PREFIX = "ari-proxy:call-context-provider";

  public static String withCallContextKeyPrefix(String resourceId) {
    return CALL_CONTEXT_KEY_PREFIX + ":" + resourceId;
  }
}

package io.retel.ariproxy.boundary.callcontext;

import static org.junit.jupiter.api.Assertions.*;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import com.typesafe.config.ConfigFactory;
import io.retel.ariproxy.boundary.callcontext.api.*;
import io.retel.ariproxy.health.api.HealthReport;
import io.vavr.control.Option;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class CallContextProviderTest {

  private static final ActorTestKit testKit =
      ActorTestKit.create("testKit", ConfigFactory.defaultApplication());

  private static final String RESOURCE_ID = "theResourceId";
  private static final String CALL_CONTEXT_FROM_DB = "theCallContextFromDB";
  private static final String CALL_CONTEXT_FROM_CHANNEL_VARS = "theCallContextFromChannelVars";

  @Test
  void verifyRegisterCallContextReturnsThatContext() {
    final ActorRef<CallContextProviderMessage> callContextProvider =
        testKit.spawn(CallContextProvider.create(new MemoryKeyValueStore()));
    final TestProbe<CallContextRegistered> probe =
        testKit.createTestProbe(CallContextRegistered.class);

    callContextProvider.tell(
        new RegisterCallContext(RESOURCE_ID, CALL_CONTEXT_FROM_DB, probe.getRef()));

    probe.expectMessage(new CallContextRegistered(RESOURCE_ID, CALL_CONTEXT_FROM_DB));
  }

  @Test
  void verifyCreateIfMissingPolicyIsAppliedProperly() {
    final Map<String, String> store = new HashMap<>();
    final ActorRef<CallContextProviderMessage> callContextProvider =
        testKit.spawn(CallContextProvider.create(new MemoryKeyValueStore(store)));
    final TestProbe<ProvideCallContextResponse> probe =
        testKit.createTestProbe(ProvideCallContextResponse.class);

    callContextProvider.tell(
        new ProvideCallContext(
            RESOURCE_ID, ProviderPolicy.CREATE_IF_MISSING, Option.none(), probe.getRef()));

    final CallContextProvided response = probe.expectMessageClass(CallContextProvided.class);
    assertDoesNotThrow(() -> UUID.fromString(response.callContext()));
    assertTrue(StringUtils.isNotBlank(store.get(RESOURCE_ID)));
  }

  @Test
  void verifyCreateIfMissingPolicyIsAppliedProperlyWhenCallContextIsProvidedInChannelVar() {
    final Map<String, String> store = new HashMap<>();
    final ActorRef<CallContextProviderMessage> callContextProvider =
        testKit.spawn(CallContextProvider.create(new MemoryKeyValueStore(store)));
    final TestProbe<ProvideCallContextResponse> probe =
        testKit.createTestProbe(ProvideCallContextResponse.class);

    callContextProvider.tell(
        new ProvideCallContext(
            RESOURCE_ID,
            ProviderPolicy.CREATE_IF_MISSING,
            Option.some(CALL_CONTEXT_FROM_CHANNEL_VARS),
            probe.getRef()));

    final CallContextProvided response = probe.expectMessageClass(CallContextProvided.class);
    assertEquals(CALL_CONTEXT_FROM_CHANNEL_VARS, response.callContext());
    assertEquals(CALL_CONTEXT_FROM_CHANNEL_VARS, store.get(RESOURCE_ID));
  }

  @Test
  void verifyCreateIfMissingPolicyIsAppliedProperlyWhenCallContextIsProvidedInChannelVarAndInDB() {
    final Map<String, String> store = new HashMap<>();
    store.put(RESOURCE_ID, CALL_CONTEXT_FROM_DB);
    final ActorRef<CallContextProviderMessage> callContextProvider =
        testKit.spawn(CallContextProvider.create(new MemoryKeyValueStore(store)));
    final TestProbe<ProvideCallContextResponse> probe =
        testKit.createTestProbe(ProvideCallContextResponse.class);

    callContextProvider.tell(
        new ProvideCallContext(
            RESOURCE_ID,
            ProviderPolicy.CREATE_IF_MISSING,
            Option.some(CALL_CONTEXT_FROM_CHANNEL_VARS),
            probe.getRef()));

    final CallContextProvided response = probe.expectMessageClass(CallContextProvided.class);
    assertEquals(CALL_CONTEXT_FROM_CHANNEL_VARS, response.callContext());
    assertEquals(CALL_CONTEXT_FROM_CHANNEL_VARS, store.get(RESOURCE_ID));
  }

  @Test
  void verifyLookupOnlyPolicyIsAppliedProperlyIfEntryAlreadyExisted() {
    final Map<String, String> store = new HashMap<>();
    store.put(RESOURCE_ID, CALL_CONTEXT_FROM_DB);
    final ActorRef<CallContextProviderMessage> callContextProvider =
        testKit.spawn(CallContextProvider.create(new MemoryKeyValueStore(store)));
    final TestProbe<ProvideCallContextResponse> probe =
        testKit.createTestProbe(ProvideCallContextResponse.class);

    callContextProvider.tell(
        new ProvideCallContext(
            RESOURCE_ID, ProviderPolicy.LOOKUP_ONLY, Option.none(), probe.getRef()));

    final CallContextProvided response = probe.expectMessageClass(CallContextProvided.class);
    assertEquals(CALL_CONTEXT_FROM_DB, response.callContext());
    assertEquals(CALL_CONTEXT_FROM_DB, store.get(RESOURCE_ID));
  }

  @Test
  void failureResponseIsReceivedWhenNoCallContextExists() {
    final ActorRef<CallContextProviderMessage> callContextProvider =
        testKit.spawn(CallContextProvider.create(new MemoryKeyValueStore()));
    final TestProbe<ProvideCallContextResponse> probe =
        testKit.createTestProbe(ProvideCallContextResponse.class);

    callContextProvider.tell(
        new ProvideCallContext(
            RESOURCE_ID, ProviderPolicy.LOOKUP_ONLY, Option.none(), probe.getRef()));

    probe.expectMessageClass(CallContextLookupError.class);
  }

  @Test
  void ensureHealthReportIsGeneratedOnRequest() {
    final ActorRef<CallContextProviderMessage> callContextProvider =
        testKit.spawn(CallContextProvider.create(new MemoryKeyValueStore()));
    final TestProbe<HealthReport> probe = testKit.createTestProbe(HealthReport.class);

    callContextProvider.tell(new ReportHealth(probe.getRef()));

    probe.expectMessage(HealthReport.ok());
  }

  @AfterAll
  public static void cleanup() {
    testKit.shutdownTestKit();
  }
}

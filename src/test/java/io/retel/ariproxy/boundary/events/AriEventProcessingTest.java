package io.retel.ariproxy.boundary.events;

import static io.retel.ariproxy.boundary.events.StasisEvents.applicationReplacedEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.invalidEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.playbackFinishedEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.recordingFinishedEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.stasisStartEvent;
import static io.retel.ariproxy.boundary.events.StasisEvents.unknownEvent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.scaladsl.model.ws.TextMessage.Strict;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.metrics.IncreaseCounter;
import io.retel.ariproxy.metrics.StartCallSetupTimer;
import io.vavr.collection.Seq;
import io.vavr.concurrent.Future;
import io.vavr.control.Try;
import java.util.function.Function;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.ValueSource;

class AriEventProcessingTest {

	private final String TEST_SYSTEM = this.getClass().getSimpleName();
	private ActorSystem system;
	private static final String fakeCommandsTopic = "commands";
	private static final String fakeEventsAndResponsesTopic = "events-and-responses";
	private static final Function<ActorRef, Runnable> genApplicationReplacedHandler =
			ref -> () -> ref.tell("Shutdown triggered!", ref);

	@AfterEach
	void teardown() {
		TestKit.shutdownActorSystem(system);
		system.terminate();
	}

	@BeforeEach
	void setup() {
		system = ActorSystem.create(TEST_SYSTEM);
	}

	@Test()
	@DisplayName("Ensure an UnparsableMessageException is thrown for a bogus message.")
	void generateProducerRecordFromEventHandlesUnparseableMessage() {
		new TestKit(system)
		{
			{
				assertThrows(RuntimeException.class, () -> AriEventProcessing
						.generateProducerRecordFromEvent(fakeCommandsTopic, fakeEventsAndResponsesTopic, new Strict(invalidEvent), getRef(), system.log(), genApplicationReplacedHandler.apply(getRef())));
			}
		};
	}

	@Test
	void throwRuntimeExceptionWhenEncounteringAnUnknownEvent() {
		new TestKit(system)
		{
			{
				assertThrows(RuntimeException.class, () -> AriEventProcessing.generateProducerRecordFromEvent(
						fakeCommandsTopic, fakeEventsAndResponsesTopic, new Strict(unknownEvent), getRef(), system.log(), genApplicationReplacedHandler.apply(getRef()))
				);
			}
		};
	}

	@DisplayName("Verify processing of both channel and playback events results in the expected kafka producer record")
	@ParameterizedTest
	@ValueSource(strings = { stasisStartEvent, playbackFinishedEvent, recordingFinishedEvent })
	void generateProducerRecordFromAllAriMessageTypes(String ariEvent) {
		new TestKit(system)
		{
			{
				final Future<Source<ProducerRecord<String, String>, NotUsed>> wsToKafkaProcessor = Future.of(
						() -> AriEventProcessing
								.generateProducerRecordFromEvent(fakeCommandsTopic, fakeEventsAndResponsesTopic, new Strict(ariEvent), getRef(), system.log(), genApplicationReplacedHandler.apply(getRef()))
				);

				expectMsgClass(ProvideCallContext.class);
				reply(new CallContextProvided("CALL_CONTEXT"));

				final ProducerRecord<String, String> record = wsToKafkaProcessor
						.flatMap(source -> Future.fromCompletableFuture(source.runWith(
								Sink.last(),
								ActorMaterializer.create(ActorMaterializerSettings.create(system), system))
								.toCompletableFuture())
						)
						.await()
						.get();

				assertThat(record.key(), is("CALL_CONTEXT"));
				assertThat(record.topic(), is(fakeEventsAndResponsesTopic));
			}
		};
	}

	@ParameterizedTest
	@EnumSource(value = AriMessageType.class, mode = Mode.EXCLUDE, names = {"STASIS_START", "STASIS_END"})
	void verifyNoMetricsAreGatheredForTheSpecifiedEventTypes(AriMessageType type) {
		final Seq<MetricsGatherer> decision = AriEventProcessing
				.determineMetricsGatherer(type);

		assertThat(
				((IncreaseCounter) decision.get(0).withCallContextSupplier(() -> "CALL_CONTEXT")).getName(),
				is(type.name())
		);
	}

	@Test
	void checkApplicationReplacedHandlerIsTriggered() {
		new TestKit(system)
		{
			{
				final Future<Source<ProducerRecord<String, String>, NotUsed>> wsToKafkaProcessor = Future.of(
						() -> AriEventProcessing
								.generateProducerRecordFromEvent(fakeCommandsTopic, fakeEventsAndResponsesTopic, new Strict(applicationReplacedEvent), getRef(), system.log(), genApplicationReplacedHandler.apply(getRef()))
				);
				assertThat(expectMsgClass(String.class), is("Shutdown triggered!"));
				assertThat(wsToKafkaProcessor.await().get(), is(Source.empty()));
			}
		};
	}

	@Test
	void verifyTheRequiredMetricsAreGatheredForStasisStart() {
		final Seq<MetricsGatherer> metricsGatherers = AriEventProcessing
				.determineMetricsGatherer(AriMessageType.STASIS_START);

		metricsGatherers.forEach(metricsGatherer -> {
			final Object metricsReq = metricsGatherer.withCallContextSupplier(() -> "CALL_CONTEXT");
			System.out.println(metricsReq);
		});

		final Seq<Object> metricsRequests = metricsGatherers
				.map(metricsGatherer -> metricsGatherer.withCallContextSupplier(() -> "CALL_CONTEXT"));

		assertThat(len(metricsGatherers), is(3));

		final IncreaseCounter eventTypeCounter = (IncreaseCounter) metricsRequests.get(0);
		final IncreaseCounter callsStartedCounter = (IncreaseCounter) metricsRequests.get(1);
		final StartCallSetupTimer callSetupTimer = (StartCallSetupTimer) metricsRequests.get(2);

		assertThat(eventTypeCounter.getName(), is(AriMessageType.STASIS_START.name()));
		assertThat(callsStartedCounter.getName(), is("CallsStarted"));
		assertThat(callSetupTimer.getCallContext(), is("CALL_CONTEXT"));
	}

	@Test
	void verifyTheRequiredMetricsAreGatheredForStasisEnd() {
		final Seq<MetricsGatherer> metricsGatherers = AriEventProcessing
				.determineMetricsGatherer(AriMessageType.STASIS_END);

		final Seq<Object> metricsRequests = metricsGatherers
				.map(metricsGatherer -> metricsGatherer.withCallContextSupplier(() -> "CALL_CONTEXT"));

		assertThat(len(metricsGatherers), is(2));

		final IncreaseCounter eventTypeCounter = (IncreaseCounter) metricsRequests.get(0);
		final IncreaseCounter callsEndedCounter = (IncreaseCounter) metricsRequests.get(1);

		assertThat(eventTypeCounter.getName(), is(AriMessageType.STASIS_END.name()));
		assertThat(callsEndedCounter.getName(), is("CallsEnded"));
	}

	@Test
	void verifyGetCallContextWorksAsExpected() {
		new TestKit(system)
		{
			{
				final Future<Try<String>> callContext = Future.of(() -> AriEventProcessing.getCallContext("RESOURCE_ID", getRef(),
						ProviderPolicy.CREATE_IF_MISSING));

				final ProvideCallContext provideCallContext = expectMsgClass(ProvideCallContext.class);

				assertThat(provideCallContext.policy(), is(ProviderPolicy.CREATE_IF_MISSING));
				assertThat(provideCallContext.resourceId(), is("RESOURCE_ID"));
				reply(new CallContextProvided("CALL_CONTEXT"));

				assertThat(callContext.await().get().get(), is("CALL_CONTEXT"));
			}
		};
	}

	@Test
	void verifyGetCallContextReturnsAFailedTryIfNoCallContextCanBeProvided() {
		new TestKit(system) {{
			assertThat(AriEventProcessing.getCallContext("RESOURCE_ID", getRef(), ProviderPolicy.CREATE_IF_MISSING).isFailure(), is(true));
		}};
	}

	@ParameterizedTest
	@ValueSource(strings = {"/type", "/channel/id"})
	void verifyPropertiesCanBeExtractedFromAChannelMessage(String path) {
		AriEventProcessing.getValueFromMessageByPath(new Strict(stasisStartEvent), path);
	}

	@ParameterizedTest
	@ValueSource(strings = {"/type", "/playback/id"})
	void verifyPropertiesCanBeExtractedFromAPlaybackMessage(String path) {
		AriEventProcessing.getValueFromMessageByPath(new Strict(playbackFinishedEvent), path);
	}

	@Test
	void verifyGetValueFromMessageByPathHandlesInvalidMessagesProperly() {
		assertThat(AriEventProcessing.getValueFromMessageByPath(new Strict(invalidEvent), "/smth").isLeft(), is(true));
	}

	private static int len(Seq<?> seq) {
		return seq.foldLeft(0, (acc, item) -> acc + 1);
	}
}

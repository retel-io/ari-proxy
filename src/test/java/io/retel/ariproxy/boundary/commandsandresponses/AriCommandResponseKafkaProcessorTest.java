package io.retel.ariproxy.boundary.commandsandresponses;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.callcontext.api.RegisterCallContext;
import io.retel.ariproxy.config.ConfigLoader;
import io.retel.ariproxy.metrics.StopCallSetupTimer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.concurrent.duration.Duration;

class AriCommandResponseKafkaProcessorTest {

	private final String TEST_SYSTEM = this.getClass().getSimpleName();
	private ActorSystem system;

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
	void properlyHandleInvalidCommandMessage() {
		final TestKit kafkaProducer = new TestKit(system);
		final TestKit metricsService = new TestKit(system);
		final TestKit callContextProvider = new TestKit(system);

		final ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("topic", 0, 0,
				"key", "NOT JSON");
		final Source<ConsumerRecord<String, String>, NotUsed> source = Source.single(consumerRecord);
		final Sink<ProducerRecord<String, String>, NotUsed> sink = Sink.<ProducerRecord<String, String>>ignore()
				.mapMaterializedValue(q -> NotUsed.getInstance());

		AriCommandResponseKafkaProcessor.commandResponseProcessing()
				.withConfig(ConfigLoader.load())
				.on(system)
				.withHandler(requestAndContext -> Http.get(system).singleRequest(requestAndContext._1))
				.withCallContextProvider(callContextProvider.getRef())
				.withMetricsService(metricsService.getRef())
				.from(source)
				.to(sink)
				.run();

		kafkaProducer.expectNoMsg(Duration.apply(250, TimeUnit.MILLISECONDS));
	}

	@Test()
	void handlePlaybackCommand() {

		final TestKit kafkaProducer = new TestKit(system);
		final TestKit metricsService = new TestKit(system);
		final TestKit callContextProvider = new TestKit(system);

		final ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("topic", 0, 0,
				"none", "{\n"
				+ "   \"resourceId\" : \"1533286879.42\",\n"
				+ "   \"ariCommand\" : {\n"
				+ "      \"url\" : \"/channels/1533286879.42/play/c4958563-1ba4-4f2f-a60f-626a624bf0e6\",\n"
				+ "      \"method\" : \"POST\",\n"
				+ "      \"body\" : \"{\\\"media\\\": \\\"sound:hd/register_success\\\", \\\"lang\\\":\\\"de\\\"}\"\n"
				+ "   }\n"
				+ "}");

		final Source<ConsumerRecord<String, String>, NotUsed> source = Source.single(consumerRecord);
		final Sink<ProducerRecord<String, String>, NotUsed> sink = Sink
				.actorRef(kafkaProducer.getRef(), new ProducerRecord<String, String>("topic", "endMessage"));

		AriCommandResponseKafkaProcessor.commandResponseProcessing()
				.withConfig(ConfigLoader.load())
				.on(system)
				.withHandler(r -> CompletableFuture.supplyAsync(() ->
						HttpResponse.create().withStatus(StatusCodes.NO_CONTENT))
				)
				.withCallContextProvider(callContextProvider.getRef())
				.withMetricsService(metricsService.getRef())
				.from(source)
				.to(sink)
				.run();

		final ProvideCallContext provideCallContext = callContextProvider.expectMsgClass(ProvideCallContext.class);
		assertThat(provideCallContext.resourceId(), is("1533286879.42"));
		assertThat(provideCallContext.policy(), is(ProviderPolicy.LOOKUP_ONLY));
		callContextProvider.reply(new CallContextProvided("CALL CONTEXT"));

		final RegisterCallContext registerCallContext = callContextProvider.expectMsgClass(RegisterCallContext.class);
		assertThat(registerCallContext.callContext(), is("CALL CONTEXT"));
		assertThat(registerCallContext.resourceId(), is("c4958563-1ba4-4f2f-a60f-626a624bf0e6"));
		callContextProvider.reply(new CallContextProvided("CALL CONTEXT"));

		final StopCallSetupTimer stopCallSetupTimer = metricsService.expectMsgClass(StopCallSetupTimer.class);
		assertThat(stopCallSetupTimer.getCallcontext(), is("CALL CONTEXT"));
		assertThat(stopCallSetupTimer.getApplication(), is("test-app"));

		final ProducerRecord responseRecord = kafkaProducer.expectMsgClass(ProducerRecord.class);
		assertThat(responseRecord.topic(), is("eventsAndResponsesTopic"));
		assertThat(responseRecord.key(), is("CALL CONTEXT"));

		final ProducerRecord endMsg = kafkaProducer.expectMsgClass(ProducerRecord.class);
		assertThat(endMsg.topic(), is("topic"));
		assertThat(endMsg.value(), is("endMessage"));
	}

	@Test()
	void handleAnswerCommand() {
		final TestKit kafkaProducer = new TestKit(system);
		final TestKit metricsService = new TestKit(system);
		final TestKit callContextProvider = new TestKit(system);

		final ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("topic", 0, 0,
				"none", "{\n"
				+ "   \"resourceId\" : \"1533218784.36\",\n"
				+ "   \"ariCommand\" : {\n"
				+ "      \"url\" : \"/channels/1533218784.36/answer\",\n"
				+ "      \"body\" : \"\",\n"
				+ "      \"method\" : \"POST\"\n"
				+ "   }\n"
				+ "}");
		final Source<ConsumerRecord<String, String>, NotUsed> source = Source.single(consumerRecord);
		final Sink<ProducerRecord<String, String>, NotUsed> sink = Sink
				.actorRef(kafkaProducer.getRef(), new ProducerRecord<String, String>("topic", "endMessage"));

		AriCommandResponseKafkaProcessor.commandResponseProcessing()
				.withConfig(ConfigLoader.load())
				.on(system)
				.withHandler(r -> CompletableFuture.supplyAsync(() ->
						HttpResponse.create().withStatus(StatusCodes.NO_CONTENT))
				)
				.withCallContextProvider(callContextProvider.getRef())
				.withMetricsService(metricsService.getRef())
				.from(source)
				.to(sink)
				.run();

		final ProvideCallContext provideCallContext = callContextProvider.expectMsgClass(ProvideCallContext.class);
		assertThat(provideCallContext.resourceId(), is("1533218784.36"));
		assertThat(provideCallContext.policy(), is(ProviderPolicy.LOOKUP_ONLY));
		callContextProvider.reply(new CallContextProvided("CALL CONTEXT"));

		final StopCallSetupTimer stopCallSetupTimer = metricsService.expectMsgClass(StopCallSetupTimer.class);
		assertThat(stopCallSetupTimer.getCallcontext(), is("CALL CONTEXT"));
		assertThat(stopCallSetupTimer.getApplication(), is("test-app"));

		final ProducerRecord responseRecord = kafkaProducer.expectMsgClass(ProducerRecord.class);
		assertThat(responseRecord.topic(), is("eventsAndResponsesTopic"));
		assertThat(responseRecord.key(), is("CALL CONTEXT"));

		final ProducerRecord endMsg = kafkaProducer.expectMsgClass(ProducerRecord.class);
		assertThat(endMsg.topic(), is("topic"));
		assertThat(endMsg.value(), is("endMessage"));
	}
}

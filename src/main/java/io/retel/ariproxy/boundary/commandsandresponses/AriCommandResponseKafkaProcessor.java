package io.retel.ariproxy.boundary.commandsandresponses;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.japi.function.Function;
import akka.japi.function.Procedure;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.Supervision;
import akka.stream.Supervision.Directive;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.retel.ariproxy.akkajavainterop.PatternsAdapter;
import io.retel.ariproxy.boundary.callcontext.api.CallContextProvided;
import io.retel.ariproxy.boundary.callcontext.api.ProvideCallContext;
import io.retel.ariproxy.boundary.callcontext.api.ProviderPolicy;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommand;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriCommandEnvelope;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageEnvelope;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriMessageType;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.AriResponse;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.CallContextAndResourceId;
import io.retel.ariproxy.boundary.commandsandresponses.auxiliary.CommandResponseHandler;
import io.retel.ariproxy.boundary.processingpipeline.ProcessingPipeline;
import io.retel.ariproxy.config.ServiceConfig;
import io.retel.ariproxy.metrics.StopCallSetupTimer;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public class AriCommandResponseKafkaProcessor {

	private static final Attributes LOG_LEVELS = Attributes.createLogLevels(
			Logging.InfoLevel(),
			Logging.InfoLevel(),
			Logging.ErrorLevel()
	);

	public static ProcessingPipeline<ConsumerRecord<String, String>, CommandResponseHandler> commandResponseProcessing() {
		return config -> system -> commandResponseHandler -> callContextProvider -> metricsService -> source -> sink -> () -> run(
				config,
				system,
				commandResponseHandler,
				callContextProvider,
				metricsService,
				source,
				sink
		);
	}

	private static ActorMaterializer run(
			ServiceConfig config,
			ActorSystem system,
			CommandResponseHandler commandResponseHandler,
			ActorRef callContextProvider,
			ActorRef metricsService,
			Source<ConsumerRecord<String, String>, NotUsed> source,
			Sink<ProducerRecord<String, String>, NotUsed> sink) {

		final Function<Throwable, Directive> decider = t -> {
			system.log().error(t, "Error in some stage; restarting stream ...");
			return Supervision.restart();
		};

		final Source<ConsumerRecord<String, String>, NotUsed> restartSource = RestartSource.withBackoff(
				Duration.of(1, ChronoUnit.SECONDS),
				Duration.of(10, ChronoUnit.SECONDS),
				0.2,
				() -> source
		);

		final ActorMaterializer materializer = ActorMaterializer.create(
				ActorMaterializerSettings.create(system).withSupervisionStrategy(decider),
				system);

		restartSource
				.log(">>>   ARI COMMAND", ConsumerRecord::value).withAttributes(LOG_LEVELS)
				.map(AriCommandResponseKafkaProcessor::unmarshallAriCommandEnvelope)
				.map(msgEnvelope -> {
					final String callContext = lookupCallContext(msgEnvelope.getResourceId(), callContextProvider);
					AriCommandResponseProcessing
							.registerCallContext(callContextProvider, callContext, msgEnvelope.getAriCommand())
							.getOrElseThrow(t -> t).run();
					return Tuple.of(
							msgEnvelope.getAriCommand(),
							new CallContextAndResourceId(callContext, msgEnvelope.getResourceId())
					);
				})
				.map(ariCommandAndContext -> ariCommandAndContext.map1(cmd -> toHttpRequest(cmd, config.getRestUri(), config.getRestUser(), config.getRestPassword())))
				.mapAsync(1, requestAndContext -> commandResponseHandler.apply(requestAndContext)
						.thenApply(response -> Tuple.of(response, requestAndContext._2)))
				.wireTap(Sink.foreach(gatherMetrics(metricsService, config.getStasisApp())))
				.mapAsync(1, rawHttpResponseAndContext -> toAriResponse(rawHttpResponseAndContext, materializer))
				.map(ariResponseAndContext -> envelopeAriResponse(ariResponseAndContext._1, ariResponseAndContext._2, config.getKafkaCommandsTopic()))
				.map(ariMessageEnvelopeAndContext -> ariMessageEnvelopeAndContext
						.map1(AriCommandResponseKafkaProcessor::marshallAriMessageEnvelope))
				.map(ariResponseStringAndContext -> new ProducerRecord<>(
						config.getKafkaEventsAndResponsesTopic(),
						ariResponseStringAndContext._2.getCallContext(),
						ariResponseStringAndContext._1)
				)
				.log(">>>   ARI RESPONSE", ProducerRecord::value).withAttributes(LOG_LEVELS)
				.toMat(sink, Keep.none())
				.run(materializer);

		return materializer;
	}

	private static Procedure<Tuple2<HttpResponse, CallContextAndResourceId>> gatherMetrics(ActorRef metricsService, String applicationName) {
		return rawHttpResponseAndContext ->
				metricsService.tell(
						new StopCallSetupTimer(
								rawHttpResponseAndContext._2.getCallContext(),
								applicationName
						),
						ActorRef.noSender()
				);
	}

	private static AriCommandEnvelope unmarshallAriCommandEnvelope(final ConsumerRecord<String, String> record) {
		return new Gson().fromJson(record.value(), AriCommandEnvelope.class);
	}

	private static String lookupCallContext(
			final String resourceId,
			final ActorRef callcontextProvider) {
		return PatternsAdapter.<CallContextProvided>ask(
				callcontextProvider,
				new ProvideCallContext(resourceId, ProviderPolicy.LOOKUP_ONLY),
				100
		)
				.await()
				.get()
				.callContext();
	}

	private static Tuple2<AriMessageEnvelope, CallContextAndResourceId> envelopeAriResponse(
			AriResponse ariResponse, CallContextAndResourceId callContextAndResourceId, String kafkaCommandsTopic) {
		String payload = io.vavr.control.Try.of(() -> new ObjectMapper().writeValueAsString(ariResponse))
				.getOrElseThrow(t -> new RuntimeException("Failed to serialize AriResponse", t));

		final AriMessageEnvelope envelope = new AriMessageEnvelope(
				AriMessageType.RESPONSE,
				kafkaCommandsTopic,
				payload,
				callContextAndResourceId.getResourceId()
		);

		return Tuple.of(envelope, callContextAndResourceId);
	}

	private static String marshallAriMessageEnvelope(AriMessageEnvelope messageEnvelope) {
		return io.vavr.control.Try.of(() -> new ObjectMapper().writeValueAsString(messageEnvelope))
				.getOrElseThrow(t -> new RuntimeException("Failed to serialize AriResponse", t));
	}

	private static CompletionStage<Tuple2<AriResponse, CallContextAndResourceId>> toAriResponse(
			Tuple2<HttpResponse, CallContextAndResourceId> responseWithContext,
			Materializer materializer) {

		final HttpResponse response = responseWithContext._1;

		final long contentLength = response
				.entity()
				.getContentLengthOption()
				.orElseThrow(() -> new RuntimeException("failed to get content length"));

		return response
				.entity()
				.toStrict(contentLength, materializer)
				.thenApply(strictText -> {
					AriResponse ariResponse = new AriResponse(
							response.status().intValue(),
							strictText.getData().decodeString(Charset.defaultCharset())
					);
					return responseWithContext.map1(httpResponse -> ariResponse);
				});
	}

	private static HttpRequest toHttpRequest(AriCommand ariCommand, String uri, String user, String password) {
		final String method = ariCommand.getMethod();
		return HttpMethods.lookup(method)
				.map(validHttpMethod -> HttpRequest
						.create()
						.withMethod(validHttpMethod)
						.addCredentials(HttpCredentials.createBasicHttpCredentials(user, password))
						.withUri(uri + ariCommand.getUrl())
						.withEntity(ContentTypes.APPLICATION_JSON, ariCommand.getBody().getBytes())
				)
				.orElseThrow(() -> new RuntimeException(String.format("Invalid http method: %s", method)));
	}
}

package io.retel.ariproxy.boundary.commandsandresponses;

import static io.retel.ariproxy.config.ServiceConfig.APPLICATION;
import static io.retel.ariproxy.config.ServiceConfig.KAFKA_COMMANDS_TOPIC;
import static io.retel.ariproxy.config.ServiceConfig.KAFKA_EVENTS_AND_RESPONSES_TOPIC;
import static io.retel.ariproxy.config.ServiceConfig.REST_PASSWORD;
import static io.retel.ariproxy.config.ServiceConfig.REST_URI;
import static io.retel.ariproxy.config.ServiceConfig.REST_USER;

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
import akka.kafka.ConsumerFailed;
import akka.stream.ActorMaterializer;
import akka.stream.ActorMaterializerSettings;
import akka.stream.Attributes;
import akka.stream.Materializer;
import akka.stream.Supervision;
import akka.stream.Supervision.Directive;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.typesafe.config.Config;
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
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public class AriCommandResponseKafkaProcessor {

	private static final Attributes LOG_LEVELS = Attributes.createLogLevels(
			Logging.InfoLevel(),
			Logging.InfoLevel(),
			Logging.ErrorLevel()
	);

	private static final Config serviceConfig = ServiceConfig.INSTANCE.get();
	private static final String APPLICATION_NAME = serviceConfig.getString(APPLICATION);
	private static final String ARI_RESPONSES_TOPIC = serviceConfig.getString(KAFKA_EVENTS_AND_RESPONSES_TOPIC);
	private static final String ASTERISK_REST_URI = serviceConfig.getString(REST_URI);
	private static final String ASTERISK_USER = serviceConfig.getString(REST_USER);
	private static final String ASTERISK_PASSWORD = serviceConfig.getString(REST_PASSWORD);
	private static final String ARI_COMMANDS_TOPIC = serviceConfig.getString(KAFKA_COMMANDS_TOPIC);

	public static ProcessingPipeline<ConsumerRecord<String, String>, CommandResponseHandler> commandResponseProcessing() {
		return system -> commandResponseHandler -> callContextProvider -> metricsService -> source -> sink -> () -> run(
				system,
				commandResponseHandler,
				callContextProvider,
				metricsService,
				source,
				sink
		);
	}

	private static ActorMaterializer run(
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

		final ActorMaterializer materializer = ActorMaterializer.create(
				ActorMaterializerSettings.create(system).withSupervisionStrategy(decider),
				system);

		source
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
				.map(ariCommandAndContext -> ariCommandAndContext.map1(AriCommandResponseKafkaProcessor::toHttpRequest))
				.mapAsync(1, requestAndContext -> commandResponseHandler.apply(requestAndContext)
						.thenApply(response -> Tuple.of(response, requestAndContext._2)))
				.wireTap(Sink.foreach(gatherMetrics(metricsService)))
				.mapAsync(1, rawHttpResponseAndContext -> toAriResponse(rawHttpResponseAndContext, materializer))
				.map(ariResponseAndContext -> envelopeAriResponse(ariResponseAndContext._1, ariResponseAndContext._2))
				.map(ariMessageEnvelopeAndContext -> ariMessageEnvelopeAndContext
						.map1(AriCommandResponseKafkaProcessor::marshallAriMessageEnvelope))
				.map(ariResponseStringAndContext -> new ProducerRecord<>(
						ARI_RESPONSES_TOPIC,
						ariResponseStringAndContext._2.getCallContext(),
						ariResponseStringAndContext._1)
				)
				.log(">>>   ARI RESPONSE", ProducerRecord::value).withAttributes(LOG_LEVELS)
				.toMat(sink, Keep.none())
				.run(materializer);

		return materializer;
	}

	private static Procedure<Tuple2<HttpResponse, CallContextAndResourceId>> gatherMetrics(ActorRef metricsService) {
		return rawHttpResponseAndContext ->
				metricsService.tell(
						new StopCallSetupTimer(
								rawHttpResponseAndContext._2.getCallContext(),
								APPLICATION_NAME
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
			AriResponse ariResponse, CallContextAndResourceId callContextAndResourceId) {
		String payload = io.vavr.control.Try.of(() -> new ObjectMapper().writeValueAsString(ariResponse))
				.getOrElseThrow(t -> new RuntimeException("Failed to serialize AriResponse", t));

		final AriMessageEnvelope envelope = new AriMessageEnvelope(
				AriMessageType.RESPONSE,
				ARI_COMMANDS_TOPIC,
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

	private static HttpRequest toHttpRequest(AriCommand ariCommand) {
		final String method = ariCommand.getMethod();
		return HttpMethods.lookup(method)
				.map(validHttpMethod -> HttpRequest
						.create()
						.withMethod(validHttpMethod)
						.addCredentials(HttpCredentials.createBasicHttpCredentials(ASTERISK_USER, ASTERISK_PASSWORD))
						.withUri(ASTERISK_REST_URI + ariCommand.getUrl())
						.withEntity(ContentTypes.APPLICATION_JSON, ariCommand.getBody().getBytes())
				)
				.orElseThrow(() -> new RuntimeException(String.format("Invalid http method: %s", method)));
	}
}

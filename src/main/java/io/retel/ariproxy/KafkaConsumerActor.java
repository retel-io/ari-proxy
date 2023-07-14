package io.retel.ariproxy;

import static io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder.KEY;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import akka.pattern.StatusReply;
import com.typesafe.config.Config;
import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KafkaConsumerActor extends AbstractBehavior<Object> {

  private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerActor.class);

  private final Config kafkaConfig;

  private final ParallelStreamProcessor<String, String> streamProcessor;

  private KafkaConsumerActor(
      final ActorContext<Object> context,
      final Config kafkaConfig,
      final ActorRef<AriCommandMessage> commandResponseProcessor) {
    super(context);

    this.kafkaConfig = kafkaConfig;
    final ParallelConsumerOptions<String, String> options =
        ParallelConsumerOptions.<String, String>builder()
            .ordering(KEY)
            .maxConcurrency(this.kafkaConfig.getInt("parallel-consumer-max-concurrency"))
            .consumer(createConsumer())
            .build();

    streamProcessor = ParallelStreamProcessor.createEosStreamProcessor(options);

    LOGGER.debug(
        "Starting Kafka Consumer and subscribing to topic {}.",
        this.kafkaConfig.getString("commands-topic"));

    streamProcessor.subscribe(Set.of(this.kafkaConfig.getString("commands-topic")));

    streamProcessor.poll(
        recordContexts -> {
          final ConsumerRecord<String, String> singleConsumerRecord =
              recordContexts.getSingleConsumerRecord();

          AskPattern.<AriCommandMessage, StatusReply<Void>>ask(
                  commandResponseProcessor,
                  replyTo -> new AriCommandMessage(singleConsumerRecord.value(), replyTo),
                  Duration.ofSeconds(1),
                  context.getSystem().scheduler())
              .thenAccept(
                  (StatusReply<Void> reply) -> {
                    if (reply.isError()) {
                      LOGGER.error(
                          "Error occurred during message processing. Committing offset anyway.",
                          reply.getError());
                    }
                  });
        });
  }

  public static Behavior<Object> create(
      final Config config, final ActorRef<AriCommandMessage> commandResponseProcessor) {
    return Behaviors.setup(
        context -> new KafkaConsumerActor(context, config, commandResponseProcessor));
  }

  @Override
  public Receive<Object> createReceive() {
    return ReceiveBuilder.create()
        .onSignal(
            PostStop.class,
            param -> {
              LOGGER.info(
                  "Received PostStop signal. Draining Kafka consumer. Messages left to process: {}",
                  streamProcessor.workRemaining());
              streamProcessor.closeDrainFirst();

              return Behaviors.same();
            })
        .build();
  }

  private Consumer<String, String> createConsumer() {

    Map<String, Object> config =
        Map.of(
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            "false",
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafkaConfig.getString("bootstrap-servers"),
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.GROUP_ID_CONFIG,
            kafkaConfig.getString("consumer-group"));

    return new KafkaConsumer<>(config);
  }
}

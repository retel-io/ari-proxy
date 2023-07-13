package io.retel.ariproxy;

import static io.confluent.parallelconsumer.ParallelConsumerOptions.ProcessingOrder.KEY;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.*;
import akka.pattern.StatusReply;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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

  private static final int MAX_PARALLEL_REQUESTS_PROCESSING = 10;
  private static final String INBOX_TOPIC =
      ConfigFactory.load().getConfig("kafka").getString("inbox-topic");
  private final ParallelStreamProcessor<String, String> streamProcessor;

  private KafkaConsumerActor(
      final ActorContext<Object> context,
      final ActorRef<AriCommandMessage> commandResponseProcessor) {
    super(context);

    final ParallelConsumerOptions<String, String> options =
        ParallelConsumerOptions.<String, String>builder()
            .ordering(KEY)
            .maxConcurrency(MAX_PARALLEL_REQUESTS_PROCESSING)
            .consumer(createConsumer())
            .build();

    streamProcessor = ParallelStreamProcessor.createEosStreamProcessor(options);

    LOGGER.debug("Starting Kafka Consumer and subscribing to topic {}.", INBOX_TOPIC);

    streamProcessor.subscribe(Set.of(INBOX_TOPIC));

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
                      LOGGER.error("something went wrong", reply.getError());
                    }
                  });
        });
  }

  public static Behavior<Object> create(
      final ActorRef<AriCommandMessage> commandResponseProcessor) {
    return Behaviors.setup(context -> new KafkaConsumerActor(context, commandResponseProcessor));
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

  private static Consumer<String, String> createConsumer() {
    final Config kafkaConfig = ConfigFactory.load().getConfig("kafka");
    final String bootstrapServers = kafkaConfig.getString("bootstrap-servers");
    final String groupId = kafkaConfig.getString("consumer-group");

    Map<String, Object> config =
        Map.of(
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            "false",
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrapServers,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.GROUP_ID_CONFIG,
            groupId);

    return new KafkaConsumer<>(config);
  }
}

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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
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
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getString("bootstrap-servers"));
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getString("consumer-group"));

    if ("SASL_SSL".equals(kafkaConfig.getString("security.protocol"))) {
      config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SASL_SSL.name());
      config.put(SaslConfigs.SASL_MECHANISM, ScramMechanism.SCRAM_SHA_256.mechanismName());
      config.put(
          SaslConfigs.SASL_JAAS_CONFIG,
          "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";"
              .formatted(
                  kafkaConfig.getString("security.user"),
                  kafkaConfig.getString("security.password")));
    }

    return new KafkaConsumer<>(config);
  }
}

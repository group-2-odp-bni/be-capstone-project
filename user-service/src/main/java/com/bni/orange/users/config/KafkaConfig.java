package com.bni.orange.users.config;

import com.bni.orange.users.config.properties.KafkaProducerProperties;
import com.bni.orange.users.config.properties.KafkaTopicProperties;
import com.bni.orange.users.config.properties.RedisPrefixProperties;
import com.bni.orange.users.consumer.KafkaConsumerErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({KafkaProducerProperties.class, KafkaTopicProperties.class, RedisPrefixProperties.class})
public class KafkaConfig {

    private final KafkaProducerProperties kafkaProps;
    private final KafkaTopicProperties topicProps;
    private final KafkaConsumerErrorHandler errorHandler;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    public Map<String, Object> producerConfigs() {
        var props = new HashMap<String, Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProps.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, kafkaProps.keySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, kafkaProps.valueSerializer());
        props.put(ProducerConfig.ACKS_CONFIG, kafkaProps.acks());
        props.put(ProducerConfig.RETRIES_CONFIG, kafkaProps.retries());
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, (int) kafkaProps.backoff().toMillis());
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, kafkaProps.reliability().maxInFlightRequestsPerConnection());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, kafkaProps.idempotence());
        props.put(ProducerConfig.LINGER_MS_CONFIG, kafkaProps.batching().lingerMs());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, kafkaProps.batching().batchSize());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, kafkaProps.compressionType());
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) kafkaProps.reliability().requestTimeout().toMillis());
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) kafkaProps.reliability().deliveryTimeout().toMillis());

        return props;
    }

    @Bean
    public ProducerFactory<String, byte[]> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, byte[]> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean(name = "kafkaVirtualThreadExecutor")
    public Executor kafkaVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public List<NewTopic> kafkaTopics() {
        if (Objects.isNull(topicProps.definitions())) {
            return List.of();
        }

        return topicProps.definitions().values().stream()
            .map(config -> {
                var builder = TopicBuilder.name(config.name())
                    .partitions(config.partitions())
                    .replicas(config.replicas());

                if (Boolean.TRUE.equals(config.compact())) {
                    builder.compact();
                }

                log.info("Registering Kafka topic: {} [Partitions: {}, Replicas: {}, Compact: {}]",
                    config.name(), config.partitions(), config.replicas(), config.compact());

                return builder.build();
            })
            .collect(Collectors.toList());
    }

    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setPollTimeout(3000);

        // Set error handler for consumer
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}

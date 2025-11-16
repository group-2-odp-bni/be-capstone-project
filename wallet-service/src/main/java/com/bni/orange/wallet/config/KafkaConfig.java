package com.bni.orange.wallet.config;

import com.bni.orange.wallet.config.properties.KafkaConsumerProperties;
import com.bni.orange.wallet.config.properties.KafkaProducerProperties;
import com.bni.orange.wallet.config.properties.KafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
    KafkaProducerProperties.class,
    KafkaConsumerProperties.class,
    KafkaTopicProperties.class
})
public class KafkaConfig {

    private final KafkaProducerProperties producerProps;
    private final KafkaConsumerProperties consumerProps;

    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, producerProps.bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, producerProps.keySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, producerProps.valueSerializer());
        props.put(ProducerConfig.ACKS_CONFIG, producerProps.acks());
        props.put(ProducerConfig.RETRIES_CONFIG, producerProps.retries());
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, (int) producerProps.retryBackoffMs().toMillis());
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, producerProps.reliability().maxInFlightRequestsPerConnection());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, producerProps.enableIdempotence());
        props.put(ProducerConfig.LINGER_MS_CONFIG, producerProps.batching().lingerMs());
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, producerProps.batching().batchSize());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producerProps.compressionType());
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) producerProps.reliability().requestTimeout().toMillis());
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (int) producerProps.reliability().deliveryTimeout().toMillis());

        log.info("Kafka producer configured: bootstrap={}, acks={}, idempotence={}",
            producerProps.bootstrapServers(), producerProps.acks(), producerProps.enableIdempotence());
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
    public Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, consumerProps.bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProps.groupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, consumerProps.keyDeserializer());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, consumerProps.valueDeserializer());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerProps.autoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, consumerProps.enableAutoCommit());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerProps.maxPollRecords());
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, consumerProps.maxPollIntervalMs());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, consumerProps.sessionTimeoutMs());
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, consumerProps.heartbeatIntervalMs());

        log.info("Kafka consumer configured: bootstrap={}, groupId={}, autoOffsetReset={}",
            consumerProps.bootstrapServers(), consumerProps.groupId(), consumerProps.autoOffsetReset());
        return props;
    }

    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(consumerProps.concurrency());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.getContainerProperties().setObservationEnabled(true);

        log.info("Kafka consumer factory configured with BATCH ack mode and concurrency {}",
            consumerProps.concurrency());
        return factory;
    }
}

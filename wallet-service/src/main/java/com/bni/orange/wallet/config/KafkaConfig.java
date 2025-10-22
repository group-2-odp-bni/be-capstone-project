package com.bni.orange.wallet.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.Map;

@Configuration
public class KafkaConfig {

  @Bean
  public ProducerFactory<String, byte[]> producerFactory(KafkaProperties props) {
    Map<String, Object> cfg = props.buildProducerProperties();
    cfg.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    cfg.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    return new DefaultKafkaProducerFactory<>(cfg);
  }

  @Bean
  public KafkaTemplate<String, byte[]> kafkaTemplate(ProducerFactory<String, byte[]> pf) {
    return new KafkaTemplate<>(pf);
  }

  @Bean
  public ConsumerFactory<String, byte[]> consumerFactory(KafkaProperties props) {
    Map<String, Object> cfg = props.buildConsumerProperties();
    cfg.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    cfg.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(cfg);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory(
      ConsumerFactory<String, byte[]> consumerFactory
  ) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
    factory.setConsumerFactory(consumerFactory);
    ContainerProperties cp = factory.getContainerProperties();
    cp.setObservationEnabled(true);
    return factory;
  }
}

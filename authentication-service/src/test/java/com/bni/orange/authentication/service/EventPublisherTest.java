package com.bni.orange.authentication.service;

import com.google.protobuf.Message;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventPublisherTest {

    @InjectMocks
    private EventPublisher eventPublisher;

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Mock
    private Executor kafkaVirtualThreadExecutor;

    @Mock
    private Message mockEvent;

    @Mock
    private SendResult<String, byte[]> sendResult;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(kafkaVirtualThreadExecutor).execute(any(Runnable.class));

        when(mockEvent.toByteArray()).thenReturn(new byte[]{1, 2, 3});

        var topicPartition = new TopicPartition("default-topic", 1);
        var recordMetadata = new RecordMetadata(topicPartition, 0, 0, 0, 0, 0);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
    }

    @Test
    @DisplayName("publish(topic, key, event) should send message successfully")
    void publishWithKey_shouldSendMessage() {
        var topic = "test-topic";
        var key = "test-key";
        var future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(topic, key, mockEvent.toByteArray())).thenReturn(future);

        eventPublisher.publish(topic, key, mockEvent);

        verify(kafkaTemplate).send(topic, key, mockEvent.toByteArray());
    }

    @Test
    @DisplayName("publish(topic, event) should call publish with null key")
    void publishWithNullKey_shouldCallCorrectMethod() {
        var topic = "test-topic";
        var spiedPublisher = spy(eventPublisher);

        doNothing().when(spiedPublisher).publish(topic, null, mockEvent);
        spiedPublisher.publish(topic, mockEvent);

        verify(spiedPublisher).publish(topic, null, mockEvent);
    }

    @Test
    @DisplayName("publishAsync should return a future that completes with SendResult")
    void publishAsync_onSuccess_shouldCompleteFuture() throws Exception {
        var topic = "test-topic";
        var key = "test-key";
        var future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(topic, key, mockEvent.toByteArray())).thenReturn(future);

        var resultFuture = eventPublisher.publishAsync(topic, key, mockEvent);

        assertEquals(sendResult, resultFuture.get());
    }

    @Test
    @DisplayName("publishAsync should complete exceptionally on failure")
    void publishAsync_onFailure_shouldCompleteExceptionally() {
        var topic = "test-topic";
        var key = "test-key";
        var exception = new RuntimeException("Kafka is down");

        when(kafkaTemplate.send(topic, key, mockEvent.toByteArray()))
            .thenThrow(exception);

        var resultFuture = eventPublisher.publishAsync(topic, key, mockEvent);

        assertTrue(resultFuture.isCompletedExceptionally());
        assertThrows(Exception.class, resultFuture::get);
    }

    @Test
    @DisplayName("publishWithCallback should call onSuccess on successful publish")
    void publishWithCallback_onSuccess_shouldCallSuccessCallback() {
        var topic = "test-topic";
        var key = "test-key";
        var future = CompletableFuture.completedFuture(sendResult);

        Consumer<SendResult<String, byte[]>> onSuccess = mock(Consumer.class);
        Consumer<Throwable> onError = mock(Consumer.class);

        when(kafkaTemplate.send(topic, key, mockEvent.toByteArray())).thenReturn(future);

        eventPublisher.publishWithCallback(topic, key, mockEvent, onSuccess, onError);

        verify(onSuccess).accept(sendResult);
        verify(onError, never()).accept(any());
    }

    @Test
    @DisplayName("publishWithCallback should call onError on failed publish")
    void publishWithCallback_onFailure_shouldCallErrorCallback() {
        var topic = "test-topic";
        var key = "test-key";
        var exception = new RuntimeException("Kafka error");

        when(kafkaTemplate.send(topic, key, mockEvent.toByteArray()))
            .thenThrow(exception);

        Consumer<SendResult<String, byte[]>> onSuccess = mock(Consumer.class);
        Consumer<Throwable> onError = mock(Consumer.class);

        eventPublisher.publishWithCallback(topic, key, mockEvent, onSuccess, onError);

        verify(onSuccess, never()).accept(any());
        verify(onError).accept(any(RuntimeException.class));
    }

    @Test
    @DisplayName("publishBatch should call publishAsync for each event")
    void publishBatch_shouldCallPublishAsyncForEachEvent() throws Exception {
        var topic = "batch-topic";
        var event1 = mock(Message.class);
        var event2 = mock(Message.class);
        var events = Map.of("key1", event1, "key2", event2);

        when(event1.toByteArray()).thenReturn(new byte[]{1});
        when(event2.toByteArray()).thenReturn(new byte[]{2});

        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

        var batchFuture = eventPublisher.publishBatch(topic, events);
        batchFuture.get(); // Wait for completion

        verify(kafkaTemplate).send(topic, "key1", event1.toByteArray());
        verify(kafkaTemplate).send(topic, "key2", event2.toByteArray());
    }

    @Test
    @DisplayName("publish should log error when future completes exceptionally")
    void publish_onFailure_shouldLogException() {
        var topic = "fail-topic";
        var key = "fail-key";
        var exception = new RuntimeException("Async Error");

        when(kafkaTemplate.send(topic, key, mockEvent.toByteArray()))
            .thenThrow(exception);

        assertDoesNotThrow(() -> eventPublisher.publish(topic, key, mockEvent));

        verify(kafkaTemplate).send(topic, key, mockEvent.toByteArray());
    }
}
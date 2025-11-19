package com.bni.orange.notification.consumer;

import com.bni.orange.authentication.proto.OtpNotificationEvent;
import com.bni.orange.notification.model.response.WahaMessageResponse;
import com.bni.orange.notification.service.WhatsAppService;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpKafkaConsumerTest {

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private DlqProducer dlqProducer;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private OtpKafkaConsumer otpKafkaConsumer;

    private String testUserId;
    private String testPhoneNumber;
    private String testOtpCode;
    private String testTopic;

    @BeforeEach
    void setUp() {
        testUserId = "user-123";
        testPhoneNumber = "+628123456789";
        testOtpCode = "123456";
        testTopic = "otp-whatsapp";
    }

    @Test
    @DisplayName("Should successfully process OTP notification event")
    void listen_Success() {
        var event = OtpNotificationEvent.newBuilder()
            .setUserId(testUserId)
            .setPhoneNumber(testPhoneNumber)
            .setOtpCode(testOtpCode)
            .build();

        var record = new ConsumerRecord<>(
            testTopic,
            0,
            100L,
            testUserId,
            event.toByteArray()
        );

        var mockResponse = new WahaMessageResponse(
            "msg-123",
            System.currentTimeMillis(),
            "whatsapp-sender",
            testPhoneNumber,
            "OTP message"
        );
        when(whatsAppService.sendOtp(any(OtpNotificationEvent.class)))
            .thenReturn(Mono.just(mockResponse));

        otpKafkaConsumer.listen(record, acknowledgment);

        verify(whatsAppService, times(1)).sendOtp(any(OtpNotificationEvent.class));
        verify(acknowledgment, times(1)).acknowledge();
        verify(dlqProducer, never()).send(any(), any());
    }

    @Test
    @DisplayName("Should send to DLQ when protobuf deserialization fails")
    void listen_InvalidProtobuf() {
        var invalidPayload = "invalid-protobuf-data".getBytes();
        var record = new ConsumerRecord<>(
            testTopic,
            0,
            100L,
            testUserId,
            invalidPayload
        );

        otpKafkaConsumer.listen(record, acknowledgment);

        verify(whatsAppService, never()).sendOtp(any());
        verify(dlqProducer, times(1)).send(eq(record), any(InvalidProtocolBufferException.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should send to DLQ when WhatsApp service fails")
    void listen_WhatsAppServiceFailure() {
        var event = OtpNotificationEvent.newBuilder()
            .setUserId(testUserId)
            .setPhoneNumber(testPhoneNumber)
            .setOtpCode(testOtpCode)
            .build();

        var record = new ConsumerRecord<>(
            testTopic,
            0,
            100L,
            testUserId,
            event.toByteArray()
        );

        when(whatsAppService.sendOtp(any(OtpNotificationEvent.class)))
            .thenReturn(Mono.error(new RuntimeException("WhatsApp API error")));

        otpKafkaConsumer.listen(record, acknowledgment);

        verify(whatsAppService, times(1)).sendOtp(any(OtpNotificationEvent.class));
        verify(dlqProducer, times(1)).send(eq(record), any(RuntimeException.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should send to DLQ when WhatsApp service times out")
    void listen_WhatsAppServiceTimeout() {
        var event = OtpNotificationEvent.newBuilder()
            .setUserId(testUserId)
            .setPhoneNumber(testPhoneNumber)
            .setOtpCode(testOtpCode)
            .build();

        var record = new ConsumerRecord<>(
            testTopic,
            0,
            100L,
            testUserId,
            event.toByteArray()
        );

        when(whatsAppService.sendOtp(any(OtpNotificationEvent.class)))
            .thenReturn(Mono.error(new java.util.concurrent.TimeoutException("Request timeout")));

        otpKafkaConsumer.listen(record, acknowledgment);

        verify(whatsAppService, times(1)).sendOtp(any(OtpNotificationEvent.class));
        verify(dlqProducer, times(1)).send(eq(record), any(Exception.class));
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("Should handle empty OTP code gracefully")
    void listen_EmptyOtpCode() {
        var event = OtpNotificationEvent.newBuilder()
            .setUserId(testUserId)
            .setPhoneNumber(testPhoneNumber)
            .setOtpCode("")
            .build();

        var record = new ConsumerRecord<>(
            testTopic,
            0,
            100L,
            testUserId,
            event.toByteArray()
        );

        var mockResponse = new WahaMessageResponse(
            "msg-123",
            System.currentTimeMillis(),
            "whatsapp-sender",
            testPhoneNumber,
            "OTP message"
        );
        when(whatsAppService.sendOtp(any(OtpNotificationEvent.class)))
            .thenReturn(Mono.just(mockResponse));

        otpKafkaConsumer.listen(record, acknowledgment);

        verify(whatsAppService, times(1)).sendOtp(any(OtpNotificationEvent.class));
        verify(acknowledgment, times(1)).acknowledge();
        verify(dlqProducer, never()).send(any(), any());
    }

    @Test
    @DisplayName("Should handle null phone number in event")
    void listen_NullPhoneNumber() {
        var event = OtpNotificationEvent.newBuilder()
            .setUserId(testUserId)
            .setOtpCode(testOtpCode)
            .build();

        var record = new ConsumerRecord<>(
            testTopic,
            0,
            100L,
            testUserId,
            event.toByteArray()
        );

        var mockResponse = new WahaMessageResponse(
            "msg-123",
            System.currentTimeMillis(),
            "whatsapp-sender",
            testPhoneNumber,
            "OTP message"
        );
        when(whatsAppService.sendOtp(any(OtpNotificationEvent.class)))
            .thenReturn(Mono.just(mockResponse));

        otpKafkaConsumer.listen(record, acknowledgment);

        verify(whatsAppService, times(1)).sendOtp(any(OtpNotificationEvent.class));
        verify(acknowledgment, times(1)).acknowledge();
    }
}

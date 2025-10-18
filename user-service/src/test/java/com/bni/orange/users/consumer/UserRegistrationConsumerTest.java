package com.bni.orange.users.consumer;

import com.bni.orange.users.model.entity.UserProfile;
import com.bni.orange.users.proto.UserRegisteredEvent;
import com.bni.orange.users.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegistrationConsumer Tests")
class UserRegistrationConsumerTest {

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private UserRegistrationConsumer consumer;

    private UUID testUserId;
    private String testPhoneNumber;
    private String testName;
    private long testRegisteredAt;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testPhoneNumber = "+628123456789";
        testName = "Test User";
        testRegisteredAt = Instant.now().getEpochSecond();
    }

    @Test
    @DisplayName("Should create user profile successfully when event is valid")
    void handleUserRegistered_Success() throws Exception {
        // Given
        var event = UserRegisteredEvent.newBuilder()
            .setUserId(testUserId.toString())
            .setPhoneNumber(testPhoneNumber)
            .setName(testName)
            .setRegisteredAt(testRegisteredAt)
            .build();

        when(profileRepository.existsById(testUserId)).thenReturn(false);
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        consumer.handleUserRegistered(
            event.toByteArray(),
            testUserId.toString(),
            0,
            100L,
            acknowledgment
        );

        // Then
        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        verify(acknowledgment).acknowledge();

        UserProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.getId()).isEqualTo(testUserId);
        assertThat(savedProfile.getName()).isEqualTo(testName);
        assertThat(savedProfile.getPhoneNumber()).isEqualTo(testPhoneNumber);
        assertThat(savedProfile.getPhoneVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should skip creation when profile already exists (idempotency)")
    void handleUserRegistered_AlreadyExists() throws Exception {
        // Given
        var event = UserRegisteredEvent.newBuilder()
            .setUserId(testUserId.toString())
            .setPhoneNumber(testPhoneNumber)
            .setName(testName)
            .setRegisteredAt(testRegisteredAt)
            .build();

        when(profileRepository.existsById(testUserId)).thenReturn(true);

        // When
        consumer.handleUserRegistered(
            event.toByteArray(),
            testUserId.toString(),
            0,
            100L,
            acknowledgment
        );

        // Then
        verify(profileRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should acknowledge and skip when userId is invalid")
    void handleUserRegistered_InvalidUserId() throws Exception {
        // Given
        var event = UserRegisteredEvent.newBuilder()
            .setUserId("invalid-uuid")
            .setPhoneNumber(testPhoneNumber)
            .setName(testName)
            .setRegisteredAt(testRegisteredAt)
            .build();

        // When
        consumer.handleUserRegistered(
            event.toByteArray(),
            "invalid-uuid",
            0,
            100L,
            acknowledgment
        );

        // Then
        verify(profileRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should throw exception when protobuf is invalid")
    void handleUserRegistered_InvalidProtobuf() {
        // Given
        byte[] invalidPayload = "not-a-protobuf".getBytes();

        // When / Then
        assertThatThrownBy(() ->
            consumer.handleUserRegistered(
                invalidPayload,
                testUserId.toString(),
                0,
                100L,
                acknowledgment
            )
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Invalid protobuf message");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should throw exception when database operation fails")
    void handleUserRegistered_DatabaseError() throws Exception {
        // Given
        var event = UserRegisteredEvent.newBuilder()
            .setUserId(testUserId.toString())
            .setPhoneNumber(testPhoneNumber)
            .setName(testName)
            .setRegisteredAt(testRegisteredAt)
            .build();

        when(profileRepository.existsById(testUserId)).thenReturn(false);
        when(profileRepository.save(any(UserProfile.class)))
            .thenThrow(new RuntimeException("Database connection error"));

        // When / Then
        assertThatThrownBy(() ->
            consumer.handleUserRegistered(
                event.toByteArray(),
                testUserId.toString(),
                0,
                100L,
                acknowledgment
            )
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Failed to process user registration event");

        verify(acknowledgment, never()).acknowledge();
    }
}

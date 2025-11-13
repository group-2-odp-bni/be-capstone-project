package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.AuthServiceClient;
import com.bni.orange.transaction.client.UserServiceClient;
import com.bni.orange.transaction.client.WalletServiceClient;
import com.bni.orange.transaction.config.properties.KafkaTopicProperties;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.event.EventPublisher;
import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import com.bni.orange.transaction.model.enums.TransactionType;
import com.bni.orange.transaction.model.enums.WalletPermission;
import com.bni.orange.transaction.model.enums.WalletStatus;
import com.bni.orange.transaction.model.enums.WalletRole;
import com.bni.orange.transaction.model.enums.WalletType;
import com.bni.orange.transaction.model.request.RecipientLookupRequest;
import com.bni.orange.transaction.model.request.TransferConfirmRequest;
import com.bni.orange.transaction.model.request.TransferInitiateRequest;
import com.bni.orange.transaction.model.response.BalanceResponse;
import com.bni.orange.transaction.model.response.UserProfileResponse;
import com.bni.orange.transaction.model.response.WalletAccessValidation;
import com.bni.orange.transaction.model.response.WalletResolutionResponse;
import com.bni.orange.transaction.repository.TransactionLedgerRepository;
import com.bni.orange.transaction.repository.TransactionRepository;
import com.bni.orange.transaction.utils.TransactionRefGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionLedgerRepository ledgerRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private TransactionRefGenerator refGenerator;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private KafkaTopicProperties topicProperties;

    @Mock
    private QuickTransferService quickTransferService;

    @InjectMocks
    private TransferService transferService;

    private UUID testUserId;
    private UUID testReceiverUserId;
    private UUID testWalletId;
    private String testPhoneNumber;
    private String testAccessToken;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testReceiverUserId = UUID.randomUUID();
        testWalletId = UUID.randomUUID();
        testPhoneNumber = "+628123456789";
        testAccessToken = "test-token";

        ReflectionTestUtils.setField(transferService, "transferFee", BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should successfully lookup recipient by phone number")
    void lookupRecipient_Success() {
        // Given
        var request = new RecipientLookupRequest(testPhoneNumber);
        var userResponse = UserProfileResponse.builder()
            .id(testReceiverUserId)
            .name("John Doe")
            .phoneNumber(testPhoneNumber)
            .build();

        var walletResponse = new WalletResolutionResponse(
            UUID.randomUUID(),
            "Personal Wallet",
            WalletType.PERSONAL,
            testReceiverUserId,
            "IDR"
        );

        when(userServiceClient.findByPhoneNumber(anyString(), eq(testAccessToken)))
            .thenReturn(Mono.just(userResponse));
        when(walletServiceClient.resolveRecipientWallet(anyString(), eq("PHONE"), eq("IDR")))
            .thenReturn(Mono.just(walletResponse));

        // When
        var result = transferService.lookupRecipient(request, testUserId, testAccessToken);

        // Then
        assertNotNull(result);
        assertEquals(testReceiverUserId, result.userId());
        assertEquals("John Doe", result.name());
        assertTrue(result.hasWallet());
        verify(userServiceClient, times(1)).findByPhoneNumber(anyString(), eq(testAccessToken));
    }

    @Test
    @DisplayName("Should throw exception when recipient not found")
    void lookupRecipient_UserNotFound() {
        // Given
        var request = new RecipientLookupRequest(testPhoneNumber);
        when(userServiceClient.findByPhoneNumber(anyString(), eq(testAccessToken)))
            .thenReturn(Mono.empty());

        // When & Then
        var exception = assertThrows(BusinessException.class, () ->
            transferService.lookupRecipient(request, testUserId, testAccessToken)
        );
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw exception when trying to transfer to self")
    void lookupRecipient_SelfTransfer() {
        // Given
        var request = new RecipientLookupRequest(testPhoneNumber);
        var userResponse = UserProfileResponse.builder()
            .id(testUserId) // Same as current user
            .name("Self")
            .phoneNumber(testPhoneNumber)
            .build();

        when(userServiceClient.findByPhoneNumber(anyString(), eq(testAccessToken)))
            .thenReturn(Mono.just(userResponse));

        // When & Then
        var exception = assertThrows(BusinessException.class, () ->
            transferService.lookupRecipient(request, testUserId, testAccessToken)
        );
        assertEquals(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should successfully initiate transfer")
    void initiateTransfer_Success() {
        // Given
        var request = new TransferInitiateRequest(
            testReceiverUserId,
            testWalletId,
            UUID.randomUUID(),
            BigDecimal.TEN,
            "IDR",
            "Test transfer"
        );
        var idempotencyKey = "test-key";

        var walletValidation = new WalletAccessValidation(
            true,
            WalletRole.OWNER,
            null,
            WalletStatus.ACTIVE,
            WalletType.PERSONAL,
            "My Wallet",
            null
        );

        var balanceResponse = new BalanceResponse(
            BigDecimal.valueOf(100),
            "IDR",
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(100)
        );

        var userResponse = UserProfileResponse.builder()
            .id(testReceiverUserId)
            .name("Receiver")
            .phoneNumber(testPhoneNumber)
            .build();

        var savedTransaction = Transaction.builder()
            .id(UUID.randomUUID())
            .transactionRef("TRX123")
            .status(TransactionStatus.PENDING)
            .build();

        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);
        when(walletServiceClient.validateAccess(any(UUID.class), any(UUID.class), any(WalletPermission.class)))
            .thenReturn(Mono.just(walletValidation));
        when(walletServiceClient.getBalance(any(UUID.class))).thenReturn(Mono.just(balanceResponse));
        when(userServiceClient.findByPhoneNumber(anyString(), eq(testAccessToken)))
            .thenReturn(Mono.just(userResponse));
        when(refGenerator.generate()).thenReturn("TRX123");
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toResponse(any())).thenReturn(null);

        // When
        transferService.initiateTransfer(request, testUserId, idempotencyKey, testAccessToken);

        // Then
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Should throw exception when amount is less than minimum")
    void initiateTransfer_InvalidAmount() {
        // Given
        var request = new TransferInitiateRequest(
            testReceiverUserId,
            testWalletId,
            UUID.randomUUID(),
            BigDecimal.valueOf(0.5),
            "IDR",
            "Test"
        );
        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);

        // When & Then
        var exception = assertThrows(BusinessException.class, () ->
            transferService.initiateTransfer(request, testUserId, "key", testAccessToken)
        );
        assertEquals(ErrorCode.INVALID_AMOUNT, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw exception when insufficient balance")
    void initiateTransfer_InsufficientBalance() {
        // Given
        var request = new TransferInitiateRequest(
            testReceiverUserId,
            testWalletId,
            UUID.randomUUID(),
            BigDecimal.valueOf(100),
            "IDR",
            "Test"
        );

        var walletValidation = new WalletAccessValidation(
            true,
            WalletRole.OWNER,
            null,
            WalletStatus.ACTIVE,
            WalletType.PERSONAL,
            "My Wallet",
            null
        );

        var balanceResponse = new BalanceResponse(
            BigDecimal.TEN, // Only 10, but need 100
            "IDR",
            BigDecimal.TEN,
            BigDecimal.TEN
        );

        when(transactionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(walletServiceClient.validateAccess(any(UUID.class), any(UUID.class), any(WalletPermission.class)))
            .thenReturn(Mono.just(walletValidation));
        when(walletServiceClient.getBalance(any(UUID.class))).thenReturn(Mono.just(balanceResponse));

        // When & Then
        var exception = assertThrows(BusinessException.class, () ->
            transferService.initiateTransfer(request, testUserId, "key", testAccessToken)
        );
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should successfully confirm transfer with valid PIN")
    void confirmTransfer_Success() {
        // Given
        var transactionId = UUID.randomUUID();
        var request = new TransferConfirmRequest("123456");

        var transaction = Transaction.builder()
            .id(transactionId)
            .transactionRef("TRX123")
            .type(TransactionType.TRANSFER_OUT)
            .senderUserId(testUserId)
            .senderWalletId(testWalletId)
            .receiverUserId(testReceiverUserId)
            .receiverWalletId(UUID.randomUUID())
            .receiverName("Receiver")
            .receiverPhone(testPhoneNumber)
            .status(TransactionStatus.PENDING)
            .amount(BigDecimal.TEN)
            .fee(BigDecimal.ZERO)
            .currency("IDR")
            .description("Transfer to Receiver")
            .notes("Test transfer")
            .build();
        transaction.calculateTotalAmount();

        var balanceResponse = new BalanceResponse(
            BigDecimal.valueOf(100),
            "IDR",
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(90)
        );

        var topicDef = new KafkaTopicProperties.TopicDefinition("transaction-completed", 3, 1, false);

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(authServiceClient.verifyPin(eq("123456"), eq(testAccessToken))).thenReturn(Mono.just(true));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        when(walletServiceClient.adjustBalance(any(), any(), anyString())).thenReturn(Mono.just(balanceResponse));
        when(ledgerRepository.save(any())).thenReturn(null);
        when(topicProperties.definitions()).thenReturn(java.util.Map.of("transaction-completed", topicDef));
        when(transactionMapper.toResponse(any())).thenReturn(null);

        // When
        transferService.confirmTransfer(transactionId, request, testUserId, testAccessToken);

        // Then
        verify(authServiceClient, times(1)).verifyPin(eq("123456"), eq(testAccessToken));
        verify(walletServiceClient, times(2)).adjustBalance(any(), any(), anyString());
        verify(eventPublisher, times(1)).publish(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw exception when PIN is invalid")
    void confirmTransfer_InvalidPin() {
        // Given
        var transactionId = UUID.randomUUID();
        var request = new TransferConfirmRequest("wrong-pin");

        var transaction = Transaction.builder()
            .id(transactionId)
            .senderUserId(testUserId)
            .status(TransactionStatus.PENDING)
            .build();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(authServiceClient.verifyPin(eq("wrong-pin"), eq(testAccessToken))).thenReturn(Mono.just(false));

        // When & Then
        var exception = assertThrows(BusinessException.class, () ->
            transferService.confirmTransfer(transactionId, request, testUserId, testAccessToken)
        );
        assertEquals(ErrorCode.INVALID_PIN, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw exception when transaction not found")
    void confirmTransfer_TransactionNotFound() {
        // Given
        var transactionId = UUID.randomUUID();
        var request = new TransferConfirmRequest("123456");

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.empty());

        // When & Then
        var exception = assertThrows(BusinessException.class, () ->
            transferService.confirmTransfer(transactionId, request, testUserId, testAccessToken)
        );
        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw exception when transaction belongs to different user")
    void confirmTransfer_UnauthorizedAccess() {
        // Given
        var transactionId = UUID.randomUUID();
        var request = new TransferConfirmRequest("123456");

        var transaction = Transaction.builder()
            .id(transactionId)
            .senderUserId(UUID.randomUUID()) // Different user
            .status(TransactionStatus.PENDING)
            .build();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        // When & Then
        var exception = assertThrows(BusinessException.class, () ->
            transferService.confirmTransfer(transactionId, request, testUserId, testAccessToken)
        );
        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }
}

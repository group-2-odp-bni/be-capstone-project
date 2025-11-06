package com.bni.orange.notification.service;

import com.bni.orange.users.proto.OtpEmailNotificationEvent;
import com.bni.orange.notification.config.properties.EmailConfigProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailConfigProperties emailConfig;

    public Mono<Boolean> sendOtp(OtpEmailNotificationEvent event) {
        log.info("Preparing to send OTP email to user {} at {}",
            event.getUserId(), maskEmail(event.getEmail()));

        return Mono.fromCallable(() -> {
                sendOtpEmail(event);
                return true;
            })
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(success -> log.info(
                "OTP email successfully sent to user {} at {}",
                event.getUserId(),
                maskEmail(event.getEmail())
            ))
            .doOnError(error -> log.error(
                "Failed to send OTP email to user {}: {}",
                event.getUserId(),
                error.getMessage()
            ))
            .timeout(Duration.ofSeconds(30));
    }

    private void sendOtpEmail(OtpEmailNotificationEvent event) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailConfig.fromAddress(), emailConfig.fromName());
            helper.setTo(event.getEmail());
            helper.setSubject("Your OTP Code - BNI Orange E-Wallet");
            helper.setText(buildHtmlContent(event.getOtpCode()), true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new EmailSendException("Failed to send OTP email", e);
        } catch (Exception e) {
            throw new EmailSendException("Unexpected error while sending email", e);
        }
    }

    private String buildHtmlContent(String otpCode) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .container {
                        background-color: #f8f9fa;
                        border-radius: 10px;
                        padding: 30px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .header {
                        text-align: center;
                        color: #ff6600;
                        margin-bottom: 30px;
                    }
                    .otp-box {
                        background-color: #fff;
                        border: 2px dashed #ff6600;
                        border-radius: 8px;
                        padding: 20px;
                        text-align: center;
                        margin: 20px 0;
                    }
                    .otp-code {
                        font-size: 32px;
                        font-weight: bold;
                        color: #ff6600;
                        letter-spacing: 5px;
                        margin: 10px 0;
                    }
                    .info {
                        background-color: #fff3cd;
                        border-left: 4px solid #ffc107;
                        padding: 15px;
                        margin: 20px 0;
                    }
                    .footer {
                        text-align: center;
                        color: #6c757d;
                        font-size: 12px;
                        margin-top: 30px;
                        padding-top: 20px;
                        border-top: 1px solid #dee2e6;
                    }
                    .warning {
                        color: #dc3545;
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔸 BNI Orange E-Wallet 🔸</h1>
                    </div>

                    <p>Hello! 👋</p>
                    <p>You requested an OTP code for your BNI Orange E-Wallet account verification.</p>

                    <div class="otp-box">
                        <p style="margin: 0; color: #6c757d;">Your OTP Code:</p>
                        <div class="otp-code">%s</div>
                    </div>

                    <div class="info">
                        <p style="margin: 5px 0;">⏰ <strong>Valid for 5 minutes</strong></p>
                        <p style="margin: 5px 0;">🔒 Keep this code secure and don't share it with anyone</p>
                    </div>

                    <p class="warning">⚠️ Important Security Notice:</p>
                    <ul>
                        <li>Never share this OTP with anyone, including BNI Orange staff</li>
                        <li>If you didn't request this code, please ignore this email and ensure your account is secure</li>
                    </ul>

                    <div class="footer">
                        <p>This is an automated message from BNI Orange E-Wallet.</p>
                        <p>Please do not reply to this email.</p>
                        <p>&copy; 2025 BNI Orange E-Wallet. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(otpCode);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return "***@" + domain;
        }

        int visibleChars = Math.min(2, localPart.length() / 3);
        String visible = localPart.substring(0, visibleChars);
        return visible + "***@" + domain;
    }

    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

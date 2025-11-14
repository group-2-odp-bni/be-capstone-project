package com.bni.orange.wallet.service.infra;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.delete-wallet-confirm-base-url}")
    private String deleteWalletConfirmBaseUrl;

    public void sendWalletDeleteConfirmationEmail(String toEmail, String walletName, String token) {
        String link = deleteWalletConfirmBaseUrl + "?token=" + token;

        String subject = "[Orange Wallet] Confirm shared wallet deletion";
        String html = """
            <p>Hi,</p>
            <p>You requested to delete the shared wallet: <b>%s</b>.</p>
            <p>Click the link below to confirm this action. This action will move remaining balance to your default receive wallet and close the shared wallet:</p>
            <p><a href="%s">Confirm wallet deletion</a></p>
            <p>If you did not request this, you can ignore this email.</p>
            """.formatted(walletName, link);

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (MessagingException e) {
            log.error("Failed to send wallet delete confirmation email", e);
            throw new RuntimeException("Failed to send confirmation email");
        }
    }
    private void sendHtml(String toEmail, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", toEmail, e);
            throw new RuntimeException("Failed to send email");
        }
    }

    public void sendWalletDeleteApprovalEmailToAdmin(
            String toEmail,
            String walletName,
            String requesterName,
            String approvalLinkToken
    ) {

        String subject = "[Orange Wallet] Approval Required – Shared Wallet Deletion";

        String html = """
            <p>Hello Admin,</p>
            <p>The shared wallet <b>%s</b> has been requested for deletion by <b>%s</b>.</p>
            <p>Please review and approve the deletion if appropriate.</p>
            <p><a href="%s" style="color:#1A73E8;">Click here to approve deletion</a></p>
            <br>
            <p>If you did not expect this email, you can ignore it.</p>
            <p>Regards,<br>Orange Wallet System</p>
            """.formatted(
                walletName,
                requesterName,
                approvalLinkToken 
            );

        sendHtml(toEmail, subject, html);
    }
}

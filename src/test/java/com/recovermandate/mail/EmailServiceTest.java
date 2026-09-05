package com.recovermandate.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
    }

    @Test
    @DisplayName("Should run in simulated mode when credentials are not configured")
    void sendRecoveryEmail_unconfigured_returnsSimulated() {
        ReflectionTestUtils.setField(emailService, "mailUsername", "");
        ReflectionTestUtils.setField(emailService, "mailPassword", "");
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);

        EmailSendResult result = emailService.sendRecoveryEmail(
                "customer@example.com",
                "Jane Doe",
                "Action Required",
                "Payment failed message",
                "https://rzp.io/l/preview",
                49900L,
                "INR"
        );

        assertNotNull(result);
        assertTrue(result.isSimulated());
        assertTrue(result.getProviderMessageId().startsWith("simulated-smtp-"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should send real email via JavaMailSender when credentials are fully configured")
    void sendRecoveryEmail_configured_sendsRealEmail() {
        ReflectionTestUtils.setField(emailService, "mailUsername", "billing@gmail.com");
        ReflectionTestUtils.setField(emailService, "mailPassword", "app-password-1234");
        ReflectionTestUtils.setField(emailService, "mailHost", "smtp.gmail.com");
        ReflectionTestUtils.setField(emailService, "mailPort", 587);
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "fromEmail", "billing@gmail.com");
        ReflectionTestUtils.setField(emailService, "fromName", "RecoverMandate Billing");

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        EmailSendResult result = emailService.sendRecoveryEmail(
                "customer@example.com",
                "Jane Doe",
                "Action Required: Pay Overdue",
                "Payment failed message body",
                "https://rzp.io/l/preview123",
                49900L,
                "INR"
        );

        assertNotNull(result);
        assertTrue(result.isRealSent());
        assertTrue(result.getProviderMessageId().startsWith("smtp-"));
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should return failed result when JavaMailSender throws exception during send")
    void sendRecoveryEmail_smtpError_returnsFailed() {
        ReflectionTestUtils.setField(emailService, "mailUsername", "billing@gmail.com");
        ReflectionTestUtils.setField(emailService, "mailPassword", "bad-password");
        ReflectionTestUtils.setField(emailService, "mailHost", "smtp.gmail.com");
        ReflectionTestUtils.setField(emailService, "mailPort", 587);
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("535 Authentication credentials invalid")).when(mailSender).send(any(MimeMessage.class));

        EmailSendResult result = emailService.sendRecoveryEmail(
                "customer@example.com",
                "Jane Doe",
                "Action Required",
                "Payment failed",
                "https://rzp.io/l/preview",
                49900L,
                "INR"
        );

        assertNotNull(result);
        assertTrue(result.isFailed());
        assertTrue(result.getErrorMessage().contains("535 Authentication credentials invalid"));
    }

    @Test
    @DisplayName("Should substitute localhost placeholder links in email message body with the live paymentLinkUrl")
    void sendRecoveryEmail_substitutesPlaceholderWithRealRazorpayLink() {
        ReflectionTestUtils.setField(emailService, "mailUsername", "billing@gmail.com");
        ReflectionTestUtils.setField(emailService, "mailPassword", "app-password-1234");
        ReflectionTestUtils.setField(emailService, "mailHost", "smtp.gmail.com");
        ReflectionTestUtils.setField(emailService, "mailPort", 587);
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);

        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String messageWithPlaceholder = "Dear Customer,\n\nYour payment failed. Retry here: http://localhost:5173/#pay/plink_preview_act_32\n\nThank you.";
        String liveRazorpayUrl = "https://rzp.io/rzp/liveLink999";

        EmailSendResult result = emailService.sendRecoveryEmail(
                "customer@example.com",
                "Jane Doe",
                "Action Required: Pay Overdue",
                messageWithPlaceholder,
                liveRazorpayUrl,
                49900L,
                "INR"
        );

        assertNotNull(result);
        assertTrue(result.isRealSent());
        verify(mailSender).send(mimeMessage);
    }
}

package com.recovermandate.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service to dispatch real transactional recovery emails via SMTP (e.g. Gmail SMTP, Brevo, AWS SES)
 * with graceful fallback to simulated mock delivery when credentials are not configured.
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${recovermandate.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${recovermandate.mail.from-email:billing@recovermandate.io}")
    private String fromEmail;

    @Value("${recovermandate.mail.from-name:RecoverMandate Billing}")
    private String fromName;

    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Checks whether live SMTP credentials are fully configured.
     */
    public boolean isConfigured() {
        return mailEnabled &&
                mailSender != null &&
                mailUsername != null && !mailUsername.isBlank() &&
                mailPassword != null && !mailPassword.isBlank();
    }

    /**
     * Dispatches a recovery email to the customer with embedded Razorpay payment link.
     *
     * @param toEmail        Customer's destination email
     * @param customerName   Customer display name
     * @param subject        Email subject line
     * @param messageText    AI-drafted message text body
     * @param paymentLinkUrl Hosted Razorpay payment link URL
     * @param amountInPaise  Transaction amount in paise (optional)
     * @param currency       Currency code (e.g. INR)
     * @return EmailSendResult indicating REAL_SENT, SIMULATED, or FAILED
     */
    public EmailSendResult sendRecoveryEmail(
            String toEmail,
            String customerName,
            String subject,
            String messageText,
            String paymentLinkUrl,
            Long amountInPaise,
            String currency
    ) {
        if (toEmail == null || toEmail.isBlank()) {
            return EmailSendResult.failed("Missing recipient email address");
        }

        String safeToEmail = toEmail.trim();
        String maskedEmail = maskEmail(safeToEmail);

        if (!isConfigured()) {
            String simId = "simulated-smtp-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("[EMAIL-DISPATCH] SMTP credentials not configured (SPRING_MAIL_USERNAME or SPRING_MAIL_PASSWORD empty). " +
                    "Dispatch running in SIMULATED mode for recipient={}", maskedEmail);
            return EmailSendResult.simulated(simId);
        }

        try {
            log.info("[EMAIL-DISPATCH] Initiating live SMTP delivery to {} via host={}:{}", maskedEmail, mailHost, mailPort);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String effectiveFrom = (mailUsername != null && !mailUsername.isBlank() && mailUsername.contains("@"))
                    ? mailUsername.trim()
                    : fromEmail;

            helper.setFrom(new InternetAddress(effectiveFrom, fromName));
            helper.setTo(safeToEmail);

            String effectiveSubject = (subject != null && !subject.isBlank())
                    ? subject
                    : "Action Required: Your Subscription Mandate Payment Failed";
            helper.setSubject(effectiveSubject);

            String htmlContent = buildBrandedHtmlEmail(
                    customerName,
                    messageText,
                    paymentLinkUrl,
                    amountInPaise,
                    currency
            );

            String plainTextContent = (messageText != null ? messageText : "") +
                    (paymentLinkUrl != null && !paymentLinkUrl.isBlank() ? "\n\nPay via Razorpay Secure Checkout:\n" + paymentLinkUrl : "");

            helper.setText(plainTextContent, htmlContent);

            mailSender.send(mimeMessage);

            String msgId = "smtp-" + UUID.randomUUID().toString().substring(0, 12);
            log.info("[EMAIL-DISPATCH] Live recovery email successfully sent to {} with messageId={}", maskedEmail, msgId);
            return EmailSendResult.realSent(msgId);

        } catch (Exception e) {
            log.error("[EMAIL-DISPATCH] Failed to send live email to {}: {}", maskedEmail, e.getMessage(), e);
            return EmailSendResult.failed("SMTP Error: " + e.getMessage());
        }
    }

    /**
     * Builds a responsive, branded HTML email template matching RecoverMandate's dark/blue theme.
     */
    private String buildBrandedHtmlEmail(
            String customerName,
            String messageText,
            String paymentLinkUrl,
            Long amountInPaise,
            String currency
    ) {
        String safeName = (customerName != null && !customerName.isBlank()) ? customerName : "Valued Customer";
        String safeCurrency = (currency != null && !currency.isBlank()) ? currency : "INR";
        String formattedAmount = amountInPaise != null
                ? String.format("%.2f %s", amountInPaise / 100.0, safeCurrency)
                : "";

        String formattedParagraphs = (messageText != null ? messageText : "")
                .replace("\n\n", "</p><p style=\"margin: 0 0 16px 0; line-height: 1.6;\">")
                .replace("\n", "<br/>");

        String ctaButtonHtml = "";
        if (paymentLinkUrl != null && !paymentLinkUrl.isBlank()) {
            ctaButtonHtml = """
                <div style="margin: 28px 0; text-align: center;">
                  <a href="%s" target="_blank" style="display: inline-block; background: linear-gradient(135deg, #3395FF 0%%, #1D4ED8 100%%); color: #ffffff; text-decoration: none; font-size: 15px; font-weight: bold; padding: 14px 28px; border-radius: 10px; box-shadow: 0 4px 14px rgba(51, 149, 255, 0.4); letter-spacing: 0.3px;">
                    ⚡ Pay Overdue %s via Razorpay Secure Checkout
                  </a>
                  <p style="margin-top: 10px; font-size: 11px; color: #64748b; font-family: monospace;">
                    Link: <a href="%s" style="color: #3395FF; text-decoration: underline;">%s</a>
                  </p>
                </div>
            """.formatted(paymentLinkUrl, formattedAmount, paymentLinkUrl, paymentLinkUrl);
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Subscription Mandate Recovery</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #02042B; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #e2e8f0;">
              <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="background-color: #02042B; padding: 30px 15px;">
                <tr>
                  <td align="center">
                    <table width="100%%" border="0" cellspacing="0" cellpadding="0" style="max-width: 600px; background-color: #0C2340; border-radius: 16px; border: 1px solid rgba(51, 149, 255, 0.3); overflow: hidden; box-shadow: 0 20px 40px rgba(0,0,0,0.5);">
                      <!-- Header -->
                      <tr>
                        <td style="padding: 24px 30px; background: linear-gradient(135deg, #061530 0%%, #0C2340 100%%); border-bottom: 1px solid rgba(51, 149, 255, 0.2);">
                          <table width="100%%" border="0" cellspacing="0" cellpadding="0">
                            <tr>
                              <td>
                                <div style="display: inline-block; vertical-align: middle;">
                                  <span style="font-size: 20px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px;">Recover<span style="color: #3395FF;">Mandate</span></span>
                                  <span style="display: block; font-size: 11px; color: #93c5fd; font-family: monospace; margin-top: 2px;">⚡ Razorpay Mandate Recovery Engine</span>
                                </div>
                              </td>
                              <td align="right">
                                <span style="background-color: rgba(51, 149, 255, 0.15); color: #93c5fd; border: 1px solid rgba(51, 149, 255, 0.3); font-size: 10px; font-weight: bold; text-transform: uppercase; padding: 4px 10px; border-radius: 20px; letter-spacing: 0.5px;">
                                  Verified Dispatch
                                </span>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>

                      <!-- Body -->
                      <tr>
                        <td style="padding: 32px 30px; color: #cbd5e1; font-size: 14px; line-height: 1.6;">
                          <div style="margin-bottom: 20px;">
                            <p style="margin: 0 0 16px 0; line-height: 1.6;">%s</p>
                          </div>

                          %s

                          <div style="background-color: #061530; border: 1px solid rgba(51, 149, 255, 0.15); border-radius: 10px; padding: 14px; margin-top: 24px; font-size: 11px; color: #94a3b8; line-height: 1.5;">
                            <strong style="color: #e2e8f0; display: block; margin-bottom: 4px;">🔒 Secure Payment Guarantee</strong>
                            This transaction is securely routed via Razorpay 256-Bit SSL encrypted gateway with instant mandate restoration upon settlement.
                          </div>
                        </td>
                      </tr>

                      <!-- Footer -->
                      <tr>
                        <td style="padding: 20px 30px; background-color: #040d1e; border-top: 1px solid rgba(51, 149, 255, 0.1); font-size: 11px; color: #64748b; text-align: center; line-height: 1.5;">
                          <p style="margin: 0 0 6px 0;">
                            Sent to <strong style="color: #94a3b8;">%s</strong> regarding recurring subscription payment.
                          </p>
                          <p style="margin: 0; color: #475569;">
                            If you no longer wish to continue your subscription, you can cancel anytime in your account settings.
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(formattedParagraphs, ctaButtonHtml, safeName);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@example.com";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 3) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.substring(0, 3) + "***@" + domain;
    }
}

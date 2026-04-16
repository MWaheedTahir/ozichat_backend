package com.ozichat.email.service.impl;

import com.ozichat.email.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.from-email:noreply@ozichat.com}")
    private String fromEmail;

    @Value("${app.from-name:OziChat}")
    private String fromName;

    @Value("${otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Override
    @Async
    public void sendEmailVerificationOtp(String toEmail, String displayName, String otp) {
        sendOtp(toEmail, displayName, "Verify your OziChat email", otp, otpExpiryMinutes);
    }

    @Override
    @Async
    public void sendPasswordResetOtp(String toEmail, String displayName, String otp) {
        sendOtp(toEmail, displayName, "Reset your OziChat password", otp, otpExpiryMinutes);
    }

    @Override
    @Async
    public void sendOtp(String toEmail, String displayName, String subject, String otp, int expiryMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(buildHtmlBody(displayName, otp, subject, expiryMinutes), true);

            mailSender.send(message);
            log.info("OTP email sent to={} subject='{}'", toEmail, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to={}: {}", toEmail, e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────
    // HTML template builder
    // ──────────────────────────────────────────────

    private String buildHtmlBody(String displayName, String otp, String subject, int expiryMinutes) {
        String name = displayName != null ? displayName : "there";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,Helvetica,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="560" cellpadding="0" cellspacing="0"
                               style="background:#ffffff;border-radius:12px;overflow:hidden;
                                      box-shadow:0 4px 20px rgba(0,0,0,.08);">
                          <!-- Header -->
                          <tr>
                            <td align="center" style="background:#2563eb;padding:32px 40px;">
                              <h1 style="margin:0;color:#ffffff;font-size:28px;letter-spacing:1px;">OziChat</h1>
                            </td>
                          </tr>
                          <!-- Body -->
                          <tr>
                            <td style="padding:40px;">
                              <p style="margin:0 0 16px;font-size:16px;color:#374151;">Hi %s,</p>
                              <p style="margin:0 0 24px;font-size:15px;color:#6b7280;">
                                Use the verification code below to complete your request.
                                This code expires in <strong>%d minutes</strong>.
                              </p>
                              <!-- OTP Box -->
                              <div style="background:#f0f4ff;border:2px dashed #2563eb;border-radius:10px;
                                          text-align:center;padding:24px;margin-bottom:24px;">
                                <span style="font-size:40px;font-weight:900;letter-spacing:10px;color:#2563eb;">%s</span>
                              </div>
                              <p style="margin:0 0 8px;font-size:13px;color:#9ca3af;">
                                If you did not request this, you can safely ignore this email.
                              </p>
                              <p style="margin:0;font-size:13px;color:#9ca3af;">
                                Do not share this code with anyone.
                              </p>
                            </td>
                          </tr>
                          <!-- Footer -->
                          <tr>
                            <td style="background:#f9fafb;padding:20px 40px;text-align:center;
                                        border-top:1px solid #e5e7eb;">
                              <p style="margin:0;font-size:12px;color:#9ca3af;">
                                &copy; 2025 OziChat. All rights reserved.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(subject, name, expiryMinutes, otp);
    }
}

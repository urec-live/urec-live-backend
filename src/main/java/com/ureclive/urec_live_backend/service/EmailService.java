package com.ureclive.urec_live_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail.from:}") String fromAddress,
            @Value("${spring.mail.username:}") String mailUsername
    ) {
        this.mailSender = mailSender;
        if (fromAddress != null && !fromAddress.isBlank()) {
            this.fromAddress = fromAddress;
        } else if (mailUsername != null && !mailUsername.isBlank()) {
            this.fromAddress = mailUsername;
        } else {
            this.fromAddress = "no-reply@urec.live";
        }
    }

    public boolean sendPasswordResetEmail(String toEmail, String resetLink) {
        if (mailSender == null) {
            System.out.println("[Email] No mail sender configured. Reset link: " + resetLink);
            return false;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(fromAddress);
        message.setSubject("Reset your UREC Live password");
        message.setText(
                "We received a request to reset your password.\n\n" +
                "Use this link to set a new password:\n" +
                resetLink + "\n\n" +
                "If you did not request this, you can ignore this email."
        );
        mailSender.send(message);
        return true;
    }
}

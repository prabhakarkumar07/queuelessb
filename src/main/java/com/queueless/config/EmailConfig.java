package com.queueless.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Email service configuration using MailerSend.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "email")
public class EmailConfig {

    /** MailerSend API key */
    private String apiKey;

    /** Default sender email */
    private String fromEmail = "noreply@queueless.in";

    /** Default sender name */
    private String fromName = "QueueLess";

    /** MailerSend API base URL */
    private String apiUrl = "https://api.mailersend.com/v1";

    /** Whether email sending is enabled */
    private boolean enabled = true;
}

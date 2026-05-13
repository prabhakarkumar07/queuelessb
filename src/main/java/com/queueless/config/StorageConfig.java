package com.queueless.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Storage configuration with primary/fallback provider support.
 * Configurable via environment variables.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "storage")
public class StorageConfig {

    /** Primary storage provider: "supabase" or "cloudinary" */
    private String primaryProvider = "supabase";

    /** Fallback storage provider: "cloudinary" or "supabase" */
    private String fallbackProvider = "cloudinary";

    /** Whether to attempt fallback on primary failure */
    private boolean fallbackEnabled = true;

    /** Maximum retry attempts per provider */
    private int maxRetries = 2;

    private Supabase supabase = new Supabase();
    private Cloudinary cloudinary = new Cloudinary();

    @Data
    public static class Supabase {
        private String url;
        private String serviceKey;
        private String bucket = "attachments";
    }

    @Data
    public static class Cloudinary {
        private String cloudName;
        private String apiKey;
        private String apiSecret;
        private String folder = "queueless";
    }
}

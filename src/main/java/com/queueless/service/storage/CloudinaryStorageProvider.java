package com.queueless.service.storage;

import com.queueless.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Cloudinary storage provider implementation.
 * Uses Cloudinary Upload API for file uploads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudinaryStorageProvider implements StorageProvider {

    private final StorageConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getProviderName() {
        return "cloudinary";
    }

    @Override
    public String upload(MultipartFile file, String path) throws Exception {
        var cloudinary = config.getCloudinary();
        String url = "https://api.cloudinary.com/v1_1/" + cloudinary.getCloudName() + "/auto/upload";

        // Generate signature timestamp
        long timestamp = System.currentTimeMillis() / 1000;
        String publicId = cloudinary.getFolder() + "/" + path.replace(".", "_" + timestamp + ".");

        // Unsigned upload with API key (for simplicity; production should use signed uploads)
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        body.add("api_key", cloudinary.getApiKey());
        body.add("timestamp", String.valueOf(timestamp));
        body.add("public_id", publicId);
        body.add("folder", cloudinary.getFolder());

        // Generate signature
        String toSign = "folder=" + cloudinary.getFolder()
                + "&public_id=" + publicId
                + "&timestamp=" + timestamp
                + cloudinary.getApiSecret();
        String signature = sha1Hex(toSign);
        body.add("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Cloudinary upload failed: " + response.getStatusCode());
        }

        return (String) response.getBody().get("secure_url");
    }

    @Override
    public void delete(String path) throws Exception {
        // Cloudinary deletion requires the public_id
        log.info("Cloudinary delete requested for path: {}", path);
    }

    @Override
    public boolean isAvailable() {
        var cloudinary = config.getCloudinary();
        return cloudinary.getCloudName() != null && !cloudinary.getCloudName().isBlank()
                && cloudinary.getApiKey() != null && !cloudinary.getApiKey().isBlank()
                && cloudinary.getApiSecret() != null && !cloudinary.getApiSecret().isBlank();
    }

    private String sha1Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 hashing failed", e);
        }
    }
}

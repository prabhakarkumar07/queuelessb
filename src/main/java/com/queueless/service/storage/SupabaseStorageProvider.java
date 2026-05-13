package com.queueless.service.storage;

import com.queueless.config.StorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Supabase Storage provider implementation.
 * Uses Supabase Storage REST API for file uploads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupabaseStorageProvider implements StorageProvider {

    private final StorageConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getProviderName() {
        return "supabase";
    }

    @Override
    public String upload(MultipartFile file, String path) throws Exception {
        var supabase = config.getSupabase();
        String url = supabase.getUrl() + "/storage/v1/object/" + supabase.getBucket() + "/" + path;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabase.getServiceKey());
        headers.set("Content-Type", file.getContentType());
        headers.set("x-upsert", "true");

        HttpEntity<byte[]> entity = new HttpEntity<>(file.getBytes(), headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Supabase upload failed: " + response.getStatusCode());
        }

        // Return public URL
        return supabase.getUrl() + "/storage/v1/object/public/" + supabase.getBucket() + "/" + path;
    }

    @Override
    public void delete(String path) throws Exception {
        var supabase = config.getSupabase();
        String url = supabase.getUrl() + "/storage/v1/object/" + supabase.getBucket() + "/" + path;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabase.getServiceKey());

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    @Override
    public boolean isAvailable() {
        var supabase = config.getSupabase();
        return supabase.getUrl() != null && !supabase.getUrl().isBlank()
                && supabase.getServiceKey() != null && !supabase.getServiceKey().isBlank();
    }
}

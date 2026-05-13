package com.queueless.service.storage;

import com.queueless.config.StorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Unified storage service with automatic failover between providers.
 * Tracks which provider was used for each upload.
 */
@Slf4j
@Service
public class StorageService {

    private final StorageConfig config;
    private final Map<String, StorageProvider> providers;

    public StorageService(StorageConfig config, List<StorageProvider> providerList) {
        this.config = config;
        this.providers = providerList.stream()
                .collect(Collectors.toMap(StorageProvider::getProviderName, p -> p));
    }

    /**
     * Upload a file with automatic failover.
     * Returns metadata including the URL, provider used, and whether fallback was triggered.
     */
    public UploadResult upload(MultipartFile file, String directory) {
        String filename = generateFilename(file.getOriginalFilename());
        String path = directory + "/" + filename;

        // Try primary provider
        StorageProvider primary = providers.get(config.getPrimaryProvider());
        if (primary != null && primary.isAvailable()) {
            try {
                String url = primary.upload(file, path);
                log.info("Upload successful via primary provider [{}]: {}", primary.getProviderName(), path);
                return UploadResult.builder()
                        .url(url)
                        .provider(primary.getProviderName())
                        .path(path)
                        .filename(filename)
                        .contentType(file.getContentType())
                        .size(file.getSize())
                        .uploadedAt(Instant.now())
                        .usedFallback(false)
                        .build();
            } catch (Exception e) {
                log.warn("Primary provider [{}] failed for {}: {}", primary.getProviderName(), path, e.getMessage());
            }
        }

        // Try fallback provider
        if (config.isFallbackEnabled()) {
            StorageProvider fallback = providers.get(config.getFallbackProvider());
            if (fallback != null && fallback.isAvailable()) {
                try {
                    String url = fallback.upload(file, path);
                    log.info("Upload successful via fallback provider [{}]: {}", fallback.getProviderName(), path);
                    return UploadResult.builder()
                            .url(url)
                            .provider(fallback.getProviderName())
                            .path(path)
                            .filename(filename)
                            .contentType(file.getContentType())
                            .size(file.getSize())
                            .uploadedAt(Instant.now())
                            .usedFallback(true)
                            .build();
                } catch (Exception e) {
                    log.error("Fallback provider [{}] also failed for {}: {}", fallback.getProviderName(), path, e.getMessage());
                }
            }
        }

        throw new RuntimeException("All storage providers failed for file: " + path);
    }

    /**
     * Delete a file from the specified provider.
     */
    public void delete(String path, String providerName) {
        StorageProvider provider = providers.get(providerName);
        if (provider == null) {
            log.warn("Unknown provider for deletion: {}", providerName);
            return;
        }
        try {
            provider.delete(path);
            log.info("Deleted {} from [{}]", path, providerName);
        } catch (Exception e) {
            log.error("Failed to delete {} from [{}]: {}", path, providerName, e.getMessage());
        }
    }

    private String generateFilename(String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16) + ext;
    }

    /**
     * Result of an upload operation with full metadata.
     */
    @lombok.Data
    @lombok.Builder
    public static class UploadResult {
        private String url;
        private String provider;
        private String path;
        private String filename;
        private String contentType;
        private long size;
        private Instant uploadedAt;
        private boolean usedFallback;
    }
}

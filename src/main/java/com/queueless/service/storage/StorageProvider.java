package com.queueless.service.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Interface for storage providers (Supabase, Cloudinary, MinIO, etc.)
 */
public interface StorageProvider {

    /** Unique provider name (e.g., "supabase", "cloudinary") */
    String getProviderName();

    /**
     * Upload a file and return the public URL.
     * @param file the file to upload
     * @param path the storage path/key (e.g., "avatars/user-123.jpg")
     * @return public URL of the uploaded file
     */
    String upload(MultipartFile file, String path) throws Exception;

    /**
     * Delete a file by its path.
     * @param path the storage path/key
     */
    void delete(String path) throws Exception;

    /** Check if this provider is configured and available */
    boolean isAvailable();
}

package com.queueless.service;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * MinIO-backed file storage service.
 * <p>
 * uploadFile()  — generic upload (legacy, used by attachments)
 * uploadImage() — validates MIME type, compresses to ≤800px, stores under a folder prefix
 * deleteObject()— removes an object by its URL
 */
@Service
@Slf4j
public class MinioService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024L; // 5 MB
    private static final int MAX_IMAGE_DIMENSION = 800;

    private final MinioClient minioClient;

    @Value("${minio.bucket:attachments}")
    private String bucketName;

    @Value("${minio.endpoint:}")
    private String endpoint;

    @Autowired
    public MinioService(@Autowired(required = false) MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    private void ensureConfigured() {
        if (minioClient == null) {
            throw new IllegalStateException("MinIO is not configured. Set minio.endpoint to enable file uploads.");
        }
    }

    // ── Generic upload (existing behaviour, unchanged) ──

    public String uploadFile(MultipartFile file) {
        ensureConfigured();
        try {
            ensureBucket();
            String fileName = UUID.randomUUID() + extensionOf(file);
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName).object(fileName)
                        .stream(in, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
            return publicUrl(fileName);
        } catch (Exception e) {
            log.error("MinIO upload failed", e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    // ── Validated image upload ──

    /**
     * Validates MIME type and file size, compresses to ≤800×800 px,
     * stores under the given folder prefix (e.g. "avatars/" or "logos/").
     *
     * @param file   the uploaded file
     * @param folder storage folder prefix, e.g. "avatars/"
     * @return public URL of the stored image
     */
    public String uploadImage(MultipartFile file, String folder) {
        ensureConfigured();
        // 1. MIME type check
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported image type. Allowed: JPEG, PNG, WebP");
        }

        // 2. Size check (before compression)
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image must be smaller than 5 MB");
        }

        try {
            ensureBucket();

            // 3. Compress / resize to ≤800×800 maintaining aspect ratio
            byte[] compressed = compress(file);

            String objectName = folder + UUID.randomUUID() + ".jpg";
            try (InputStream in = new ByteArrayInputStream(compressed)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucketName).object(objectName)
                        .stream(in, compressed.length, -1)
                        .contentType("image/jpeg")
                        .build());
            }

            log.info("Image uploaded: {}", objectName);
            return publicUrl(objectName);
        } catch (IllegalArgumentException e) {
            throw e; // re-throw validation errors as-is
        } catch (Exception e) {
            log.error("MinIO image upload failed", e);
            throw new RuntimeException("Failed to upload image", e);
        }
    }

    /**
     * Removes an image from MinIO by its full public URL.
     * Silently ignores objects that no longer exist.
     */
    public void deleteByUrl(String url) {
        if (url == null || url.isBlank()) return;
        if (minioClient == null) return;
        try {
            // Extract object name from URL: endpoint/bucket/<objectName>
            String prefix = endpoint + "/" + bucketName + "/";
            if (!url.startsWith(prefix)) return;
            String objectName = url.substring(prefix.length());
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName).object(objectName).build());
            log.info("Deleted object from MinIO: {}", objectName);
        } catch (Exception e) {
            log.warn("Could not delete MinIO object for URL {}: {}", url, e.getMessage());
        }
    }

    // ── Helpers ──

    private byte[] compress(MultipartFile file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .size(MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                .keepAspectRatio(true)
                .outputFormat("jpg")
                .outputQuality(0.85)
                .toOutputStream(out);
        return out.toByteArray();
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Created bucket {}", bucketName);
        }

        // Always ensure public read-only policy is set so existing buckets are fixed
        String policy = "{\n" +
                "  \"Version\":\"2012-10-17\",\n" +
                "  \"Statement\":[\n" +
                "    {\n" +
                "      \"Action\":[\"s3:GetObject\"],\n" +
                "      \"Effect\":\"Allow\",\n" +
                "      \"Principal\":\"*\",\n" +
                "      \"Resource\":[\"arn:aws:s3:::" + bucketName + "/*\"]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build());
    }

    private String extensionOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf("."));
        }
        return "";
    }

    private String publicUrl(String objectName) {
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }
}

package com.queueless.controller;

import com.queueless.dto.Dtos;
import com.queueless.entity.Attachment;
import com.queueless.entity.User;
import com.queueless.exception.BusinessException;
import com.queueless.service.AttachmentService;
import com.queueless.service.MinioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Attachment upload and retrieval controller.
 * B-12: Added MIME-type whitelist validation before upload to prevent malicious file uploads.
 */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final MinioService minioService;

    /** Allowed MIME types for uploaded files. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "application/pdf"
    );

    /**
     * Uploads a file to MinIO and records an attachment.
     * Only JPEG, PNG, WebP, GIF and PDF files are accepted.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam UUID targetId,
            @RequestParam Attachment.TargetType targetType,
            @AuthenticationPrincipal User user) {

        // B-12: Validate MIME type before sending anything to MinIO
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(
                    "Unsupported file type: " + contentType +
                    ". Only JPEG, PNG, WebP, GIF, and PDF files are allowed.");
        }
        // Additional size guard (belt-and-suspenders alongside Spring's multipart limit)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("File too large. Maximum allowed size is 5 MB.");
        }

        attachmentService.validateAttachmentAccess(targetId, targetType, user);
        String url = minioService.uploadFile(file);
        Dtos.CreateAttachmentRequest request = new Dtos.CreateAttachmentRequest();
        request.setTargetId(targetId);
        request.setTargetType(targetType);
        request.setFileUrl(url);
        Dtos.AttachmentDto attachment = attachmentService.addAttachment(request, user);
        return ResponseEntity.ok(Map.of(
                "fileUrl", url,
                "attachmentId", attachment.getId().toString()
        ));
    }

    @PostMapping
    public ResponseEntity<Dtos.AttachmentDto> addAttachment(
            @Valid @RequestBody Dtos.CreateAttachmentRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attachmentService.addAttachment(request, user));
    }

    @GetMapping("/token/{tokenId}")
    public ResponseEntity<List<Dtos.AttachmentDto>> getTokenAttachments(
            @PathVariable UUID tokenId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attachmentService.getAttachments(tokenId, Attachment.TargetType.TOKEN, user));
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<List<Dtos.AttachmentDto>> getAppointmentAttachments(
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(attachmentService.getAttachments(appointmentId, Attachment.TargetType.APPOINTMENT, user));
    }
}

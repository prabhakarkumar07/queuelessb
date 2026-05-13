package com.queueless.service;

import com.queueless.dto.Dtos;
import com.queueless.entity.Attachment;
import com.queueless.entity.Appointment;
import com.queueless.entity.Token;
import com.queueless.entity.User;
import com.queueless.exception.AccessDeniedException;
import com.queueless.exception.ResourceNotFoundException;
import com.queueless.repository.AppointmentRepository;
import com.queueless.repository.AttachmentRepository;
import com.queueless.repository.ServiceProviderRepository;
import com.queueless.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final TokenRepository tokenRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceProviderRepository providerRepository;

    @Transactional
    public Dtos.AttachmentDto addAttachment(Dtos.CreateAttachmentRequest request, User user) {
        validateAttachmentAccess(request.getTargetId(), request.getTargetType(), user);
        Attachment attachment = Attachment.builder()
                .targetId(request.getTargetId())
                .targetType(request.getTargetType())
                .fileUrl(request.getFileUrl())
                .description(request.getDescription())
                .build();

        attachment = attachmentRepository.save(attachment);
        log.info("Added attachment {} for {} {}", attachment.getId(), request.getTargetType(), request.getTargetId());
        return toDto(attachment);
    }

    @Transactional(readOnly = true)
    public List<Dtos.AttachmentDto> getAttachments(UUID targetId, Attachment.TargetType type, User user) {
        validateAttachmentAccess(targetId, type, user);
        return attachmentRepository.findAllByTargetIdAndTargetType(targetId, type)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public void validateAttachmentAccess(UUID targetId, Attachment.TargetType type, User user) {
        if (user == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (user.getRole() == User.Role.ADMIN) {
            return;
        }

        if (type == Attachment.TargetType.TOKEN) {
            Token token = tokenRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Token not found"));
            if (token.getUser().getId().equals(user.getId())
                    || token.getShop().getOwner().getId().equals(user.getId())
                    || isProviderForShop(token.getShop().getId(), user.getId())) {
                return;
            }
        } else if (type == Attachment.TargetType.APPOINTMENT) {
            Appointment appointment = appointmentRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
            if (appointment.getUser().getId().equals(user.getId())
                    || appointment.getShop().getOwner().getId().equals(user.getId())
                    || isProviderForShop(appointment.getShop().getId(), user.getId())) {
                return;
            }
        }

        throw new AccessDeniedException("Not authorized to access attachments for this item");
    }

    private boolean isProviderForShop(UUID shopId, UUID userId) {
        return providerRepository.findByUserId(userId)
                .filter(provider -> provider.isActive() && provider.getShop().getId().equals(shopId))
                .isPresent();
    }

    private Dtos.AttachmentDto toDto(Attachment a) {
        return Dtos.AttachmentDto.builder()
                .id(a.getId())
                .targetId(a.getTargetId())
                .targetType(a.getTargetType().name())
                .fileUrl(a.getFileUrl())
                .description(a.getDescription())
                .createdAt(a.getCreatedAt())
                .build();
    }
}

package com.queueless.service;

// Trigger recompile
import com.queueless.dto.Dtos.*;
import com.queueless.entity.*;
import com.queueless.exception.*;
import com.queueless.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages customer reviews and ratings for shops.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;
    private final TokenRepository tokenRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;

    /**
     * Submits a review. The customer must have a SERVED token or COMPLETED appointment
     * at the shop to leave a review.
     */
    @Transactional
    public ReviewDto create(CreateReviewRequest request, UUID userId) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Token token = null;
        Appointment appointment = null;

        if (request.getTokenId() != null) {
            token = tokenRepository.findById(request.getTokenId())
                    .filter(t -> t.getUser().getId().equals(userId))
                    .filter(t -> t.getShop().getId().equals(shop.getId()))
                    .filter(t -> t.getStatus() == Token.TokenStatus.SERVED)
                    .orElseThrow(() -> new BusinessException(
                            "You can only review after your token has been served"));

            reviewRepository.findByUserIdAndTokenId(userId, token.getId())
                    .ifPresent(r -> { throw new BusinessException("You have already reviewed this visit"); });
        } else if (request.getAppointmentId() != null) {
            appointment = appointmentRepository.findById(request.getAppointmentId())
                    .filter(a -> a.getUser().getId().equals(userId))
                    .filter(a -> a.getShop().getId().equals(shop.getId()))
                    .filter(a -> a.getStatus() == Appointment.AppointmentStatus.COMPLETED)
                    .orElseThrow(() -> new BusinessException(
                            "You can only review after your appointment is completed"));

            reviewRepository.findByUserIdAndAppointmentId(userId, appointment.getId())
                    .ifPresent(r -> { throw new BusinessException("You have already reviewed this appointment"); });
        } else {
            throw new BusinessException("Either tokenId or appointmentId must be provided");
        }

        Review review = Review.builder()
                .shop(shop)
                .user(user)
                .token(token)
                .appointment(appointment)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        review = reviewRepository.save(review);
        log.info("Review {} created for shop {} by user {}", review.getId(), shop.getId(), userId);
        return toDto(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> getShopReviews(UUID shopId, int page, int size) {
        Page<Review> reviewPage = reviewRepository
                .findByShopIdAndVisibleTrueOrderByCreatedAtDesc(shopId, PageRequest.of(page, size));

        return toPageResponse(reviewPage, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> getOwnerShopReviews(UUID shopId, UUID actorId, int page, int size) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        boolean isOwner = shop.getOwner().getId().equals(actorId);
        boolean isAdmin = userRepository.findById(actorId)
                .map(user -> user.getRole() == User.Role.ADMIN)
                .orElse(false);
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Not authorized to view reviews for this shop");
        }

        Page<Review> reviewPage = reviewRepository
                .findByShopIdOrderByCreatedAtDesc(shopId, PageRequest.of(page, size));
        return toPageResponse(reviewPage, page, size);
    }

    private PageResponse<ReviewDto> toPageResponse(Page<Review> reviewPage, int page, int size) {
        return PageResponse.<ReviewDto>builder()
                .content(reviewPage.getContent().stream().map(this::toDto).collect(Collectors.toList()))
                .page(page).size(size)
                .totalElements(reviewPage.getTotalElements())
                .totalPages(reviewPage.getTotalPages())
                .last(reviewPage.isLast())
                .build();
    }

    @Transactional
    public ReviewDto moderate(UUID reviewId, Review.ModerationStatus status, String reason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        review.setModerationStatus(status);
        review.setModerationReason(reason);
        review.setModeratedAt(java.time.Instant.now());
        review.setVisible(status == Review.ModerationStatus.APPROVED);
        return toDto(reviewRepository.save(review));
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> getFlaggedReviews() {
        return reviewRepository
                .findByModerationStatusOrderByCreatedAtDesc(Review.ModerationStatus.FLAGGED)
                .stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReviewSummaryDto getSummary(UUID shopId) {
        return buildSummary(reviewRepository.getRatingSummary(shopId), reviewRepository.getRatingBreakdown(shopId));
    }

    @Transactional(readOnly = true)
    public ReviewSummaryDto getOwnerSummary(UUID shopId, UUID actorId) {
        validateOwnerAccess(shopId, actorId);
        return buildSummary(reviewRepository.getOwnerRatingSummary(shopId), reviewRepository.getOwnerRatingBreakdown(shopId));
    }

    private void validateOwnerAccess(UUID shopId, UUID actorId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        boolean isOwner = shop.getOwner().getId().equals(actorId);
        boolean isAdmin = userRepository.findById(actorId)
                .map(user -> user.getRole() == User.Role.ADMIN)
                .orElse(false);
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("Not authorized to view reviews for this shop");
        }
    }

    private ReviewSummaryDto buildSummary(Object[] row, List<Object[]> breakdownRows) {
        Map<Integer, Long> breakdown = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) breakdown.put(i, 0L);
        breakdownRows.forEach(r -> {
            breakdown.put(((Number) r[0]).intValue(), ((Number) r[1]).longValue());
        });

        long total = breakdown.values().stream().mapToLong(Long::longValue).sum();
        long weightedTotal = breakdown.entrySet().stream()
                .mapToLong(entry -> entry.getKey() * entry.getValue())
                .sum();
        double avg = total > 0 ? (double) weightedTotal / total : 0.0;

        return ReviewSummaryDto.builder()
                .avgRating(Math.round(avg * 10.0) / 10.0)
                .totalReviews(total)
                .breakdown(breakdown)
                .build();
    }

    private ReviewDto toDto(Review r) {
        return ReviewDto.builder()
                .id(r.getId())
                .shopId(r.getShop().getId())
                .shopName(r.getShop().getName())
                .userId(r.getUser().getId())
                .userName(r.getUser().getName())
                .tokenId(r.getToken() != null ? r.getToken().getId() : null)
                .appointmentId(r.getAppointment() != null ? r.getAppointment().getId() : null)
                .rating(r.getRating())
                .comment(r.getComment())
                .visible(r.isVisible())
                .moderationStatus(r.getModerationStatus().name())
                .moderationReason(r.getModerationReason())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

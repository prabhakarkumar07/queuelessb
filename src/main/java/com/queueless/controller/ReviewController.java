package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for ratings and reviews.
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** Submit a review (customer only, after token SERVED or appointment COMPLETED). */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReviewDto> create(
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(request, user.getId()));
    }

    /** Get paginated reviews for a shop (public). */
    @GetMapping("/shops/{shopId}")
    public ResponseEntity<PageResponse<ReviewDto>> getShopReviews(
            @PathVariable UUID shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewService.getShopReviews(shopId, page, size));
    }

    /** Get owner/admin review list, including hidden or moderated reviews. */
    @GetMapping("/owner/shops/{shopId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<PageResponse<ReviewDto>> getOwnerShopReviews(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(reviewService.getOwnerShopReviews(shopId, user.getId(), page, size));
    }

    /** Get rating summary for a shop (public). */
    @GetMapping("/shops/{shopId}/summary")
    public ResponseEntity<ReviewSummaryDto> getSummary(@PathVariable UUID shopId) {
        return ResponseEntity.ok(reviewService.getSummary(shopId));
    }

    /** Get rating summary for owner/admin view, including hidden or moderated reviews. */
    @GetMapping("/owner/shops/{shopId}/summary")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<ReviewSummaryDto> getOwnerSummary(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(reviewService.getOwnerSummary(shopId, user.getId()));
    }
}

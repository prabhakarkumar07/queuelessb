package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.Shop;
import com.queueless.service.ReviewService;
import com.queueless.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/moderation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminModerationController {

    private final ReviewService reviewService;
    private final ShopService shopService;

    @GetMapping("/shops/pending")
    public ResponseEntity<List<ShopDto>> getPendingShops() {
        return ResponseEntity.ok(shopService.getPendingShops());
    }

    @GetMapping("/reviews/flagged")
    public ResponseEntity<List<ReviewDto>> getFlaggedReviews() {
        return ResponseEntity.ok(reviewService.getFlaggedReviews());
    }

    @PatchMapping("/reviews/{reviewId}")
    public ResponseEntity<ReviewDto> moderateReview(@PathVariable UUID reviewId,
                                                    @Valid @RequestBody ModerateReviewRequest request) {
        return ResponseEntity.ok(reviewService.moderate(reviewId, request.getStatus(), request.getReason()));
    }

    @PatchMapping("/shops/{shopId}/verification")
    public ResponseEntity<ShopDto> verifyShop(@PathVariable UUID shopId,
                                              @RequestParam Shop.VerificationStatus status,
                                              @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(shopService.adminUpdateVerification(shopId, status, active));
    }
}

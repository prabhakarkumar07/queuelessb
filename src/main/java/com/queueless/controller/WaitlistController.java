package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for waitlist management.
 */
@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    /** Join the waitlist for a shop. */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<WaitlistDto> join(
            @Valid @RequestBody JoinWaitlistRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(waitlistService.join(request, user.getId()));
    }

    /** Leave the waitlist. */
    @DeleteMapping("/{waitlistId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> leave(
            @PathVariable UUID waitlistId,
            @AuthenticationPrincipal User user) {
        waitlistService.leave(waitlistId, user.getId());
        return ResponseEntity.noContent().build();
    }

    /** Get my active waitlist entries. */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<WaitlistDto>> getMyWaitlist(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(waitlistService.getMyWaitlist(user.getId()));
    }
}

package com.queueless.controller;

import com.queueless.dto.Dtos.BusinessAccountDto;
import com.queueless.dto.Dtos.UpdateBusinessAccountRequest;
import com.queueless.entity.User;
import com.queueless.service.BusinessAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Business account management controller.
 *
 * Exposes two URL namespaces for the same resource:
 *   - /api/business/settings     (legacy / original mapping)
 *   - /api/owner/business-accounts (matches web dashboard businessAccountApi client)
 *
 * B-09: Fixed URL mismatch — the web client called /api/owner/business-accounts/* which always 404'd.
 */
@RestController
@RequiredArgsConstructor
public class BusinessAccountController {

    private final BusinessAccountService businessAccountService;

    // ——— /api/business/settings (original) ———

    @GetMapping("/api/business/settings")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public BusinessAccountDto getSettings(@AuthenticationPrincipal User user) {
        return businessAccountService.getMyBusinessAccount(user.getId());
    }

    @PutMapping("/api/business/settings")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public BusinessAccountDto updateSettings(@AuthenticationPrincipal User user,
                                             @Valid @RequestBody UpdateBusinessAccountRequest request) {
        return businessAccountService.updateBusinessAccount(user.getId(), request);
    }

    // ——— /api/owner/business-accounts (web dashboard businessAccountApi) ———

    /**
     * GET /api/owner/business-accounts
     * Returns the business account for the authenticated shop owner.
     */
    @GetMapping("/api/owner/business-accounts")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public BusinessAccountDto getMine(@AuthenticationPrincipal User user) {
        return businessAccountService.getMyBusinessAccount(user.getId());
    }

    /**
     * POST /api/owner/business-accounts
     * Creates or upserts the business account for the authenticated shop owner.
     */
    @PostMapping("/api/owner/business-accounts")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<BusinessAccountDto> create(@AuthenticationPrincipal User user,
                                                     @Valid @RequestBody UpdateBusinessAccountRequest request) {
        return ResponseEntity.ok(businessAccountService.updateBusinessAccount(user.getId(), request));
    }

    /**
     * PUT /api/owner/business-accounts/{id}
     * Updates the business account (id param is accepted but ignored — one account per owner).
     */
    @PutMapping("/api/owner/business-accounts/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public BusinessAccountDto update(@PathVariable String id,
                                     @AuthenticationPrincipal User user,
                                     @Valid @RequestBody UpdateBusinessAccountRequest request) {
        return businessAccountService.updateBusinessAccount(user.getId(), request);
    }
}

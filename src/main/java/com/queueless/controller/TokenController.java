package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.RateLimitService;
import com.queueless.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Token management controller — issue, call next, skip, cancel, queue view.
 */
@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;
    private final RateLimitService rateLimitService;

    /**
     * Issues a new token for the authenticated customer at the given shop.
     * POST /api/tokens
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TokenDto> getToken(@Valid @RequestBody GetTokenRequest request,
                                              @AuthenticationPrincipal User user,
                                              HttpServletRequest httpRequest) {
        rateLimitService.consumeTokenIssue("token:" + clientIp(httpRequest) + ":" + user.getId());
        TokenDto token = tokenService.getToken(
                request.getShopId(), user.getId(), request.getServiceId(), request.getProviderId(), request.getNotes());
        return ResponseEntity.status(HttpStatus.CREATED).body(token);
    }

    /**
     * Issues a token for a walk-in customer entered by staff at the counter.
     * POST /api/tokens/walk-in
     */
    @PostMapping("/walk-in")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> createWalkInToken(@Valid @RequestBody CreateWalkInTokenRequest request,
                                                       @AuthenticationPrincipal User user,
                                                       HttpServletRequest httpRequest) {
        rateLimitService.consumeTokenIssue("walk-in:" + clientIp(httpRequest) + ":" + user.getId());
        TokenDto token = tokenService.createWalkInToken(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(token);
    }

    /**
     * Calls the next waiting token (shop owner action).
     * POST /api/tokens/shops/{shopId}/call-next
     */
    @PostMapping("/shops/{shopId}/call-next")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> callNext(@PathVariable UUID shopId,
                                              @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.callNext(shopId, user.getId()));
    }

    /**
     * Skips a specific token.
     * POST /api/tokens/{tokenId}/skip
     */
    @PostMapping("/{tokenId}/skip")
    @PreAuthorize("hasAnyRole('CUSTOMER','SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> skip(@PathVariable UUID tokenId,
                                          @RequestBody(required = false) TokenActionReasonRequest request,
                                          @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.skipToken(tokenId, user.getId(), request != null ? request.getReason() : null));
    }

    /**
     * Lets a customer rejoin during the shop's grace window after a no-show skip.
     * POST /api/tokens/{tokenId}/rejoin
     */
    @PostMapping("/{tokenId}/rejoin")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TokenDto> rejoin(@PathVariable UUID tokenId,
                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.rejoinSkippedToken(tokenId, user.getId()));
    }

    /**
     * Snoozes a token, pushing it back in the queue (customer or operator action).
     * POST /api/tokens/{tokenId}/snooze
     */
    @PostMapping("/{tokenId}/snooze")
    @PreAuthorize("hasAnyRole('CUSTOMER','SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> snooze(@PathVariable UUID tokenId,
                                           @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.snoozeToken(tokenId, user.getId()));
    }

    /**
     * Cancels a token. Customers can cancel their own; shop owners/staff can cancel any in their shop.
     * POST /api/tokens/{tokenId}/cancel
     */
    @PostMapping("/{tokenId}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> cancel(@PathVariable UUID tokenId,
                                            @RequestBody(required = false) TokenActionReasonRequest request,
                                            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.cancelToken(tokenId, user.getId(), request != null ? request.getReason() : null));
    }

    /**
     * Marks a token as currently being served.
     * POST /api/tokens/{tokenId}/serving
     */
    @PostMapping("/{tokenId}/serving")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> markServing(@PathVariable UUID tokenId,
                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.markServing(tokenId, user.getId()));
    }

    /**
     * Marks a token as physically arrived at the counter.
     * POST /api/tokens/{tokenId}/arrived
     */
    @PostMapping("/{tokenId}/arrived")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> markArrived(@PathVariable UUID tokenId,
                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.markArrived(tokenId, user.getId()));
    }

    /**
     * Marks a token as completely served.
     * POST /api/tokens/{tokenId}/complete
     */
    @PostMapping("/{tokenId}/complete")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> completeToken(@PathVariable UUID tokenId,
                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.completeToken(tokenId, user.getId()));
    }

    /**
     * Returns the live queue state for a shop (public endpoint for TV display and customer tracking).
     * GET /api/tokens/shops/{shopId}/queue
     */
    @GetMapping("/shops/{shopId}/queue")
    public ResponseEntity<LiveQueueDto> getLiveQueue(@PathVariable UUID shopId) {
        return ResponseEntity.ok(tokenService.getLiveQueue(shopId));
    }

    /**
     * Returns live queue state with customer details for authorized operators only.
     * GET /api/tokens/shops/{shopId}/queue/operator
     */
    @GetMapping("/shops/{shopId}/queue/operator")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<LiveQueueDto> getOperatorLiveQueue(@PathVariable UUID shopId,
                                                              @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.getOperatorLiveQueue(shopId, user.getId()));
    }

    /**
     * Returns the authenticated user's token history.
     * GET /api/tokens/my-history
     */
    @GetMapping("/my-history")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PageResponse<TokenDto>> getMyHistory(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(tokenService.getUserTokenHistory(user.getId(), page, size));
    }

    /**
     * Sets the priority of a token (operator only).
     * POST /api/tokens/{tokenId}/priority
     */
    @PostMapping("/{tokenId}/priority")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> setPriority(
            @PathVariable UUID tokenId,
            @Valid @RequestBody SetPriorityRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.setPriority(tokenId, request.getPriority(), user.getId()));
    }

    /**
     * Transfers a waiting/called token to another service or provider.
     * POST /api/tokens/{tokenId}/transfer
     */
    @PostMapping("/{tokenId}/transfer")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<TokenDto> transferToken(
            @PathVariable UUID tokenId,
            @Valid @RequestBody TransferTokenRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(tokenService.transferToken(tokenId, request, user.getId()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

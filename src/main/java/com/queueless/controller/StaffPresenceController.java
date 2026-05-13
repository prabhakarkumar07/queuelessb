package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.StaffHeartbeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shops/{shopId}/staff-presence")
@RequiredArgsConstructor
public class StaffPresenceController {

    private final StaffHeartbeatService heartbeatService;

    @PostMapping("/heartbeat")
    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER', 'SHOP_OWNER', 'ADMIN')")
    public ResponseEntity<StaffHeartbeatDto> heartbeat(@PathVariable UUID shopId,
                                                       @Valid @RequestBody StaffHeartbeatRequest request,
                                                       @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(heartbeatService.heartbeat(shopId, user.getId(), request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<List<StaffHeartbeatDto>> presence(@PathVariable UUID shopId,
                                                            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(heartbeatService.getShopPresence(shopId, user.getId()));
    }
}

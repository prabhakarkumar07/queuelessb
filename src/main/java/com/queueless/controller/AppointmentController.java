package com.queueless.controller;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.User;
import com.queueless.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Appointment booking, payment verification, rescheduling, and cancellation endpoints.
 */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /** Books a new appointment and returns a Razorpay order ID for payment. */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AppointmentDto> book(@Valid @RequestBody BookAppointmentRequest request,
                                                @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.book(request, user.getId()));
    }

    /** Verifies Razorpay payment and confirms the appointment. */
    @PostMapping("/{appointmentId}/verify-payment")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AppointmentDto> verifyPayment(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody PaymentVerifyRequest verifyRequest,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(
                appointmentService.verifyPaymentAndConfirm(appointmentId, verifyRequest, user.getId()));
    }

    /** Reschedules an appointment to a new time slot. */
    @PatchMapping("/{appointmentId}/reschedule")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AppointmentDto> reschedule(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody RescheduleRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(appointmentService.reschedule(appointmentId, request, user.getId()));
    }

    /** Cancels an appointment (with refund if applicable). Customers cancel their own; owners/admin cancel any in their shop. */
    @DeleteMapping("/{appointmentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','SHOP_OWNER','ADMIN','SERVICE_PROVIDER')")
    public ResponseEntity<AppointmentDto> cancel(
            @PathVariable UUID appointmentId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(appointmentService.cancel(appointmentId, user.getId(), reason));
    }

    /** Marks an appointment as COMPLETED (Shop Owner only). */
    @PostMapping("/{appointmentId}/complete")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<AppointmentDto> complete(
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(appointmentService.completeAppointment(appointmentId, user.getId()));
    }

    /** Returns the authenticated user's appointment history. */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PageResponse<AppointmentDto>> getMyAppointments(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(appointmentService.getUserAppointments(user.getId(), page, size));
    }

    /** Returns all appointments for a specific shop (Owner/Admin only). */
    @GetMapping("/shop/{shopId}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','ADMIN')")
    public ResponseEntity<PageResponse<AppointmentDto>> getShopAppointments(
            @PathVariable UUID shopId,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(appointmentService.getShopAppointments(shopId, user.getId(), page, size));
    }
}
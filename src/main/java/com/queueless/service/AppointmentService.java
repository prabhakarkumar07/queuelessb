package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.*;
import com.queueless.exception.*;
import com.queueless.repository.*;
import com.razorpay.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for appointment booking, rescheduling, cancellation, and Razorpay
 * payment processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ShopRepository shopRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceProviderRepository serviceProviderRepository;
    private final ShopHolidayRepository shopHolidayRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final LoyaltyService loyaltyService;
    private final TokenService tokenService;
    private final Environment environment;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    /**
     * Books an appointment slot and creates a Razorpay payment order.
     * Validates slot availability before charging.
     *
     * @param request appointment booking data
     * @param userId  authenticated customer ID
     * @return AppointmentDto with Razorpay order ID for frontend payment
     */
    @Transactional
    public AppointmentDto book(BookAppointmentRequest request, UUID userId) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        com.queueless.entity.Serviceoffred service = serviceRepository.findById(request.getServiceId())
                .filter(s -> s.getShop().getId().equals(shop.getId()) && s.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ServiceProvider provider = null;
        if (request.getProviderId() != null) {
            provider = serviceProviderRepository.findById(request.getProviderId())
                    .filter(p -> p.getShop().getId().equals(shop.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Service provider not found"));

            if (!provider.isActive() || !provider.isAvailable()) {
                throw new BusinessException("Selected provider is currently unavailable");
            }
            if (!provider.getSupportedServices().isEmpty()
                    && provider.getSupportedServices().stream().noneMatch(s -> s.getId().equals(service.getId()))) {
                throw new BusinessException("This provider does not offer the selected service");
            }
        }

        Instant scheduledAt = resolveScheduledAt(
                request.getScheduledAt(),
                service.getDurationMins(),
                shop.getId(),
                provider);
        validateAppointmentWindow(shop, scheduledAt, service.getDurationMins());
        Instant endTime = scheduledAt.plusSeconds(service.getDurationMins() * 60L);
        long conflicts = countConflictingSlots(shop.getId(), provider, scheduledAt, endTime);

        if (conflicts > 0) {
            throw new BusinessException("This time slot is already booked. Please choose another time.");
        }

        BigDecimal amount = service.getPrice();
        String razorpayOrderId = null;

        // Create Razorpay order only if service has a price
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            razorpayOrderId = createRazorpayOrder(amount, userId.toString());
        }

        Appointment appointment = Appointment.builder()
                .shop(shop)
                .user(user)
                .service(service)
                .serviceProvider(provider)
                .scheduledAt(scheduledAt)
                .durationMins(service.getDurationMins())
                .amount(amount)
                .razorpayOrderId(razorpayOrderId)
                .status(amount.compareTo(BigDecimal.ZERO) > 0
                        ? Appointment.AppointmentStatus.PENDING
                        : Appointment.AppointmentStatus.CONFIRMED)
                .paymentStatus(amount.compareTo(BigDecimal.ZERO) > 0
                        ? Appointment.PaymentStatus.PENDING
                        : Appointment.PaymentStatus.PAID)
                .notes(request.getNotes())
                .build();

        appointment = appointmentRepository.save(appointment);
        log.info("Appointment {} booked by user {} at shop {}", appointment.getId(), userId, shop.getId());

        notificationService.sendInAppNotification(
                user.getId(), shop.getId(), null,
                "Appointment Booked",
                String.format("Your appointment at %s is booked for %s",
                        shop.getName(), scheduledAt));

        return toDto(appointment);
    }

    /**
     * Verifies a Razorpay payment signature and confirms the appointment.
     * Uses HMAC-SHA256 to validate the signature.
     *
     * @param appointmentId appointment to confirm
     * @param verifyRequest Razorpay payment verification data
     * @param userId        authenticated user
     * @return updated AppointmentDto
     */
    @Transactional
    public AppointmentDto verifyPaymentAndConfirm(UUID appointmentId,
            PaymentVerifyRequest verifyRequest,
            UUID userId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (!appointment.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your appointment");
        }

        if (appointment.getRazorpayOrderId() == null
                || !appointment.getRazorpayOrderId().equals(verifyRequest.getRazorpayOrderId())) {
            throw new BusinessException("Payment order does not match this appointment");
        }

        // Verify Razorpay signature: HMAC-SHA256(orderId|paymentId, keySecret)
        boolean valid = verifyRazorpaySignature(
                verifyRequest.getRazorpayOrderId(),
                verifyRequest.getRazorpayPaymentId(),
                verifyRequest.getRazorpaySignature());

        if (!valid) {
            appointment.setPaymentStatus(Appointment.PaymentStatus.FAILED);
            appointmentRepository.save(appointment);
            throw new BusinessException("Payment verification failed. Please contact support.");
        }

        appointment.setPaymentId(verifyRequest.getRazorpayPaymentId());
        appointment.setPaymentStatus(Appointment.PaymentStatus.PAID);
        appointment.setStatus(Appointment.AppointmentStatus.CONFIRMED);
        
        // Auto-issue token for the live queue
        com.queueless.entity.Token token = tokenService.issueTokenForAppointment(appointment);
        appointment.setToken(token);
   
        appointment = appointmentRepository.save(appointment);

        notificationService.sendInAppNotification(
                appointment.getUser().getId(), appointment.getShop().getId(), appointment.getToken() != null ? appointment.getToken().getId() : null,
                "Appointment Confirmed",
                "Your appointment at " + appointment.getShop().getName() + " is confirmed."
        );

        return toDto(appointment);
    }

    /**
     * Reschedules an existing appointment to a new time slot.
     *
     * @param appointmentId ID of the appointment to reschedule
     * @param request       new schedule data
     * @param userId        authenticated customer
     * @return updated AppointmentDto
     */
    @Transactional
    public AppointmentDto reschedule(UUID appointmentId, RescheduleRequest request, UUID userId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (!appointment.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your appointment");
        }

        if (appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED
                || appointment.getStatus() == Appointment.AppointmentStatus.COMPLETED) {
            throw new BusinessException("Cannot reschedule a " + appointment.getStatus() + " appointment");
        }

        // Validate new slot availability
        validateAppointmentWindow(appointment.getShop(), request.getNewScheduledAt(), appointment.getDurationMins());
        Instant endTime = request.getNewScheduledAt()
                .plusSeconds(appointment.getDurationMins() * 60L);
        long conflicts = countConflictingSlots(
                appointment.getShop().getId(),
                appointment.getServiceProvider(),
                request.getNewScheduledAt(),
                endTime);

        if (conflicts > 0) {
            throw new BusinessException("The new time slot is not available");
        }

        appointment.setScheduledAt(request.getNewScheduledAt());
        appointment.setStatus(Appointment.AppointmentStatus.RESCHEDULED);
        appointment = appointmentRepository.save(appointment);

        log.info("Appointment {} rescheduled to {}", appointmentId, request.getNewScheduledAt());
        return toDto(appointment);
    }

    /**
     * Cancels an appointment and initiates a refund if payment was made.
     *
     * @param appointmentId ID of appointment
     * @param userId        authenticated user (customer or shop owner)
     * @param reason        cancellation reason
     * @return updated AppointmentDto
     */
    @Transactional
    public AppointmentDto cancel(UUID appointmentId, UUID userId, String reason) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        boolean isCustomer = appointment.getUser().getId().equals(userId);
        boolean isOwner = appointment.getShop().getOwner().getId().equals(userId);

        if (!isCustomer && !isOwner) {
            throw new AccessDeniedException("Not authorized to cancel this appointment");
        }

        if (appointment.getStatus() == Appointment.AppointmentStatus.CANCELLED) {
            throw new BusinessException("Appointment is already cancelled");
        }

        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(Instant.now());
        appointment.setCancelReason(reason);

        // Initiate refund if payment was made
        if (appointment.getPaymentStatus() == Appointment.PaymentStatus.PAID
                && appointment.getPaymentId() != null) {
            initiateRazorpayRefund(appointment);
            appointment.setPaymentStatus(Appointment.PaymentStatus.REFUNDED);
        }

        appointment = appointmentRepository.save(appointment);
        log.info("Appointment {} cancelled", appointmentId);
        return toDto(appointment);
    }

    /**
     * Retrieves paginated appointment history for a customer.
     */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentDto> getUserAppointments(UUID userId, int page, int size) {
        Page<Appointment> apptPage = appointmentRepository
                .findByUserIdOrderByScheduledAtDesc(userId, PageRequest.of(page, size));

        return PageResponse.<AppointmentDto>builder()
                .content(apptPage.getContent().stream().map(this::toDto).collect(Collectors.toList()))
                .page(page)
                .size(size)
                .totalElements(apptPage.getTotalElements())
                .totalPages(apptPage.getTotalPages())
                .last(apptPage.isLast())
                .build();
    }

    /**
     * Retrieves paginated appointment history for a shop (Owner/Admin only).
     */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentDto> getShopAppointments(UUID shopId, UUID ownerId, int page, int size) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        if (!shop.getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Not authorized to view these appointments");
        }

        Page<Appointment> apptPage = appointmentRepository
                .findByShopIdOrderByScheduledAtAsc(shopId, PageRequest.of(page, size));

        return PageResponse.<AppointmentDto>builder()
                .content(apptPage.getContent().stream().map(this::toDto).collect(Collectors.toList()))
                .page(page)
                .size(size)
                .totalElements(apptPage.getTotalElements())
                .totalPages(apptPage.getTotalPages())
                .last(apptPage.isLast())
                .build();
    }

    /**
     * Marks an appointment as COMPLETED and awards loyalty points.
     */
    @Transactional
    public AppointmentDto completeAppointment(UUID appointmentId, UUID ownerId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (!appointment.getShop().getOwner().getId().equals(ownerId)) {
            throw new AccessDeniedException("Not authorized to complete this appointment");
        }

        if (appointment.getStatus() != Appointment.AppointmentStatus.CONFIRMED
                && appointment.getStatus() != Appointment.AppointmentStatus.RESCHEDULED) {
            throw new BusinessException("Only confirmed or rescheduled appointments can be completed");
        }

        appointment.setStatus(Appointment.AppointmentStatus.COMPLETED);
        appointment = appointmentRepository.save(appointment);

        // Award loyalty points
        loyaltyService.awardPoints(appointment.getUser(), appointment.getShop());

        // Safe async handover: resolve data before background execution
        UUID userId = appointment.getUser().getId();
        String fcmToken = appointment.getUser().getFcmToken();
        String shopName = appointment.getShop().getName();

        notificationService.sendPushNotification(
                userId,
                fcmToken,
                "How was your appointment?",
                String.format("Your appointment at %s is complete. Please leave feedback.", shopName));

        log.info("Appointment {} marked as COMPLETED", appointmentId);
        return toDto(appointment);
    }

    // ——— Razorpay helpers ———

    /**
     * Creates a Razorpay order for the given amount in INR.
     *
     * @param amount    price in rupees
     * @param receiptId unique receipt identifier
     * @return Razorpay order ID string
     */
    private String createRazorpayOrder(BigDecimal amount, String receiptId) {
        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject options = new JSONObject();
            options.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue()); // paise
            options.put("currency", "INR");
            options.put("receipt", "appt_" + receiptId.substring(0, 8));
            options.put("payment_capture", 1);

            Order order = client.orders.create(options);
            return order.get("id");
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new BusinessException("Payment service unavailable. Please try again.");
        }
    }

    /**
     * Verifies the Razorpay payment signature using HMAC-SHA256.
     *
     * @param orderId   Razorpay order ID
     * @param paymentId Razorpay payment ID
     * @param signature signature from Razorpay callback
     * @return true if signature is valid
     */
    private boolean verifyRazorpaySignature(String orderId, String paymentId, String signature) {
        try {
            if (isNonProductionProfile()
                    && ("simulated_signature".equals(signature) || "simulated_sig_for_dev_mode".equals(signature))) {
                return true;
            }
            String data = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString().equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private boolean isNonProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev")
                        || profile.equalsIgnoreCase("test")
                        || profile.equalsIgnoreCase("local"));
    }

    /**
     * Initiates a full refund for a paid appointment via Razorpay.
     */
    private void initiateRazorpayRefund(Appointment appointment) {
        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject options = new JSONObject();
            options.put("amount", appointment.getAmount()
                    .multiply(BigDecimal.valueOf(100)).intValue());
            options.put("speed", "normal");
            options.put("notes", new JSONObject().put("reason", appointment.getCancelReason()));

            client.payments.refund(appointment.getPaymentId(), options);
            log.info("Refund initiated for appointment {}", appointment.getId());
        } catch (RazorpayException e) {
            log.error("Razorpay refund failed for appointment {}: {}", appointment.getId(), e.getMessage());
        }
    }

    private long countConflictingSlots(UUID shopId, ServiceProvider provider, Instant start, Instant end) {
        Instant graceTime = Instant.now().minusSeconds(15 * 60); // 15-minute checkout grace period
        if (provider != null) {
            return appointmentRepository.countConflictingSlotsForProvider(
                    shopId, provider.getId(), start, end, graceTime);
        }

        return appointmentRepository.countConflictingSlots(shopId, start, end, graceTime);
    }

    private Instant resolveScheduledAt(Instant requestedStart,
            int durationMins,
            UUID shopId,
            ServiceProvider provider) {
        Instant candidate = requestedStart;
        Instant candidateEnd = candidate.plusSeconds(durationMins * 60L);

        if (provider == null) {
            return candidate;
        }

        for (int i = 0; i < 24; i++) {
            long conflicts = countConflictingSlots(shopId, provider, candidate, candidateEnd);
            if (conflicts == 0) {
                return candidate;
            }

            candidate = candidate.plusSeconds(durationMins * 60L);
            candidateEnd = candidate.plusSeconds(durationMins * 60L);
        }

        throw new BusinessException("No provider slots are available in the selected time range");
    }

    private void validateAppointmentWindow(Shop shop, Instant start, int durationMins) {
        if (start.isBefore(Instant.now())) {
            throw new BusinessException("Appointment time must be in the future");
        }

        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.ZonedDateTime localStart = start.atZone(zone);
        java.time.ZonedDateTime localEnd = start.plusSeconds(durationMins * 60L).atZone(zone);
        java.time.LocalDate date = localStart.toLocalDate();
        java.time.LocalTime startTime = localStart.toLocalTime();
        java.time.LocalTime endTime = localEnd.toLocalTime();

        if (!localEnd.toLocalDate().equals(date)) {
            throw new BusinessException("Appointment cannot cross midnight");
        }

        if (shop.getClosedDays() != null && shop.getClosedDays().contains(date.getDayOfWeek())) {
            throw new BusinessException("Shop is closed on " + date.getDayOfWeek());
        }

        shopHolidayRepository.findByShopIdAndDate(shop.getId(), date)
                .ifPresent(holiday -> {
                    throw new BusinessException("Shop is closed on selected date: "
                            + (holiday.getReason() != null ? holiday.getReason() : "Holiday"));
                });

        if (startTime.isBefore(shop.getOpenTime()) || endTime.isAfter(shop.getCloseTime())) {
            throw new BusinessException("Appointment must be within shop hours: "
                    + shop.getOpenTime() + " - " + shop.getCloseTime());
        }

        if (shop.getBreakStartTime() != null && shop.getBreakEndTime() != null
                && startTime.isBefore(shop.getBreakEndTime())
                && endTime.isAfter(shop.getBreakStartTime())) {
            throw new BusinessException("Appointment overlaps shop break time");
        }
    }

    private AppointmentDto toDto(Appointment a) {
        return AppointmentDto.builder()
                .id(a.getId())
                .shopId(a.getShop().getId())
                .shopName(a.getShop().getName())
                .serviceId(a.getService().getId())
                .serviceName(a.getService().getName())
                .providerId(a.getServiceProvider() != null ? a.getServiceProvider().getId() : null)
                .providerName(a.getServiceProvider() != null ? a.getServiceProvider().getUser().getName() : null)
                .scheduledAt(a.getScheduledAt())
                .durationMins(a.getDurationMins())
                .status(a.getStatus())
                .paymentStatus(a.getPaymentStatus())
                .amount(a.getAmount())
                .razorpayOrderId(a.getRazorpayOrderId())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }
}

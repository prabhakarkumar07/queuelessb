package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.*;
import com.queueless.entity.Token.TokenStatus;
import com.queueless.exception.*;
import com.queueless.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Core service for queue token management.
 * Handles token issuance, calling next, skipping, cancellation, and live queue
 * state.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final TokenRepository tokenRepository;
    private final ShopRepository shopRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final ServiceProviderRepository providerRepository;
    private final ShopHolidayRepository shopHolidayRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationService notificationService;
    private final QueueWebSocketService webSocketService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LoyaltyService loyaltyService;
    private final JdbcTemplate jdbcTemplate;
    private final AIPredictionService aiPredictionService;

    @Value("${queue.sms-alert-threshold}")
    private int smsAlertThreshold;

    @Value("${queue.max-tokens-per-day}")
    private int maxTokensPerDay;

    private static final String QUEUE_LOCK_PREFIX = "queue:lock:";
    private static final String TOKEN_SEQ_PREFIX = "queue:seq:";

    /**
     * Issues a new digital token for a customer at a shop.
     * Validates shop is open, queue is active, and customer doesn't already have an
     * active token.
     *
     * @param shopId    ID of the shop
     * @param userId    ID of the requesting customer
     * @param serviceId optional service selection
     * @param notes     optional customer notes
     * @return TokenDto with token number, position, and estimated wait
     */
    @Transactional
    public TokenDto getToken(UUID shopId, UUID userId, UUID serviceId, UUID providerId, String notes) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found: " + shopId));

        if (!shop.isActive()) {
            throw new BusinessException("Shop is not active");
        }
        if (shop.isQueuePaused()) {
            throw new BusinessException("Queue is currently paused. Please try again later.");
        }

        LocalDate today = LocalDate.now();
        java.time.LocalTime now = java.time.LocalTime.now();

        if (shop.getClosedDays() != null && shop.getClosedDays().contains(today.getDayOfWeek())) {
            throw new BusinessException("Shop is closed today");
        }

        if (now.isBefore(shop.getOpenTime()) || !now.isBefore(shop.getCloseTime())) {
            throw new BusinessException("Shop is currently closed. Open hours: "
                    + shop.getOpenTime() + " - " + shop.getCloseTime());
        }

        // Block on holidays
        shopHolidayRepository.findByShopIdAndDate(shopId, today)
                .ifPresent(h -> {
                    throw new BusinessException("Shop is closed today: " +
                            (h.getReason() != null ? h.getReason() : "Holiday"));
                });

        // Block during break time
        if (shop.getBreakStartTime() != null && shop.getBreakEndTime() != null) {
            if (!now.isBefore(shop.getBreakStartTime()) && now.isBefore(shop.getBreakEndTime())) {
                throw new BusinessException("Shop is currently on break. Queue resumes at "
                        + shop.getBreakEndTime());
            }
        }

        // Check customer doesn't already have an active token at this shop today
        tokenRepository.findActiveTokenForUserAtShop(userId, shopId, today)
                .ifPresent(t -> {
                    throw new BusinessException("You already have an active token: " + t.getDisplayNumber());
                });

        // Check daily limit
        int todayCount = tokenRepository.findMaxTokenNumberForShopToday(shopId, today);
        if (todayCount >= maxTokensPerDay) {
            throw new BusinessException("Today's token limit reached. Please visit tomorrow.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Serviceoffred service = null;
        if (serviceId != null) {
            service = serviceRepository.findById(serviceId)
                    .filter(s -> s.getShop().getId().equals(shopId))
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        }
        final Serviceoffred selectedService = service;

        ServiceProvider provider = null;
        if (providerId != null) {
            provider = providerRepository.findById(providerId)
                    .filter(p -> p.getShop().getId().equals(shopId))
                    .orElseThrow(() -> new ResourceNotFoundException("Service Provider not found"));

            if (!provider.isActive() || !provider.isAvailable()) {
                throw new BusinessException("Selected provider is currently unavailable");
            }
            if (selectedService != null
                    && !provider.getSupportedServices().isEmpty()
                    && provider.getSupportedServices().stream()
                            .noneMatch(s -> s.getId().equals(selectedService.getId()))) {
                throw new BusinessException("This provider does not support the selected service");
            }
        }

        // Generate next token number atomically in the database.
        int tokenNumber = generateNextTokenNumber(shopId);
        String displayNumber = generateDisplayNumber(tokenNumber);

        // Calculate queue position
        List<Token> activeQueue = tokenRepository.findActiveQueueByShopAndDate(shopId, today);
        if (activeQueue.size() >= shop.getMaxQueueSize()) {
            throw new BusinessException("Queue is full. Please try again later.");
        }
        int queuePosition = activeQueue.size() + 1;
        int staticEstimatedWait = (queuePosition - 1) * shop.getAvgServiceMins();
        
        // AI dynamic wait time prediction
        List<Token> recentHistory = tokenRepository.findRecentServedTokens(shopId, PageRequest.of(0, 5)).getContent();
        int estimatedWait = aiPredictionService.predictWaitTime(shop, null, queuePosition - 1, recentHistory);

        Token token = Token.builder()
                .shop(shop)
                .user(user)
                .service(service)
                .serviceProvider(provider)
                .tokenNumber(tokenNumber)
                .displayNumber(displayNumber)
                .status(TokenStatus.WAITING)
                .queuePosition(queuePosition)
                .dateIssued(today)
                .notes(notes)
                .build();

        token = tokenRepository.save(token);
        log.info("Token {} issued to user {} at shop {}", displayNumber, userId, shopId);

        // Async compute no-show probability
        aiPredictionService.computeAndSaveNoShowProbabilityAsync(token.getId(), token.getSnoozeCount(), token.getRejoinCount(), estimatedWait);

        // Broadcast queue update via WebSocket
        broadcastQueueUpdate(shopId, "TOKEN_ISSUED");

        List<Token> updatedQueue = tokenRepository.findActiveQueueByShopAndDate(shopId, today)
                .stream()
                .sorted(this::compareQueueOrder)
                .collect(Collectors.toList());
        int lanes = availableServiceLanes(shop);
        int cumulativeWorkMins = 0;
        TokenDto dto = toDto(token, queuePosition, estimatedWait);
        for (int i = 0; i < updatedQueue.size(); i++) {
            Token queued = updatedQueue.get(i);
            int est = estimateWaitForToken(queued, cumulativeWorkMins, lanes);
            if (queued.getId().equals(token.getId())) {
                dto = toDto(token, i + 1, est);
                queuePosition = i + 1;
                estimatedWait = est;
                break;
            }
            if (isActiveQueueStatus(queued.getStatus())) {
                cumulativeWorkMins += serviceDurationMins(queued);
            }
        }

        // Send confirmation notification
        notificationService.sendInAppNotification(
                token.getUser().getId(), token.getShop().getId(), token.getId(),
                "Joined Queue",
                "You have joined the queue at " + shop.getName() + " with token " + token.getDisplayNumber()
        );

        return dto;
    }

    @Transactional
    public TokenDto createWalkInToken(CreateWalkInTokenRequest request, UUID actorId) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found: " + request.getShopId()));
        validateQueueOperator(shop, actorId);

        User customer = resolveWalkInCustomer(request.getCustomerName(), request.getCustomerPhone());
        String notes = normalizeWalkInNotes(request.getNotes());

        TokenDto token = getToken(
                shop.getId(),
                customer.getId(),
                request.getServiceId(),
                request.getProviderId(),
                notes);
        log.info("Walk-in token {} issued by {} for shop {}", token.getDisplayNumber(), actorId, shop.getId());
        return token;
    }

    @Transactional
    public Token issueTokenForAppointment(Appointment appointment) {
        Shop shop = appointment.getShop();
        LocalDate today = LocalDate.now();

        tokenRepository.findActiveTokenForUserAtShop(appointment.getUser().getId(), shop.getId(), today)
                .ifPresent(t -> {
                    throw new BusinessException("You already have an active token: " + t.getDisplayNumber());
                });

        int tokenNumber = generateNextTokenNumber(shop.getId());
        String displayNumber = generateDisplayNumber(tokenNumber);
        List<Token> activeQueue = tokenRepository.findActiveQueueByShopAndDate(shop.getId(), today);
        int queuePosition = activeQueue.size() + 1;

        Token token = Token.builder()
                .shop(shop)
                .user(appointment.getUser())
                .service(appointment.getService())
                .serviceProvider(appointment.getServiceProvider())
                .tokenNumber(tokenNumber)
                .displayNumber(displayNumber)
                .status(TokenStatus.WAITING)
                .queuePosition(queuePosition)
                .dateIssued(today)
                .notes("Appointment " + appointment.getId())
                .build();

        token = tokenRepository.save(token);
        broadcastQueueUpdate(shop.getId(), "TOKEN_ISSUED");
        return token;
    }

    /**
     * Calls the next waiting token in the queue (shop owner action).
     * Marks current serving token as SERVED, then advances to the next WAITING
     * token.
     *
     * @param shopId  ID of the shop
     * @param ownerId ID of the shop owner making the call
     * @return TokenDto of the newly called token
     */
    @Transactional
    public TokenDto callNext(UUID shopId, UUID actorId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        boolean isOwner = shop.getOwner().getId().equals(actorId);
        ServiceProvider provider = null;
        if (!isOwner) {
            provider = providerRepository.findByUserId(actorId)
                    .filter(p -> p.getShop().getId().equals(shopId))
                    .orElseThrow(() -> new AccessDeniedException("Not authorized for this shop"));
        }

        final UUID providerId = (provider != null) ? provider.getId() : null;

        // Mark current SERVING/CALLED token as SERVED for this specific provider
        List<Token> currentlyServing = tokenRepository
                .findByShopIdAndDateIssuedAndStatus(shopId, LocalDate.now(), TokenStatus.SERVING)
                .stream()
                .filter(t -> (providerId == null)
                        || (t.getServiceProvider() != null && t.getServiceProvider().getId().equals(providerId)))
                .collect(Collectors.toList());
        currentlyServing.forEach(t -> {
            t.setStatus(TokenStatus.SERVED);
            t.setServedAt(Instant.now());
        });
        if (!currentlyServing.isEmpty()) {
            tokenRepository.saveAll(currentlyServing);
        }

        // Also mark CALLED as SKIPPED if shop calls next without serving
        List<Token> calledTokens = tokenRepository
                .findByShopIdAndDateIssuedAndStatus(shopId, LocalDate.now(), TokenStatus.CALLED)
                .stream()
                .filter(t -> (providerId == null)
                        || (t.getServiceProvider() != null && t.getServiceProvider().getId().equals(providerId)))
                .collect(Collectors.toList());
        calledTokens.forEach(t -> {
            t.setStatus(TokenStatus.SKIPPED);
            t.setSkippedAt(Instant.now());
        });
        if (!calledTokens.isEmpty()) {
            tokenRepository.saveAll(calledTokens);
        }

        // Find next waiting token
        Token nextToken;
        if (providerId != null) {
            nextToken = tokenRepository.findNextWaitingTokenForProvider(shopId, LocalDate.now(), providerId)
                    .orElseThrow(() -> new BusinessException("No more tokens in queue for you"));

            // Assign the token to this provider if it was an "any provider" token
            if (nextToken.getServiceProvider() == null) {
                nextToken.setServiceProvider(provider);
            }
        } else {
            nextToken = tokenRepository.findNextWaitingToken(shopId, LocalDate.now())
                    .orElseThrow(() -> new BusinessException("No more tokens in queue"));
        }

        nextToken.setStatus(TokenStatus.CALLED);
        nextToken.setCalledAt(Instant.now());
        nextToken = tokenRepository.save(nextToken);

        log.info("Token {} called at shop {}", nextToken.getDisplayNumber(), shopId);

        // Check and send SMS alerts for tokens now 3 positions away
        checkAndSendSmsAlerts(shopId, nextToken.getTokenNumber());

        // Broadcast update
        broadcastQueueUpdate(shopId, "TOKEN_CALLED");

        // Notify the called customer
        notificationService.sendPushNotification(
                nextToken.getUser().getId(),
                nextToken.getUser().getFcmToken(),
                "Your Turn!",
                String.format("Token %s — it's your turn at %s. Please proceed!",
                        nextToken.getDisplayNumber(), shop.getName()));

        long tokensAhead = tokenRepository.countTokensAhead(shopId, LocalDate.now(), nextToken.getTokenNumber());
        return toDto(nextToken, 1, 0);
    }

    /**
     * Skips a specific token (customer no-show or shop action).
     *
     * @param tokenId ID of the token to skip
     * @param actorId ID of user performing the skip (must be owner or the customer)
     * @return updated TokenDto
     */
    @Transactional
    public TokenDto skipToken(UUID tokenId, UUID actorId) {
        return skipToken(tokenId, actorId, null);
    }

    @Transactional
    public TokenDto skipToken(UUID tokenId, UUID actorId, String reason) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getStatus() != TokenStatus.WAITING && token.getStatus() != TokenStatus.CALLED) {
            throw new BusinessException("Token cannot be skipped in status: " + token.getStatus());
        }

        Shop shop = token.getShop();
        boolean isOwner = shop.getOwner().getId().equals(actorId);
        boolean isCustomer = token.getUser().getId().equals(actorId);
        boolean isAssignedProvider = isAssignedProviderForShop(shop, actorId);

        if (!isOwner && !isCustomer && !isAssignedProvider) {
            throw new AccessDeniedException("Not authorized to skip this token");
        }

        token.setStatus(TokenStatus.SKIPPED);
        token.setSkippedAt(Instant.now());
        appendTokenNote(token, normalizeActionReason(reason, "Skipped by operator"));
        token = tokenRepository.save(token);

        broadcastQueueUpdate(shop.getId(), "TOKEN_SKIPPED");
        log.info("Token {} skipped", token.getDisplayNumber());
        
        autoPromoteFromWaitlist(shop.getId());

        return toDto(token, null, null);
    }

    /**
     * Cancels a token. Customers can cancel their own; owners can cancel any.
     *
     * @param tokenId ID of the token
     * @param actorId user performing cancellation
     * @return updated TokenDto
     */
    @Transactional
    public TokenDto cancelToken(UUID tokenId, UUID actorId) {
        return cancelToken(tokenId, actorId, null);
    }

    @Transactional
    public TokenDto cancelToken(UUID tokenId, UUID actorId, String reason) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        if (token.getStatus() == TokenStatus.SERVED || token.getStatus() == TokenStatus.CANCELLED) {
            throw new BusinessException("Token is already " + token.getStatus());
        }

        Shop shop = token.getShop();
        boolean isOwner = shop.getOwner().getId().equals(actorId);
        boolean isCustomer = token.getUser().getId().equals(actorId);

        if (!isOwner && !isCustomer) {
            throw new AccessDeniedException("Not authorized to cancel this token");
        }

        token.setStatus(TokenStatus.CANCELLED);
        token.setCancelledAt(Instant.now());
        appendTokenNote(token, normalizeActionReason(reason, "Cancelled by operator"));
        token = tokenRepository.save(token);

        broadcastQueueUpdate(shop.getId(), "TOKEN_CANCELLED");
        log.info("Token {} cancelled by {}", token.getDisplayNumber(), actorId);

        autoPromoteFromWaitlist(shop.getId());

        return toDto(token, null, null);
    }

    /**
     * Returns the live queue state for a shop including all waiting tokens and
     * stats.
     *
     * @param shopId ID of the shop
     * @return LiveQueueDto with all active tokens and queue metadata
     */
    @Transactional(readOnly = true)
    public LiveQueueDto getLiveQueue(UUID shopId) {
        return getLiveQueue(shopId, false);
    }

    @Transactional(readOnly = true)
    public LiveQueueDto getOperatorLiveQueue(UUID shopId, UUID actorId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        if (shop.getOwner().getId().equals(actorId)) {
            return buildLiveQueue(shop, true, null, true);
        }

        ServiceProvider provider = providerRepository.findByUserId(actorId)
                .filter(ServiceProvider::isActive)
                .filter(p -> p.getShop().getId().equals(shopId))
                .orElseThrow(() -> new AccessDeniedException("You are not allowed to manage this queue"));

        return buildLiveQueue(shop, true, provider.getId(), false);
    }

    private LiveQueueDto getLiveQueue(UUID shopId, boolean includeCustomerDetails) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        return buildLiveQueue(shop, includeCustomerDetails, null, false);
    }

    private LiveQueueDto buildLiveQueue(Shop shop,
            boolean includeCustomerDetails,
            UUID visibleProviderId,
            boolean includePhone) {
        UUID shopId = shop.getId();
        LocalDate today = LocalDate.now();
        List<Token> activeQueue = tokenRepository.findActiveQueueByShopAndDate(shopId, today)
                .stream()
                .filter(t -> visibleProviderId == null
                        || t.getServiceProvider() == null
                        || t.getServiceProvider().getId().equals(visibleProviderId))
                .sorted(this::compareQueueOrder)
                .collect(Collectors.toList());

        List<Object[]> stats = tokenRepository.countByStatusForShopToday(shopId, today);
        long servedCount = 0;
        for (Object[] row : stats) {
            if (TokenStatus.SERVED.name().equals(row[0].toString())) {
                servedCount = (long) row[1];
            }
        }

        // Find currently active front-of-queue token for TV display
        String currentTokenDisplay = activeQueue.stream()
                .filter(t -> t.getStatus() == TokenStatus.CALLED || t.getStatus() == TokenStatus.ARRIVED
                        || t.getStatus() == TokenStatus.SERVING)
                .findFirst()
                .map(Token::getDisplayNumber)
                .orElse("—");

        List<TokenDto> waitingDtos = new ArrayList<>();
        int position = 1;
        int lanes = visibleProviderId != null ? 1 : availableServiceLanes(shop);
        int cumulativeWorkMins = 0;
        for (Token t : activeQueue) {
            int estWait = estimateWaitForToken(t, cumulativeWorkMins, lanes);
            waitingDtos.add(toDto(t, position, estWait, includeCustomerDetails, includePhone));
            if (isActiveQueueStatus(t.getStatus())) {
                cumulativeWorkMins += serviceDurationMins(t);
            }
            position++;
        }

        return LiveQueueDto.builder()
                .shopId(shopId)
                .shopName(shop.getName())
                .queuePaused(shop.isQueuePaused())
                .totalWaiting(activeQueue.size())
                .totalServedToday((int) servedCount)
                .avgServiceMins(shop.getAvgServiceMins())
                .currentTokenDisplay(currentTokenDisplay)
                .waitingTokens(waitingDtos)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Gets a customer's token history with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<TokenDto> getUserTokenHistory(UUID userId, int page, int size) {
        Page<Token> tokenPage = tokenRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));

        List<TokenDto> dtos = tokenPage.getContent().stream()
                .map(t -> toDto(t, null, null))
                .collect(Collectors.toList());

        return PageResponse.<TokenDto>builder()
                .content(dtos)
                .page(page)
                .size(size)
                .totalElements(tokenPage.getTotalElements())
                .totalPages(tokenPage.getTotalPages())
                .last(tokenPage.isLast())
                .build();
    }

    /**
     * Marks a token as ARRIVED (customer physically checked in at the counter).
     */
    @Transactional
    public TokenDto markArrived(UUID tokenId, UUID ownerId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        validateQueueOperator(token.getShop(), ownerId);

        if (token.getStatus() != TokenStatus.CALLED && token.getStatus() != TokenStatus.WAITING) {
            throw new BusinessException("Only waiting or called tokens can be marked arrived");
        }

        token.setStatus(TokenStatus.ARRIVED);
        appendTokenNote(token, "Customer checked in at counter");
        token = tokenRepository.save(token);
        broadcastQueueUpdate(token.getShop().getId(), "TOKEN_ARRIVED");
        return toDto(token, null, null);
    }

    /**
     * Marks a token as SERVING (service has started).
     */
    @Transactional
    public TokenDto markServing(UUID tokenId, UUID ownerId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        validateQueueOperator(token.getShop(), ownerId);

        if (token.getStatus() != TokenStatus.CALLED && token.getStatus() != TokenStatus.ARRIVED) {
            throw new BusinessException("Token must be in CALLED or ARRIVED status to mark as SERVING");
        }

        token.setStatus(TokenStatus.SERVING);
        token = tokenRepository.save(token);
        broadcastQueueUpdate(token.getShop().getId(), "TOKEN_SERVING");
        return toDto(token, null, null);
    }

    /**
     * Marks a token as SERVED (completed).
     */
    @Transactional
    public TokenDto completeToken(UUID tokenId, UUID ownerId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        validateQueueOperator(token.getShop(), ownerId);

        if (token.getStatus() != TokenStatus.CALLED && token.getStatus() != TokenStatus.ARRIVED
                && token.getStatus() != TokenStatus.SERVING) {
            throw new BusinessException("Token must be in CALLED, ARRIVED, or SERVING status to be completed");
        }

        token.setStatus(TokenStatus.SERVED);
        token.setServedAt(Instant.now());
        token = tokenRepository.save(token);

        // Award loyalty points
        loyaltyService.awardPoints(token.getUser(), token.getShop());

        notificationService.sendPushNotification(
                token.getUser().getId(),
                token.getUser().getFcmToken(),
                "How was your visit?",
                String.format("Your service at %s is complete. Please leave feedback when you have a moment.",
                        token.getShop().getName()));

        broadcastQueueUpdate(token.getShop().getId(), "TOKEN_SERVED");
        
        autoPromoteFromWaitlist(token.getShop().getId());
        
        return toDto(token, null, null);
    }

    // ——— Private Helpers ———

    /**
     * Atomically generates the next token sequence number using the database.
     * Redis is only updated as a best-effort cache so token allocation remains safe
     * across app restarts, Redis loss, and multiple backend instances.
     */
    @Transactional
    public TokenDto rejoinSkippedToken(UUID tokenId, UUID actorId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));
        if (!token.getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("Only the token owner can rejoin");
        }
        if (token.getStatus() != TokenStatus.SKIPPED) {
            throw new BusinessException("Only skipped tokens can rejoin");
        }
        Shop shop = token.getShop();
        if (token.getRejoinCount() >= shop.getMaxRejoins()) {
            throw new BusinessException("Rejoin limit reached for this shop");
        }
        if (token.getSkippedAt() == null
                || token.getSkippedAt().plusSeconds(shop.getRejoinWindowMins() * 60L).isBefore(Instant.now())) {
            throw new BusinessException("Rejoin window has expired");
        }

        token.setStatus(TokenStatus.WAITING);
        token.setRejoinCount(token.getRejoinCount() + 1);
        token.setQueuePosition(tokenRepository.findActiveQueueByShopAndDate(shop.getId(), LocalDate.now()).size() + 1);
        token = tokenRepository.save(token);
        broadcastQueueUpdate(shop.getId(), "TOKEN_REJOINED");
        return toDto(token, token.getQueuePosition(), serviceDurationMins(token));
    }

    @Transactional
    public void autoPromoteFromWaitlist(UUID shopId) {
        Shop shop = shopRepository.findById(shopId).orElseThrow();
        LocalDate today = LocalDate.now();
        List<Token> activeQueue = tokenRepository.findActiveQueueByShopAndDate(shopId, today);
        if (activeQueue.size() >= shop.getMaxQueueSize()) {
            return;
        }

        int spots = shop.getMaxQueueSize() - activeQueue.size();
        List<Waitlist> waitingList = waitlistRepository.findTopWaiting(shopId, spots);
        
        for (Waitlist w : waitingList) {
            try {
                TokenDto dto = getToken(shopId, w.getUser().getId(), w.getService() != null ? w.getService().getId() : null, null, "Auto-promoted from waitlist");
                w.setStatus(Waitlist.WaitlistStatus.JOINED);
                w.setNotifiedAt(Instant.now());
                waitlistRepository.save(w);

                notificationService.sendPushNotification(
                        w.getUser().getId(),
                        w.getUser().getFcmToken(),
                        "Auto-Promoted to Queue!",
                        "You've been automatically promoted from the waitlist at " + shop.getName() + 
                        ". Your token is " + dto.getDisplayNumber() + "."
                );
            } catch (Exception e) {
                log.error("Failed to auto-promote user {} from waitlist: {}", w.getUser().getId(), e.getMessage());
            }
        }
    }

    /**
     * Snoozes a token, pushing it back in the queue (customer or operator action).
     * Increases the sort_penalty to move the token 3 positions behind in the
     * WAITING list.
     */
    @Transactional
    public TokenDto snoozeToken(UUID tokenId, UUID actorId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));

        Shop shop = token.getShop();
        boolean isOwner = shop.getOwner().getId().equals(actorId);
        boolean isStaff = providerRepository.findByUserId(actorId)
                .filter(p -> p.getShop().getId().equals(shop.getId()))
                .isPresent();

        if (!token.getUser().getId().equals(actorId) && !isOwner && !isStaff) {
            throw new AccessDeniedException("Not authorized to snooze this token");
        }

        if (token.getStatus() != TokenStatus.CALLED && token.getStatus() != TokenStatus.WAITING) {
            throw new BusinessException("Only waiting or called tokens can be snoozed");
        }

        // Logic: Push back by 3 spots.
        List<Token> activeQueue = tokenRepository.findActiveQueueByShopAndDate(shop.getId(), LocalDate.now());
        int myIndex = -1;
        for (int i = 0; i < activeQueue.size(); i++) {
            if (activeQueue.get(i).getId().equals(token.getId())) {
                myIndex = i;
                break;
            }
        }

        if (myIndex != -1 && myIndex + 3 < activeQueue.size()) {
            Token targetToken = activeQueue.get(myIndex + 3);
            int newSortValue = targetToken.getTokenNumber() + targetToken.getSortPenalty() + 1;
            token.setSortPenalty(newSortValue - token.getTokenNumber());
        } else {
            // If less than 3 tokens left or at the end, just push to the end
            token.setSortPenalty(token.getSortPenalty() + 50);
        }

        token.setStatus(TokenStatus.WAITING); // Reset status if it was CALLED
        token.setSnoozeCount(token.getSnoozeCount() + 1);
        token = tokenRepository.save(token);

        // Recalculate AI no-show probability
        long aheadWait = tokenRepository.countTokensAhead(shop.getId(), LocalDate.now(), token.getTokenNumber() + token.getSortPenalty());
        aiPredictionService.computeAndSaveNoShowProbabilityAsync(token.getId(), token.getSnoozeCount(), token.getRejoinCount(), (int)(aheadWait * shop.getAvgServiceMins()));

        broadcastQueueUpdate(shop.getId(), "TOKEN_SNOOZED");

        // Recalculate position
        long ahead = tokenRepository.countTokensAhead(shop.getId(), LocalDate.now(),
                token.getTokenNumber() + token.getSortPenalty());
        return toDto(token, (int) ahead + 1, (int) (ahead * shop.getAvgServiceMins()));
    }

    private int generateNextTokenNumber(UUID shopId) {
        LocalDate today = LocalDate.now();
        String key = TOKEN_SEQ_PREFIX + shopId + ":" + today;

        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
                int dbMax = tokenRepository.findMaxTokenNumberForShopToday(shopId, today);
                Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, dbMax);
                if (Boolean.TRUE.equals(isNew)) {
                    redisTemplate.expire(key, 26, TimeUnit.HOURS);
                }
            }

            Long next = redisTemplate.opsForValue().increment(key);
            if (next != null) {
                // Best-effort async update to token_sequences table could be done here, 
                // but the tokens table itself maintains the truth via max(token_number)
                return next.intValue();
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for token sequence, falling back to DB: {}", e.getMessage());
        }

        Integer nextDb = jdbcTemplate.queryForObject("""
                INSERT INTO token_sequences (shop_id, date, last_number, prefix)
                VALUES (?, ?, 1, 'A')
                ON CONFLICT (shop_id, date)
                DO UPDATE SET last_number = token_sequences.last_number + 1
                RETURNING last_number
                """, Integer.class, shopId, today);

        return nextDb != null ? nextDb : fallbackTokenNumber(shopId);
    }

    private int fallbackTokenNumber(UUID shopId) {
        return tokenRepository.findMaxTokenNumberForShopToday(shopId, LocalDate.now()) + 1;
    }

    /**
     * Generates a human-readable display number (e.g., token 42 → "A042").
     */
    private String generateDisplayNumber(int tokenNumber) {
        char prefix = (char) ('A' + (tokenNumber - 1) / 999);
        int displayNum = ((tokenNumber - 1) % 999) + 1;
        return String.format("%c%03d", prefix, displayNum);
    }

    /**
     * Sends SMS alerts to customers who are now within the threshold distance from
     * their turn.
     */
    private void checkAndSendSmsAlerts(UUID shopId, int currentTokenNumber) {
        List<Token> alertTargets = tokenRepository.findTokensNeedingSmsAlert(
                shopId, LocalDate.now(), currentTokenNumber, smsAlertThreshold);

        for (Token token : alertTargets) {
            long tokensAhead = tokenRepository.countTokensAhead(shopId, LocalDate.now(), token.getTokenNumber());
            notificationService.sendSmsAlert(token.getId(), (int) tokensAhead);
            token.setSmsSent(true);
        }

        if (!alertTargets.isEmpty()) {
            tokenRepository.saveAll(alertTargets);
        }
    }

    /** Publishes a QueueUpdateEvent to the WebSocket broker. */
    public void broadcastQueueUpdate(UUID shopId, String eventType) {
        try {
            LiveQueueDto queue = getLiveQueue(shopId);
            QueueUpdateEvent event = QueueUpdateEvent.builder()
                    .shopId(shopId)
                    .eventType(eventType)
                    .currentToken(queue.getCurrentTokenDisplay())
                    .waitingCount(queue.getTotalWaiting())
                    .waitingTokens(queue.getWaitingTokens())
                    .timestamp(Instant.now())
                    .build();
            webSocketService.broadcastQueueUpdate(shopId, event);
        } catch (Exception e) {
            log.error("Failed to broadcast WebSocket update for shop {}: {}", shopId, e.getMessage());
        }
    }

    /** Validates that the given user is the owner of the shop. */
    private void validateOwnership(Shop shop, UUID userId) {
        if (!shop.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("You are not the owner of this shop");
        }
    }

    /** Validates that the user can operate the live queue for the shop. */
    private void validateQueueOperator(Shop shop, UUID userId) {
        if (!shop.getOwner().getId().equals(userId) && !isAssignedProviderForShop(shop, userId) && !isAdmin(userId)) {
            throw new AccessDeniedException("You are not allowed to manage this queue");
        }
    }

    private boolean isAdmin(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == User.Role.ADMIN)
                .orElse(false);
    }

    private boolean isAssignedProviderForShop(Shop shop, UUID userId) {
        return providerRepository.findByUserId(userId)
                .filter(ServiceProvider::isActive)
                .map(ServiceProvider::getShop)
                .map(assignedShop -> assignedShop.getId().equals(shop.getId()))
                .orElse(false);
    }

    private User resolveWalkInCustomer(String rawName, String rawPhone) {
        String name = rawName != null && !rawName.isBlank() ? rawName.trim() : "Walk-in customer";
        String phone = rawPhone != null && !rawPhone.isBlank() ? rawPhone.trim() : null;

        if (phone != null) {
            Optional<User> existing = userRepository.findByPhone(phone);
            if (existing.isPresent()) {
                User user = existing.get();
                if (user.getRole() != User.Role.CUSTOMER) {
                    throw new BusinessException("This phone number belongs to a staff or owner account");
                }
                if (!user.isActive()) {
                    throw new BusinessException("Customer account is deactivated");
                }
                if ((user.getName() == null || user.getName().isBlank()) && !name.isBlank()) {
                    user.setName(name);
                    return userRepository.save(user);
                }
                return user;
            }
        }

        User customer = User.builder()
                .name(name)
                .phone(phone)
                .role(User.Role.CUSTOMER)
                .provider("WALK_IN")
                .active(true)
                .build();
        return userRepository.save(customer);
    }

    private String normalizeWalkInNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return "Created by staff as walk-in";
        }
        return "Walk-in: " + notes.trim();
    }

    private String normalizeActionReason(String reason, String fallback) {
        return reason != null && !reason.isBlank() ? reason.trim() : fallback;
    }

    private void appendTokenNote(Token token, String note) {
        String previousNotes = token.getNotes();
        token.setNotes((previousNotes == null || previousNotes.isBlank())
                ? note
                : previousNotes + "\n" + note);
    }

    private int compareQueueOrder(Token left, Token right) {
        int statusCompare = Integer.compare(statusRank(left), statusRank(right));
        if (statusCompare != 0) {
            return statusCompare;
        }
        int priorityCompare = Integer.compare(priorityRank(left), priorityRank(right));
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return Integer.compare(left.getTokenNumber(), right.getTokenNumber());
    }

    private int statusRank(Token token) {
        if (token.getStatus() == TokenStatus.SERVING)
            return 0;
        if (token.getStatus() == TokenStatus.ARRIVED)
            return 1;
        if (token.getStatus() == TokenStatus.CALLED)
            return 2;
        return 3;
    }

    private boolean isActiveQueueStatus(TokenStatus status) {
        return status == TokenStatus.WAITING
                || status == TokenStatus.CALLED
                || status == TokenStatus.ARRIVED
                || status == TokenStatus.SERVING;
    }

    private int priorityRank(Token token) {
        return switch (token.getPriority()) {
            case EMERGENCY -> 0;
            case VIP -> 1;
            case SENIOR -> 2;
            case PREGNANT -> 3;
            case NORMAL -> 4;
        };
    }

    private int serviceDurationMins(Token token) {
        if (token.getService() != null) {
            return token.getService().getDurationMins();
        }
        try {
            Double historical = jdbcTemplate.queryForObject("""
                    SELECT AVG(avg_service_minutes)
                    FROM daily_shop_stats
                    WHERE shop_id = ? AND stat_date >= CURRENT_DATE - INTERVAL '14 days'
                      AND avg_service_minutes > 0
                    """, Double.class, token.getShop().getId());
            if (historical != null && historical > 0) {
                return (int) Math.max(1, Math.round(historical));
            }
        } catch (Exception e) {
            log.debug("Historical service duration unavailable for shop {}: {}", token.getShop().getId(),
                    e.getMessage());
        }
        return token.getShop().getAvgServiceMins();
    }

    private int availableServiceLanes(Shop shop) {
        long availableProviders = providerRepository.findByShopIdAndActiveTrue(shop.getId())
                .stream()
                .filter(ServiceProvider::isAvailable)
                .count();
        return (int) Math.max(1, availableProviders);
    }

    private int estimateWaitForToken(Token token, int cumulativeWorkMins, int lanes) {
        if (token.getStatus() == TokenStatus.CALLED || token.getStatus() == TokenStatus.ARRIVED
                || token.getStatus() == TokenStatus.SERVING) {
            return 0;
        }
        int effectiveLanes = token.getServiceProvider() != null ? 1 : Math.max(1, lanes);
        return (int) Math.ceil(cumulativeWorkMins / (double) effectiveLanes);
    }

    /**
     * Maps a Token entity to its DTO with computed queue position and wait time.
     */
    private TokenDto toDto(Token token, Integer position, Integer estimatedWait) {
        return TokenDto.builder()
                .id(token.getId())
                .shopId(token.getShop().getId())
                .shopName(token.getShop().getName())
                .userId(token.getUser().getId())
                .userName(token.getUser().getName())
                .userPhone(token.getUser().getPhone())
                .serviceId(token.getService() != null ? token.getService().getId() : null)
                .serviceName(token.getService() != null ? token.getService().getName() : null)
                .providerId(token.getServiceProvider() != null ? token.getServiceProvider().getId() : null)
                .providerName(
                        token.getServiceProvider() != null ? token.getServiceProvider().getUser().getName() : null)
                .tokenNumber(token.getTokenNumber())
                .displayNumber(token.getDisplayNumber())
                .status(token.getStatus())
                .priority(token.getPriority())
                .queuePosition(position)
                .estimatedWaitMins(estimatedWait)
                .issuedAt(token.getIssuedAt())
                .calledAt(token.getCalledAt())
                .servedAt(token.getServedAt())
                .dateIssued(token.getDateIssued())
                .rejoinCount(token.getRejoinCount())
                .snoozeCount(token.getSnoozeCount())
                .noShowProbability(token.getNoShowProbability())
                .skippedAt(token.getSkippedAt())
                .build();
    }

    private TokenDto toDto(Token token,
            Integer position,
            Integer estimatedWait,
            boolean includeCustomerDetails,
            boolean includePhone) {
        TokenDto dto = toDto(token, position, estimatedWait);
        if (!includeCustomerDetails) {
            dto.setUserId(null);
            dto.setUserName(null);
            dto.setUserPhone(null);
        } else if (!includePhone) {
            dto.setUserPhone(null);
        }
        return dto;
    }

    /**
     * Sets the priority of a token. Only shop owners and service providers may do
     * this.
     */
    @Transactional
    public TokenDto setPriority(UUID tokenId, Token.Priority priority, UUID actorId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));
        validateQueueOperator(token.getShop(), actorId);
        token.setPriority(priority);
        token = tokenRepository.save(token);
        broadcastQueueUpdate(token.getShop().getId(), "TOKEN_PRIORITY_UPDATED");
        log.info("Token {} priority set to {} by {}", token.getDisplayNumber(), priority, actorId);
        return toDto(token, null, null);
    }

    @Transactional
    public TokenDto transferToken(UUID tokenId, TransferTokenRequest request, UUID actorId) {
        Token token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new ResourceNotFoundException("Token not found"));
        Shop shop = token.getShop();
        validateQueueOperator(shop, actorId);

        if (token.getStatus() != TokenStatus.WAITING && token.getStatus() != TokenStatus.CALLED) {
            throw new BusinessException("Only waiting or called tokens can be transferred");
        }

        Serviceoffred service = null;
        if (request.getServiceId() != null) {
            service = serviceRepository.findById(request.getServiceId())
                    .filter(s -> s.getShop().getId().equals(shop.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        }
        final Serviceoffred selectedService = service;

        ServiceProvider provider = null;
        if (request.getProviderId() != null) {
            provider = providerRepository.findById(request.getProviderId())
                    .filter(p -> p.getShop().getId().equals(shop.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Service provider not found"));
            if (!provider.isActive() || !provider.isAvailable()) {
                throw new BusinessException("Selected provider is currently unavailable");
            }
            if (selectedService != null
                    && !provider.getSupportedServices().isEmpty()
                    && provider.getSupportedServices().stream().noneMatch(s -> s.getId().equals(selectedService.getId()))) {
                throw new BusinessException("This provider does not support the selected service");
            }
        }

        token.setService(service);
        token.setServiceProvider(provider);
        String reason = request.getReason() != null && !request.getReason().isBlank()
                ? request.getReason().trim()
                : "Transferred by operator";
        String previousNotes = token.getNotes();
        token.setNotes((previousNotes == null || previousNotes.isBlank())
                ? reason
                : previousNotes + "\n" + reason);
        token = tokenRepository.save(token);

        broadcastQueueUpdate(shop.getId(), "TOKEN_TRANSFERRED");
        log.info("Token {} transferred by {}", token.getDisplayNumber(), actorId);
        return toDto(token, null, null);
    }
}

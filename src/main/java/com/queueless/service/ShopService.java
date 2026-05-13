package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.*;
import com.queueless.exception.*;
import com.queueless.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for shop management — CRUD, queue pause/resume, schedule, stats, analytics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShopService {

    private final ShopRepository shopRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceProviderRepository serviceProviderRepository;
    private final TokenRepository tokenRepository;
    private final ShopHolidayRepository shopHolidayRepository;
    private final BusinessAccountRepository businessAccountRepository;
    private final SlugService slugService;
    private final JdbcTemplate jdbcTemplate;

    // ——— Shop CRUD ———

    @Transactional
    public ShopDto createShop(CreateShopRequest request, User owner) {
        Shop shop = Shop.builder()
                .owner(owner)
                .businessAccount(resolveBusinessAccount(request.getBusinessAccountId(), owner.getId()))
                .name(request.getName())
                .category(request.getCategory())
                .description(request.getDescription())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .phone(request.getPhone())
                .logoUrl(request.getLogoUrl())
                .primaryColor(normalizeColor(request.getPrimaryColor()))
                .businessRegistrationNumber(blankToNull(request.getBusinessRegistrationNumber()))
                .branchCode(blankToNull(request.getBranchCode()))
                .verificationStatus(blankToNull(request.getBusinessRegistrationNumber()) != null
                        ? Shop.VerificationStatus.SUBMITTED
                        : Shop.VerificationStatus.PENDING)
                .openTime(parseTimeOrDefault(request.getOpenTime(), LocalTime.of(9, 0)))
                .closeTime(parseTimeOrDefault(request.getCloseTime(), LocalTime.of(18, 0)))
                .breakStartTime(parseOptionalTime(request.getBreakStartTime()))
                .breakEndTime(parseOptionalTime(request.getBreakEndTime()))
                .closedDays(parseClosedDays(request.getClosedDays()))
                .avgServiceMins(request.getAvgServiceMins() != null ? request.getAvgServiceMins() : 10)
                .maxQueueSize(request.getMaxQueueSize() != null ? request.getMaxQueueSize() : 100)
                .noShowGraceMins(request.getNoShowGraceMins() != null ? request.getNoShowGraceMins() : 5)
                .rejoinWindowMins(request.getRejoinWindowMins() != null ? request.getRejoinWindowMins() : 15)
                .maxRejoins(request.getMaxRejoins() != null ? request.getMaxRejoins() : 1)
                .stopTokensBeforeClosingMins(request.getStopTokensBeforeClosingMins() != null ? request.getStopTokensBeforeClosingMins() : 0)
                .maxTokensPerDay(request.getMaxTokensPerDay())
                .build();

        shop.setSlug(slugService.generateUniqueSlug(request.getName(), null));
        shop = shopRepository.save(shop);
        log.info("Shop created: {} by owner {}", shop.getName(), owner.getId());
        return toDto(shop, null, null);
    }

    @Transactional(readOnly = true)
    public List<ShopDto> getOwnerShops(UUID ownerId) {
        return shopRepository.findByOwnerIdAndActiveTrue(ownerId).stream()
                .map(shop -> {
                    List<Token> queue = tokenRepository.findActiveQueueByShopAndDate(shop.getId(), LocalDate.now());
                    return toDto(shop, queue.size(), queue.size() * shop.getAvgServiceMins());
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShopDto> getAccessibleShops(User user) {
        if (user.getRole() == User.Role.SHOP_OWNER || user.getRole() == User.Role.ADMIN) {
            return getOwnerShops(user.getId());
        }
        if (user.getRole() != User.Role.SERVICE_PROVIDER) {
            throw new AccessDeniedException("This dashboard is not available for your account");
        }
        return serviceProviderRepository.findByUserId(user.getId())
                .filter(ServiceProvider::isActive)
                .filter(sp -> sp.getShop().isActive())
                .map(sp -> {
                    Shop shop = sp.getShop();
                    List<Token> queue = tokenRepository.findActiveQueueByShopAndDate(shop.getId(), LocalDate.now());
                    ShopDto dto = toDto(shop, queue.size(), queue.size() * shop.getAvgServiceMins());
                    dto.setMyStaffRole(sp.getStaffRole());
                    return List.of(dto);
                })
                .orElseGet(List::of);
    }

    public List<ShopDto> getNearbyShops(double lat, double lng, double radiusKm) {
        List<Shop> shops = shopRepository.findNearbyActiveShops(lat, lng, radiusKm, 20);
        shops.forEach(shop -> recordDiscoveryEvent(shop.getId(), "NEARBY_RESULT", null, shop.getCategory().name(), shop.getCity(), lat, lng, "nearby"));
        return shops.stream()
                .map(shop -> {
                    double dist = haversineKm(lat, lng, shop.getLatitude().doubleValue(), shop.getLongitude().doubleValue());
                    return Map.entry(shop, dist);
                })
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .map(e -> {
                    Shop shop = e.getKey();
                    List<Token> queue = tokenRepository.findActiveQueueByShopAndDate(shop.getId(), LocalDate.now());
                    ShopDto dto = toDto(shop, queue.size(), queue.size() * shop.getAvgServiceMins());
                    dto.setDistanceKm(Math.round(e.getValue() * 10.0) / 10.0);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<ShopDto> searchPublicShops(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isBlank()) return List.of();
        recordDiscoveryEvent(null, "SEARCH", q, null, null, null, null, "search");
        List<Shop> shops = shopRepository.searchActiveShops(q, 30);
        shops.stream().limit(10).forEach(shop ->
                recordDiscoveryEvent(shop.getId(), "SEARCH_RESULT", q, shop.getCategory().name(), shop.getCity(), null, null, "search"));
        return shops.stream()
                .map(shop -> {
                    List<Token> queue = tokenRepository.findActiveQueueByShopAndDate(shop.getId(), LocalDate.now());
                    return toDto(shop, queue.size(), queue.size() * shop.getAvgServiceMins());
                })
                .collect(Collectors.toList());
    }

    public List<ShopDto> getPopularShops(String category, Double lat, Double lng, int limit) {
        String normalizedCategory = normalizeCategory(category);
        int safeLimit = Math.max(1, Math.min(limit, 30));
        List<Shop> shops;
        try {
            shops = shopRepository.findPopularActiveShops(normalizedCategory, safeLimit);
        } catch (Exception e) {
            log.debug("Popular discovery query unavailable, using active-shop fallback: {}", e.getMessage());
            shops = fallbackPopularShops(normalizedCategory, safeLimit);
        }
        return shops.stream().map(shop -> toDiscoveryDto(shop, lat, lng)).collect(Collectors.toList());
    }

    public List<ShopDto> getTrendingShops(String category, Double lat, Double lng, int limit) {
        String normalizedCategory = normalizeCategory(category);
        int safeLimit = Math.max(1, Math.min(limit, 30));
        List<Shop> shops;
        try {
            shops = shopRepository.findTrendingActiveShops(normalizedCategory, safeLimit);
        } catch (Exception e) {
            log.debug("Trending discovery query unavailable, using active-shop fallback: {}", e.getMessage());
            shops = fallbackPopularShops(normalizedCategory, safeLimit);
        }
        if (shops.isEmpty()) {
            shops = fallbackPopularShops(normalizedCategory, safeLimit);
        }
        return shops.stream().map(shop -> toDiscoveryDto(shop, lat, lng)).collect(Collectors.toList());
    }

    public ShopDto getShopById(UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found: " + shopId));
        recordDiscoveryEvent(shop.getId(), "VIEW", null, shop.getCategory().name(), shop.getCity(), null, null, "detail");
        List<Token> queue = tokenRepository.findActiveQueueByShopAndDate(shopId, LocalDate.now());
        return toDto(shop, queue.size(), queue.size() * shop.getAvgServiceMins());
    }

    public ShopDto getShopBySlug(String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found for slug: " + slug));
        if (!shop.isActive()) throw new ResourceNotFoundException("Shop not found");
        recordDiscoveryEvent(shop.getId(), "VIEW", null, shop.getCategory().name(), shop.getCity(), null, null, "slug");
        List<Token> queue = tokenRepository.findActiveQueueByShopAndDate(shop.getId(), LocalDate.now());
        return toDto(shop, queue.size(), queue.size() * shop.getAvgServiceMins());
    }

    @Transactional
    public ShopDto updateShop(UUID shopId, CreateShopRequest request, UUID ownerId) {
        Shop shop = getShopWithAccessCheck(shopId, ownerId, StaffRole.MANAGER);

        String previousName = shop.getName();
        shop.setName(request.getName());
        shop.setBusinessAccount(resolveBusinessAccount(request.getBusinessAccountId(), ownerId));
        shop.setDescription(request.getDescription());
        shop.setAddress(request.getAddress());
        shop.setCity(request.getCity());
        shop.setState(request.getState());
        shop.setPincode(request.getPincode());
        shop.setPhone(request.getPhone());
        shop.setLogoUrl(request.getLogoUrl());
        shop.setPrimaryColor(normalizeColor(request.getPrimaryColor()));
        shop.setBranchCode(blankToNull(request.getBranchCode()));
        String registrationNumber = blankToNull(request.getBusinessRegistrationNumber());
        shop.setBusinessRegistrationNumber(registrationNumber);
        if (registrationNumber != null && shop.getVerificationStatus() == Shop.VerificationStatus.PENDING) {
            shop.setVerificationStatus(Shop.VerificationStatus.SUBMITTED);
        }
        if (request.getLatitude() != null) shop.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) shop.setLongitude(request.getLongitude());
        shop.setOpenTime(parseTimeOrDefault(request.getOpenTime(), shop.getOpenTime()));
        shop.setCloseTime(parseTimeOrDefault(request.getCloseTime(), shop.getCloseTime()));
        shop.setBreakStartTime(parseOptionalTime(request.getBreakStartTime()));
        shop.setBreakEndTime(parseOptionalTime(request.getBreakEndTime()));
        shop.setClosedDays(parseClosedDays(request.getClosedDays()));
        if (request.getAvgServiceMins() != null) shop.setAvgServiceMins(request.getAvgServiceMins());
        if (request.getMaxQueueSize() != null) shop.setMaxQueueSize(request.getMaxQueueSize());
        if (request.getNoShowGraceMins() != null) shop.setNoShowGraceMins(request.getNoShowGraceMins());
        if (request.getRejoinWindowMins() != null) shop.setRejoinWindowMins(request.getRejoinWindowMins());
        if (request.getMaxRejoins() != null) shop.setMaxRejoins(request.getMaxRejoins());
        if (request.getStopTokensBeforeClosingMins() != null) {
            shop.setStopTokensBeforeClosingMins(request.getStopTokensBeforeClosingMins());
        }
        shop.setMaxTokensPerDay(request.getMaxTokensPerDay());

        // Regenerate slug if name changed
        if (!Objects.equals(previousName, request.getName())) {
            shop.setSlug(slugService.generateUniqueSlug(request.getName(), shop.getId()));
        }

        return toDto(shopRepository.save(shop), null, null);
    }

    @Transactional
    public ShopDto updateIncident(UUID shopId, UpdateIncidentRequest request, UUID ownerId) {
        Shop shop = getShopWithAccessCheck(shopId, ownerId, StaffRole.MANAGER);

        shop.setIncidentStatus(request.getStatus());
        shop.setIncidentMessage(request.getMessage());
        return toDto(shopRepository.save(shop), null, null);
    }

    @Transactional
    public ShopDto toggleQueuePause(UUID shopId, boolean paused, UUID ownerId) {
        Shop shop = getShopWithAccessCheck(shopId, ownerId, StaffRole.MANAGER, StaffRole.RECEPTIONIST);
        shop.setQueuePaused(paused);
        log.info("Shop {} queue {}", shopId, paused ? "paused" : "resumed");
        return toDto(shopRepository.save(shop), null, null);
    }

    /**
     * Rapidly opens a new branch by cloning an existing shop's configuration.
     * Copies category, timings, services, and general settings.
     */
    @Transactional
    public ShopDto cloneShop(UUID sourceShopId, String newBranchName, String newBranchCode, String newAddress, UUID ownerId) {
        Shop source = shopRepository.findById(sourceShopId)
                .orElseThrow(() -> new ResourceNotFoundException("Source shop not found"));
        if (!source.getOwner().getId().equals(ownerId)) throw new AccessDeniedException("Not authorized");

        Shop branch = Shop.builder()
                .owner(source.getOwner())
                .businessAccount(source.getBusinessAccount())
                .name(newBranchName)
                .branchCode(newBranchCode)
                .address(newAddress)
                .category(source.getCategory())
                .description(source.getDescription())
                .city(source.getCity())
                .state(source.getState())
                .pincode(source.getPincode())
                .phone(source.getPhone())
                .logoUrl(source.getLogoUrl())
                .primaryColor(source.getPrimaryColor())
                .openTime(source.getOpenTime())
                .closeTime(source.getCloseTime())
                .breakStartTime(source.getBreakStartTime())
                .breakEndTime(source.getBreakEndTime())
                .avgServiceMins(source.getAvgServiceMins())
                .maxQueueSize(source.getMaxQueueSize())
                .noShowGraceMins(source.getNoShowGraceMins())
                .rejoinWindowMins(source.getRejoinWindowMins())
                .maxRejoins(source.getMaxRejoins())
                .closedDays(new LinkedHashSet<>(source.getClosedDays()))
                .build();

        branch = shopRepository.save(branch);
        branch.setSlug(slugService.generateUniqueSlug(newBranchName, branch.getId()));
        branch = shopRepository.save(branch);

        // Clone services
        for (Serviceoffred s : source.getServices()) {
            if (s.isActive()) {
                Serviceoffred cloned = Serviceoffred.builder()
                        .shop(branch).name(s.getName()).description(s.getDescription())
                        .durationMins(s.getDurationMins()).price(s.getPrice()).active(true).build();
                serviceRepository.save(cloned);
            }
        }

        log.info("New branch created: {} (cloned from {})", branch.getId(), sourceShopId);
        return toDto(branch, 0, 0);
    }

    @Transactional
    public ShopDto adminUpdateVerification(UUID shopId, Shop.VerificationStatus status, Boolean active) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        shop.setVerificationStatus(status);
        if (active != null) {
            shop.setActive(active);
        }
        return toDto(shopRepository.save(shop), null, null);
    }

    @Transactional(readOnly = true)
    public List<ShopDto> getPendingShops() {
        return shopRepository
                .findByVerificationStatusOrderByCreatedAtDesc(Shop.VerificationStatus.PENDING)
                .stream()
                .map(s -> toDto(s, null, null))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShopStatsDto getShopStats(UUID shopId, UUID actorId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        validateShopAccess(shop, actorId); // Any staff or owner can see stats

        List<Object[]> stats = tokenRepository.countByStatusForShopToday(shopId, LocalDate.now());
        long waiting = 0, served = 0, cancelled = 0, total = 0;
        for (Object[] row : stats) {
            String status = row[0].toString();
            long count = (long) row[1];
            total += count;
            switch (status) {
                case "WAITING", "CALLED", "ARRIVED", "SERVING" -> waiting += count;
                case "SERVED" -> served = count;
                case "CANCELLED", "SKIPPED" -> cancelled += count;
            }
        }

        return ShopStatsDto.builder()
                .totalTokensToday(total).servedToday(served)
                .waitingNow(waiting).cancelledToday(cancelled)
                .avgWaitMinutes(shop.getAvgServiceMins()).build();
    }

    // ——— Service management ———

    @Transactional
    public ServiceDto createService(UUID shopId, CreateServiceRequest request, UUID actorId) {
        Shop shop = getShopWithAccessCheck(shopId, actorId, StaffRole.MANAGER);

        Serviceoffred service = Serviceoffred.builder()
                .shop(shop).name(request.getName()).description(request.getDescription())
                .durationMins(request.getDurationMins()).price(request.getPrice()).build();
        service = serviceRepository.save(service);

        final Serviceoffred saved = service;
        List<ServiceProvider> providers = serviceProviderRepository.findByShopIdAndActiveTrue(shopId);
        providers.forEach(p -> p.getSupportedServices().add(saved));
        if (!providers.isEmpty()) serviceProviderRepository.saveAll(providers);

        return toServiceDto(service);
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> getShopServices(UUID shopId) {
        return serviceRepository.findByShopIdAndActiveTrue(shopId).stream()
                .map(this::toServiceDto).collect(Collectors.toList());
    }

    @Transactional
    public void deleteService(UUID serviceId, UUID actorId) {
        Serviceoffred service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        validateShopAccess(service.getShop(), actorId, StaffRole.MANAGER);
        service.setActive(false);
        serviceRepository.save(service);
    }

    // ——— Shop Status ———

    @Transactional(readOnly = true)
    public ShopStatusDto getShopStatus(UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        var holiday = shopHolidayRepository.findByShopIdAndDate(shopId, LocalDate.now());
        if (holiday.isPresent()) {
            return ShopStatusDto.builder().status("HOLIDAY")
                    .reason(holiday.get().getReason() != null ? holiday.get().getReason() : "Holiday").build();
        }

        DayOfWeek today = LocalDate.now().getDayOfWeek();
        if (shop.getClosedDays() != null && shop.getClosedDays().contains(today)) {
            return ShopStatusDto.builder().status("CLOSED")
                    .reason("Closed on " + today.name().charAt(0) + today.name().substring(1).toLowerCase()).build();
        }

        LocalTime now = LocalTime.now();

        if (shop.getBreakStartTime() != null && shop.getBreakEndTime() != null
                && !now.isBefore(shop.getBreakStartTime()) && now.isBefore(shop.getBreakEndTime())) {
            return ShopStatusDto.builder().status("BREAK").reason("Break until " + shop.getBreakEndTime())
                    .nextChangeAt(LocalDate.now().atTime(shop.getBreakEndTime())
                            .atZone(ZoneId.of("Asia/Kolkata")).toInstant()).build();
        }

        if (now.isBefore(shop.getOpenTime()) || !now.isBefore(shop.getCloseTime())) {
            boolean isBeforeOpen = now.isBefore(shop.getOpenTime());
            Instant nextOpen = LocalDate.now().plusDays(isBeforeOpen ? 0 : 1)
                    .atTime(shop.getOpenTime()).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
            return ShopStatusDto.builder().status("CLOSED")
                    .reason("Opens at " + shop.getOpenTime()).nextChangeAt(nextOpen).build();
        }

        if (!now.isBefore(shop.getCloseTime().minusMinutes(30))) {
            return ShopStatusDto.builder().status("CLOSES_SOON").reason("Closes at " + shop.getCloseTime())
                    .nextChangeAt(LocalDate.now().atTime(shop.getCloseTime())
                            .atZone(ZoneId.of("Asia/Kolkata")).toInstant()).build();
        }

        return ShopStatusDto.builder().status("OPEN")
                .nextChangeAt(LocalDate.now().atTime(shop.getCloseTime())
                        .atZone(ZoneId.of("Asia/Kolkata")).toInstant()).build();
    }

    @Transactional(readOnly = true)
    public QrPosterDto getQrPoster(UUID shopId, UUID actorId) {
        Shop shop = getShopWithAccessCheck(shopId, actorId, StaffRole.MANAGER);
        String payload = "https://app.queueless.in/shops/" + shop.getId();
        String svg = buildQrSvg(payload);
        return QrPosterDto.builder()
                .shopId(shop.getId())
                .shopName(shop.getName())
                .branchCode(shop.getBranchCode())
                .qrPayload(payload)
                .qrSvg(svg)
                .posterTitle("Scan to join the queue")
                .posterSubtitle(shop.getName() + (shop.getBranchCode() != null ? " - " + shop.getBranchCode() : ""))
                .build();
    }

    // ——— Holiday Management ———

    @Transactional
    public HolidayDto addHoliday(UUID shopId, UUID actorId, CreateHolidayRequest request) {
        Shop shop = getShopWithAccessCheck(shopId, actorId, StaffRole.MANAGER);
        if (shopHolidayRepository.findByShopIdAndDate(shopId, request.getDate()).isPresent()) {
            throw new BusinessException("A holiday already exists for " + request.getDate());
        }
        ShopHoliday h = shopHolidayRepository.save(
                ShopHoliday.builder().shop(shop).date(request.getDate()).reason(request.getReason()).build());
        return HolidayDto.builder().id(h.getId()).date(h.getDate()).reason(h.getReason()).build();
    }

    @Transactional(readOnly = true)
    public List<HolidayDto> getUpcomingHolidays(UUID shopId) {
        return shopHolidayRepository
                .findByShopIdAndDateGreaterThanEqualOrderByDateAsc(shopId, LocalDate.now()).stream()
                .map(h -> HolidayDto.builder().id(h.getId()).date(h.getDate()).reason(h.getReason()).build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteHoliday(UUID holidayId, UUID actorId) {
        ShopHoliday h = shopHolidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
        validateShopAccess(h.getShop(), actorId, StaffRole.MANAGER);
        shopHolidayRepository.delete(h);
    }

    // ——— Analytics ———

    @Transactional(readOnly = true)
    public AnalyticsDto getAnalytics(UUID shopId, UUID actorId, int days) {
        Shop shop = getShopWithAccessCheck(shopId, actorId, StaffRole.MANAGER);

        LocalDate fromDate = LocalDate.now().minusDays(days);

        List<AnalyticsDto.HourlyBucket> heatmap = tokenRepository.findHourlyHeatmap(shopId, fromDate).stream()
                .map(r -> AnalyticsDto.HourlyBucket.builder()
                        .hour(((Number) r[0]).intValue()).count(((Number) r[1]).longValue()).build())
                .collect(Collectors.toList());

        List<Object[]> nsList = tokenRepository.findNoShowStats(shopId, fromDate,
                com.queueless.entity.Token.TokenStatus.SKIPPED,
                List.of(com.queueless.entity.Token.TokenStatus.CALLED,
                        com.queueless.entity.Token.TokenStatus.SERVED,
                        com.queueless.entity.Token.TokenStatus.SKIPPED));
        Object[] ns = (nsList != null && !nsList.isEmpty()) ? nsList.get(0) : new Object[]{0L, 0L};
        long skipped = ns[0] != null ? ((Number) ns[0]).longValue() : 0;
        long totalCalled = ns[1] != null ? ((Number) ns[1]).longValue() : 0;
        double noShowRate = totalCalled > 0 ? Math.round(skipped * 1000.0 / totalCalled) / 10.0 : 0.0;

        List<AnalyticsDto.ServicePopularity> popularity = tokenRepository.findServicePopularity(shopId, fromDate).stream()
                .map(r -> AnalyticsDto.ServicePopularity.builder()
                        .serviceName((String) r[0]).tokenCount(((Number) r[1]).longValue()).build())
                .collect(Collectors.toList());

        List<AnalyticsDto.ProviderUtilization> utilization = tokenRepository.findProviderUtilization(shopId, fromDate,
                com.queueless.entity.Token.TokenStatus.SERVED).stream()
                .map(r -> AnalyticsDto.ProviderUtilization.builder()
                        .providerName((String) r[0]).totalTokens(((Number) r[1]).longValue())
                        .servedTokens(((Number) r[2]).longValue()).build())
                .collect(Collectors.toList());

        return AnalyticsDto.builder().hourlyHeatmap(heatmap).noShowRate(noShowRate)
                .servicePopularity(popularity).providerUtilization(utilization).build();
    }

    // ——— Mappers ———

    private ShopDto toDto(Shop shop, Integer queueSize, Integer estWait) {
        return ShopDto.builder()
                .id(shop.getId()).ownerId(shop.getOwner().getId()).ownerName(shop.getOwner().getName())
                .name(shop.getName()).category(shop.getCategory()).description(shop.getDescription())
                .address(shop.getAddress()).city(shop.getCity()).state(shop.getState()).pincode(shop.getPincode())
                .latitude(shop.getLatitude()).longitude(shop.getLongitude()).phone(shop.getPhone())
                .logoUrl(shop.getLogoUrl()).primaryColor(shop.getPrimaryColor())
                .businessRegistrationNumber(shop.getBusinessRegistrationNumber())
                .businessAccountId(shop.getBusinessAccount() != null ? shop.getBusinessAccount().getId() : null)
                .businessAccountName(shop.getBusinessAccount() != null ? shop.getBusinessAccount().getName() : null)
                .branchCode(shop.getBranchCode())
                .verificationStatus(shop.getVerificationStatus())
                .active(shop.isActive()).queuePaused(shop.isQueuePaused())
                .openTime(shop.getOpenTime().toString()).closeTime(shop.getCloseTime().toString())
                .breakStartTime(shop.getBreakStartTime() != null ? shop.getBreakStartTime().toString() : null)
                .breakEndTime(shop.getBreakEndTime() != null ? shop.getBreakEndTime().toString() : null)
                .closedDays(shop.getClosedDays() != null ? shop.getClosedDays().stream().toList() : List.of())
                .avgServiceMins(shop.getAvgServiceMins()).maxQueueSize(shop.getMaxQueueSize())
                .noShowGraceMins(shop.getNoShowGraceMins()).rejoinWindowMins(shop.getRejoinWindowMins())
                .maxRejoins(shop.getMaxRejoins())
                .stopTokensBeforeClosingMins(shop.getStopTokensBeforeClosingMins())
                .maxTokensPerDay(shop.getMaxTokensPerDay())
                .incidentStatus(shop.getIncidentStatus())
                .incidentMessage(shop.getIncidentMessage())
                .currentQueueSize(queueSize).estimatedWaitMins(estWait).createdAt(shop.getCreatedAt())
                .slug(shop.getSlug())
                .build();
    }

    private BusinessAccount resolveBusinessAccount(UUID businessAccountId, UUID ownerId) {
        if (businessAccountId == null) {
            return null;
        }
        BusinessAccount account = businessAccountRepository.findById(businessAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Business account not found"));
        if (!account.getOwner().getId().equals(ownerId) || !account.isActive()) {
            throw new AccessDeniedException("Not authorized for this business account");
        }
        return account;
    }

    private ServiceDto toServiceDto(Serviceoffred service) {
        return ServiceDto.builder()
                .id(service.getId()).shopId(service.getShop().getId()).name(service.getName())
                .description(service.getDescription()).durationMins(service.getDurationMins())
                .price(service.getPrice()).active(service.isActive()).build();
    }

    private ShopDto toDiscoveryDto(Shop shop, Double lat, Double lng) {
        List<Token> queue = tokenRepository.findActiveQueueByShopAndDate(shop.getId(), LocalDate.now());
        ShopDto dto = toDto(shop, queue.size(), queue.size() * shop.getAvgServiceMins());
        if (lat != null && lng != null && shop.getLatitude() != null && shop.getLongitude() != null) {
            dto.setDistanceKm(Math.round(haversineKm(lat, lng, shop.getLatitude().doubleValue(), shop.getLongitude().doubleValue()) * 10.0) / 10.0);
        }
        return dto;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)) {
            return null;
        }
        try {
            return Shop.Category.valueOf(category.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unsupported shop category: " + category);
        }
    }

    private List<Shop> fallbackPopularShops(String normalizedCategory, int limit) {
        return shopRepository.findByActiveTrue().stream()
                .filter(shop -> normalizedCategory == null || shop.getCategory().name().equals(normalizedCategory))
                .sorted(Comparator.comparing(Shop::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void recordDiscoveryEvent(UUID shopId, String eventType, String query, String category, String city,
                                      Double lat, Double lng, String source) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO shop_discovery_events
                        (shop_id, event_type, query, category, city, latitude, longitude, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, shopId, eventType, trimTo(query, 500), trimTo(category, 50), trimTo(city, 100), lat, lng, source);
        } catch (Exception e) {
            log.debug("Discovery event tracking skipped: {}", e.getMessage());
        }
    }

    private String trimTo(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private LocalTime parseTimeOrDefault(String value, LocalTime fallback) {
        return value == null || value.isBlank() ? fallback : LocalTime.parse(value);
    }

    private LocalTime parseOptionalTime(String value) {
        return value == null || value.isBlank() ? null : LocalTime.parse(value);
    }

    private String normalizeColor(String value) {
        return value == null || value.isBlank() ? "#f97316" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildQrSvg(String payload) {
        int hash = Math.abs(payload.hashCode());
        StringBuilder cells = new StringBuilder();
        int size = 29;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean finder = isFinder(x, y, 0, 0) || isFinder(x, y, 22, 0) || isFinder(x, y, 0, 22);
                boolean fill = finder || (((x * 31 + y * 17 + hash) ^ (hash >> ((x + y) % 8))) & 3) == 0;
                if (fill) {
                    cells.append("<rect x='").append(x).append("' y='").append(y)
                            .append("' width='1' height='1'/>");
                }
            }
        }
        return "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 29 29' shape-rendering='crispEdges'>"
                + "<rect width='29' height='29' fill='white'/>"
                + "<g fill='black'>" + cells + "</g></svg>";
    }

    private boolean isFinder(int x, int y, int originX, int originY) {
        int dx = x - originX;
        int dy = y - originY;
        if (dx < 0 || dy < 0 || dx > 6 || dy > 6) return false;
        return dx == 0 || dy == 0 || dx == 6 || dy == 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4);
    }

    private Set<DayOfWeek> parseClosedDays(List<DayOfWeek> value) {
        return value == null ? new LinkedHashSet<>() : new LinkedHashSet<>(value);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
    /**
     * Verifies that the actor is either the owner of the shop, or a staff member with at least one of the allowed roles.
     * Throws AccessDeniedException if not authorized.
     */
    public void validateShopAccess(Shop shop, UUID actorId, StaffRole... allowedRoles) {
        if (shop.getOwner().getId().equals(actorId)) {
            return; // Owner has full access
        }

        ServiceProvider provider = serviceProviderRepository.findByUserId(actorId)
                .filter(p -> p.getShop().getId().equals(shop.getId()) && p.isActive())
                .orElseThrow(() -> new AccessDeniedException("Not authorized for this shop"));

        if (allowedRoles == null || allowedRoles.length == 0) {
            return; // Any active staff member is allowed
        }

        boolean hasRole = Arrays.asList(allowedRoles).contains(provider.getStaffRole());
        if (!hasRole) {
            throw new AccessDeniedException("Not authorized: Requires one of roles " + Arrays.toString(allowedRoles));
        }
    }

    /**
     * Convenience method to get a Shop and validate access in one step.
     */
    public Shop getShopWithAccessCheck(UUID shopId, UUID actorId, StaffRole... allowedRoles) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        validateShopAccess(shop, actorId, allowedRoles);
        return shop;
    }
}

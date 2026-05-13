package com.queueless.service;

import com.queueless.dto.Dtos.*;
import com.queueless.entity.ServiceProvider;
import com.queueless.entity.Serviceoffred;
import com.queueless.entity.Shop;
import com.queueless.entity.User;
import com.queueless.exception.AccessDeniedException;
import com.queueless.exception.BusinessException;
import com.queueless.exception.ResourceNotFoundException;
import com.queueless.repository.ServiceProviderRepository;
import com.queueless.repository.ServiceRepository;
import com.queueless.repository.ShopRepository;
import com.queueless.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing service providers (staff) within a shop.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceProviderService {

    private final ServiceProviderRepository providerRepository;
    private final ShopRepository shopRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a new service provider for a shop.
     * Only the shop owner can perform this action.
     */
    @Transactional
    public ServiceProviderDto createProvider(UUID shopId, UUID ownerId, CreateServiceProviderRequest request) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        validateManagerAccess(shop, ownerId);

        // Check if user already exists
        if (userRepository.findByPhone(request.getPhone()).isPresent()) {
            throw new BusinessException("A user with this phone number already exists");
        }

        // Create the user account for the provider
        User user = User.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(User.Role.SERVICE_PROVIDER)
                .active(true)
                .build();
        user = userRepository.save(user);

        // Create the provider link to the shop
        Set<Serviceoffred> supportedServices = resolveSupportedServices(shopId, request.getServiceIds());

        ServiceProvider provider = ServiceProvider.builder()
                .shop(shop)
                .user(user)
                .title(request.getTitle())
                .staffRole(request.getStaffRole() != null ? request.getStaffRole() : com.queueless.entity.StaffRole.PROVIDER)
                .supportedServices(supportedServices)
                .active(true)
                .available(true)
                .build();
        provider = providerRepository.save(provider);

        log.info("Service provider {} created for shop {}", provider.getId(), shopId);

        return toDto(provider);
    }

    /**
     * Gets all active providers for a shop.
     */
    @Transactional(readOnly = true)
    public List<ServiceProviderDto> getProvidersByShop(UUID shopId) {
        return providerRepository.findByShopIdAndActiveTrue(shopId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Deletes (soft deletes) a provider.
     */
    @Transactional
    public void deleteProvider(UUID shopId, UUID providerId, UUID ownerId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        validateManagerAccess(shop, ownerId);

        ServiceProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (!provider.getShop().getId().equals(shopId)) {
            throw new BusinessException("Provider does not belong to this shop");
        }

        provider.setActive(false);
        provider.getUser().setActive(false);
        
        providerRepository.save(provider);
        userRepository.save(provider.getUser());
        
        log.info("Service provider {} removed from shop {}", providerId, shopId);
    }

    /**
     * Updates whether a provider is currently available for queue and appointment assignment.
     * Owners/admins may update any provider in their shop; providers may update themselves.
     */
    @Transactional
    public ServiceProviderDto updateAvailability(UUID shopId, UUID providerId, UUID actorId, boolean available) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));

        ServiceProvider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (!provider.getShop().getId().equals(shopId)) {
            throw new BusinessException("Provider does not belong to this shop");
        }

        boolean isOwner = shop.getOwner().getId().equals(actorId);
        boolean isSelf = provider.getUser().getId().equals(actorId);
        boolean isManager = false;

        if (!isOwner && !isSelf) {
            isManager = providerRepository.findByUserId(actorId)
                    .filter(p -> p.getShop().getId().equals(shopId) && p.isActive() && p.getStaffRole() == com.queueless.entity.StaffRole.MANAGER)
                    .isPresent();
        }

        if (!isOwner && !isSelf && !isManager) {
            throw new AccessDeniedException("Not authorized to update availability");
        }

        provider.setAvailable(available);
        provider = providerRepository.save(provider);
        log.info("Provider {} availability set to {}", providerId, available);
        return toDto(provider);
    }

    /**
     * Updates the authenticated provider's own availability for the specified shop.
     */
    @Transactional
    public ServiceProviderDto updateMyAvailability(UUID shopId, UUID actorId, boolean available) {
        ServiceProvider provider = providerRepository.findByUserId(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider profile not found"));

        return updateAvailability(shopId, provider.getId(), actorId, available);
    }

    private ServiceProviderDto toDto(ServiceProvider provider) {
        return ServiceProviderDto.builder()
                .id(provider.getId())
                .shopId(provider.getShop().getId())
                .userId(provider.getUser().getId())
                .name(provider.getUser().getName())
                .phone(provider.getUser().getPhone())
                .title(provider.getTitle())
                .staffRole(provider.getStaffRole())
                .serviceIds(provider.getSupportedServices().stream()
                        .map(Serviceoffred::getId)
                        .toList())
                .serviceNames(provider.getSupportedServices().stream()
                        .map(Serviceoffred::getName)
                        .toList())
                .active(provider.isActive())
                .available(provider.isAvailable())
                .build();
    }

    private Set<Serviceoffred> resolveSupportedServices(UUID shopId, List<UUID> requestedServiceIds) {
        List<Serviceoffred> shopServices = serviceRepository.findByShopIdAndActiveTrue(shopId);
        if (requestedServiceIds == null || requestedServiceIds.isEmpty()) {
            return new LinkedHashSet<>(shopServices);
        }

        Set<UUID> requestedIds = new LinkedHashSet<>(requestedServiceIds);
        List<Serviceoffred> matched = shopServices.stream()
                .filter(service -> requestedIds.contains(service.getId()))
                .toList();

        if (matched.size() != requestedIds.size()) {
            throw new BusinessException("One or more selected services do not belong to this shop");
        }

        return new LinkedHashSet<>(matched);
    }

    private void validateManagerAccess(Shop shop, UUID actorId) {
        if (shop.getOwner().getId().equals(actorId)) return;

        providerRepository.findByUserId(actorId)
                .filter(p -> p.getShop().getId().equals(shop.getId()) && p.isActive())
                .filter(p -> p.getStaffRole() == com.queueless.entity.StaffRole.MANAGER)
                .orElseThrow(() -> new AccessDeniedException("Only the shop owner or a manager can perform this action"));
    }
}

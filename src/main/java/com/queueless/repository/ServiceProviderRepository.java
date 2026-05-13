package com.queueless.repository;

import com.queueless.entity.ServiceProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository for ServiceProvider entity. */
@Repository
public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, UUID> {
    
    List<ServiceProvider> findByShopIdAndActiveTrue(UUID shopId);
    
    Optional<ServiceProvider> findByUserId(UUID userId);
}

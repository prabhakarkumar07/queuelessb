package com.queueless.repository;

import com.queueless.entity.Serviceoffred;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Serviceoffred, UUID> {
    List<Serviceoffred> findByShopIdAndActiveTrue(UUID shopId);
    List<Serviceoffred> findByShopId(UUID shopId);
    boolean existsByShopIdAndName(UUID shopId, String name);
}
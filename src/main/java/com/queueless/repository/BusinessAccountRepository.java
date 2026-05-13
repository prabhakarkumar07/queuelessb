package com.queueless.repository;

import com.queueless.entity.BusinessAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessAccountRepository extends JpaRepository<BusinessAccount, UUID> {
    List<BusinessAccount> findByOwnerIdAndActiveTrue(UUID ownerId);

    Optional<BusinessAccount> findByOwnerId(UUID ownerId);

    Optional<BusinessAccount> findByIdAndOwnerId(UUID id, UUID ownerId);
}

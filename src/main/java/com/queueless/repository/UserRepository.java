package com.queueless.repository;

import com.queueless.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Repository for User entity operations. */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailOrPhone(String email, String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    @Query("SELECT u FROM User u WHERE u.phone = :phone AND u.active = true")
    Optional<User> findActiveByPhone(@Param("phone") String phone);
}
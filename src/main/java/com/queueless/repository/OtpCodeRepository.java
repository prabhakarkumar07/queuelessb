package com.queueless.repository;

import com.queueless.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phone, OtpCode.Purpose purpose);

    @Modifying
    @Query("UPDATE OtpCode o SET o.consumedAt = :now WHERE o.phone = :phone AND o.purpose = :purpose AND o.consumedAt IS NULL")
    int consumeOpenCodes(@Param("phone") String phone,
                         @Param("purpose") OtpCode.Purpose purpose,
                         @Param("now") Instant now);
}

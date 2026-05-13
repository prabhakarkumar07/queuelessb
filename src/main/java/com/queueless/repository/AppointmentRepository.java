package com.queueless.repository;

import com.queueless.entity.Appointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Page<Appointment> findByUserIdOrderByScheduledAtDesc(UUID userId, Pageable pageable);

    Page<Appointment> findByShopIdOrderByScheduledAtAsc(UUID shopId, Pageable pageable);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.shop.id = :shopId
          AND a.scheduledAt BETWEEN :start AND :end
          AND a.status NOT IN ('CANCELLED', 'RESCHEDULED')
        ORDER BY a.scheduledAt ASC
        """)
    List<Appointment> findByShopAndDateRange(@Param("shopId") UUID shopId,
                                              @Param("start") Instant start,
                                              @Param("end") Instant end);

    @Query(value = """
        SELECT COUNT(*) FROM appointments a
        WHERE a.shop_id = :shopId
          AND a.status NOT IN ('CANCELLED', 'RESCHEDULED')
          AND (a.payment_status != 'FAILED')
          AND (a.status != 'PENDING' OR a.created_at > :graceTime)
          AND (a.scheduled_at < :end)
          AND (a.scheduled_at + (a.duration_mins * interval '1 minute') > :start)
        """, nativeQuery = true)
    long countConflictingSlots(@Param("shopId") UUID shopId,
                                @Param("start") Instant start,
                                @Param("end") Instant end,
                                @Param("graceTime") Instant graceTime);

    @Query(value = """
        SELECT COUNT(*) FROM appointments a
        WHERE a.shop_id = :shopId
          AND a.provider_id = :providerId
          AND a.status NOT IN ('CANCELLED', 'RESCHEDULED')
          AND (a.payment_status != 'FAILED')
          AND (a.status != 'PENDING' OR a.created_at > :graceTime)
          AND (a.scheduled_at < :end)
          AND (a.scheduled_at + (a.duration_mins * interval '1 minute') > :start)
        """, nativeQuery = true)
    long countConflictingSlotsForProvider(@Param("shopId") UUID shopId,
                                          @Param("providerId") UUID providerId,
                                          @Param("start") Instant start,
                                          @Param("end") Instant end,
                                          @Param("graceTime") Instant graceTime);

    List<Appointment> findAllByStatusAndScheduledAtBetweenAndReminderSentFalse(
            Appointment.AppointmentStatus status, Instant start, Instant end);
}

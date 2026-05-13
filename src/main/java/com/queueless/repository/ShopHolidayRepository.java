package com.queueless.repository;

import com.queueless.entity.ShopHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShopHolidayRepository extends JpaRepository<ShopHoliday, UUID> {

    List<ShopHoliday> findByShopIdAndDateBetween(UUID shopId, LocalDate from, LocalDate to);

    Optional<ShopHoliday> findByShopIdAndDate(UUID shopId, LocalDate date);

    List<ShopHoliday> findByShopIdAndDateGreaterThanEqualOrderByDateAsc(UUID shopId, LocalDate from);
}

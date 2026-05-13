package com.queueless.service;

import com.queueless.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TokenRepository tokenRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getShopAnalytics(UUID shopId, int days) {
        LocalDate fromDate = LocalDate.now().minusDays(days);
        Map<String, Object> result = new HashMap<>();

        // 1. Daily Traffic
        List<Object[]> dailyTrafficData = tokenRepository.findDailyTraffic(shopId, fromDate);
        result.put("dailyTraffic", dailyTrafficData.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", row[0] != null ? row[0].toString() : null);
            item.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
            return item;
        }).collect(Collectors.toList()));

        // 2. Service Popularity
        List<Object[]> serviceData = tokenRepository.findServicePopularity(shopId, fromDate);
        result.put("servicePopularity", serviceData.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("service", row[0] != null ? row[0].toString() : "Unknown");
            item.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
            return item;
        }).collect(Collectors.toList()));

        // 3. Provider Performance
        List<Object[]> providerData = tokenRepository.findProviderUtilization(shopId, fromDate, com.queueless.entity.Token.TokenStatus.SERVED);
        result.put("providerPerformance", providerData.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("name", row[0] != null ? row[0].toString() : "Unknown");
            item.put("total", row[1] != null ? ((Number) row[1]).longValue() : 0L);
            item.put("served", row[2] != null ? ((Number) row[2]).longValue() : 0L);
            return item;
        }).collect(Collectors.toList()));

        // 4. Hourly Heatmap
        List<Object[]> heatmapData = tokenRepository.findHourlyHeatmap(shopId, fromDate);
        result.put("hourlyHeatmap", heatmapData.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", row[0] != null ? ((Number) row[0]).intValue() : 0);
            item.put("count", row[1] != null ? ((Number) row[1]).longValue() : 0L);
            return item;
        }).collect(Collectors.toList()));

        // 5. No-Show Rate
        List<com.queueless.entity.Token.TokenStatus> relevant = Arrays.asList(
                com.queueless.entity.Token.TokenStatus.CALLED,
                com.queueless.entity.Token.TokenStatus.ARRIVED,
                com.queueless.entity.Token.TokenStatus.SERVING,
                com.queueless.entity.Token.TokenStatus.SERVED,
                com.queueless.entity.Token.TokenStatus.SKIPPED
        );
        List<Object[]> noShowData = tokenRepository.findNoShowStats(shopId, fromDate, com.queueless.entity.Token.TokenStatus.SKIPPED, relevant);
        if (!noShowData.isEmpty() && noShowData.get(0) != null && noShowData.get(0)[1] != null) {
            long skipped = noShowData.get(0)[0] != null ? ((Number) noShowData.get(0)[0]).longValue() : 0L;
            long total = ((Number) noShowData.get(0)[1]).longValue();
            result.put("noShowRate", total > 0 ? (double) skipped / (double) total : 0.0);
        } else {
            result.put("noShowRate", 0.0);
        }

        return result;
    }
}

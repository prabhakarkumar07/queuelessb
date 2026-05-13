package com.queueless.service;

import com.queueless.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.UUID;

/**
 * Generates and ensures uniqueness of URL-safe slugs for shops.
 *
 * Rules:
 *  - lowercase only
 *  - non-alphanumeric characters → single hyphen
 *  - strip leading/trailing hyphens
 *  - max 100 characters
 *  - unique within the shops table (appends -2, -3… if collision)
 */
@Service
@RequiredArgsConstructor
public class SlugService {

    private final ShopRepository shopRepository;

    /**
     * Converts a raw name to a slug candidate.
     * e.g. "Star Salon & Spa!" → "star-salon-spa"
     */
    public String toSlug(String name) {
        if (name == null || name.isBlank()) return "shop";

        // Normalize unicode (e.g. é → e)
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return normalized
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")  // non-alphanumeric → hyphen
                .replaceAll("^-+|-+$", "")       // strip leading/trailing hyphens
                .substring(0, Math.min(normalized.length(), 100));
    }

    /**
     * Generates a unique slug for a new shop (shopId is null).
     * For existing shops, pass the shopId to exclude self from uniqueness check.
     */
    public String generateUniqueSlug(String name, UUID shopId) {
        String base = toSlug(name);
        if (base.isBlank()) base = "shop";

        String candidate = base;
        int suffix = 2;

        while (isSlugTaken(candidate, shopId)) {
            candidate = base + "-" + suffix;
            suffix++;
            if (suffix > 999) {
                // Safety fallback: append UUID fragment
                candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
                break;
            }
        }
        return candidate;
    }

    private boolean isSlugTaken(String slug, UUID excludeShopId) {
        if (excludeShopId == null) {
            return shopRepository.existsBySlug(slug);
        }
        return shopRepository.existsBySlugAndIdNot(slug, excludeShopId);
    }
}

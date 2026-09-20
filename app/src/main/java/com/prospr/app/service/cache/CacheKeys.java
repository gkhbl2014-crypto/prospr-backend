package com.prospr.app.service.cache;

import java.util.UUID;

/**
 * Centralized cache-key builder so no other class constructs a Redis key by hand.
 * Convention: {@code prospr:v1:{domain}:{resource}:{scope}:{identifier}}. {@code v1} lets the
 * whole namespace be bumped later if an analytics calculation's shape changes in a way that would
 * make old cached values misleading rather than just stale.
 *
 * <p>Every key here is scoped by {@code memberId}, not {@code familyId} - Lifestyle Creep, Safety
 * Net, and Spending Summary are all computed per-member today (see each service's own docs). A
 * future family-wide endpoint for any of these must authorize the caller against the target
 * member's family <em>before</em> ever calling into the cache, exactly like
 * {@code SafetyNetController.getMemberSafetyNet} already does today - the cache itself performs no
 * authorization of its own.
 */
public final class CacheKeys {

    private static final String PREFIX = "prospr:v1:analytics:";

    private CacheKeys() {
    }

    public static String lifestyleCreep(UUID memberId) {
        return PREFIX + "lifestyle-creep:member:" + memberId;
    }

    public static String safetyNet(UUID memberId) {
        return PREFIX + "safety-net:member:" + memberId;
    }

    public static String spendingSummary(UUID memberId) {
        return PREFIX + "spending-summary:member:" + memberId;
    }
}

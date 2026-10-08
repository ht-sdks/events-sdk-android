package com.hightouch.analytics.braze;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hightouch.analytics.integrations.TrackPayload;

import java.util.Collections;
import java.util.Map;

/** What a {@link BrazePurchase} was mapped from. */
public final class PurchaseContext {
    private final TrackPayload event;
    private final Map<String, Object> order;
    private final Map<String, Object> product;

    PurchaseContext(
            TrackPayload event, Map<String, Object> order, Map<String, Object> product) {
        this.event = event;
        this.order = Collections.unmodifiableMap(order);
        this.product = product == null ? null : Collections.unmodifiableMap(product);
    }

    @NonNull
    public TrackPayload event() {
        return event;
    }

    /** The event's properties. */
    @NonNull
    public Map<String, Object> order() {
        return order;
    }

    /** The product this purchase came from, or {@code null} for a purchase per order. */
    @Nullable
    public Map<String, Object> product() {
        return product;
    }
}

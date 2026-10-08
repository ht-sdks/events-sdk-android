package com.hightouch.analytics.braze;

import androidx.annotation.NonNull;

import com.hightouch.analytics.integrations.TrackPayload;

/** Decides whether a {@code track} call is logged as a Braze purchase. */
public interface PurchaseEventMatcher {
    boolean isPurchaseEvent(@NonNull TrackPayload track);
}

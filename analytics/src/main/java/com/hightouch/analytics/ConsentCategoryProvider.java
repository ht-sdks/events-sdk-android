package com.hightouch.analytics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Map;

/**
 * Adapts a Consent Management Platform (CMP) SDK to the {@link ConsentManager}, reporting consent
 * state keyed by CMP category ID (e.g. OneTrust group IDs like {@code "C0002"}). Unknown or
 * undetermined categories must be reported as {@code false}. Implementations must be thread-safe:
 * {@link #getConsentStatuses()} is called from both the analytics worker thread and the main
 * thread.
 */
public interface ConsentCategoryProvider {

    /** Returns a snapshot of the current consent state: category ID to consented. */
    @NonNull
    Map<String, Boolean> getConsentStatuses();

    /**
     * Sets the single listener invoked on consent changes ({@code null} clears it). The {@link
     * ConsentManager} owns this registration.
     */
    void setConsentChangeListener(@Nullable ConsentChangeListener listener);

    /** Releases any resources held by the provider (e.g. unregisters CMP listeners). */
    void shutdown();

    interface ConsentChangeListener {
        void onConsentChange(@NonNull Map<String, Boolean> statuses);
    }
}

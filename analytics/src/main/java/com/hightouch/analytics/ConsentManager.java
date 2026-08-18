package com.hightouch.analytics;

import static com.hightouch.analytics.internal.Utils.assertNotNull;
import static com.hightouch.analytics.internal.Utils.assertNotNullOrEmpty;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hightouch.analytics.integrations.BasePayload;
import com.hightouch.analytics.integrations.TrackPayload;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wires CMP-driven consent into an {@link Analytics} instance: stamps every event with {@code
 * context.consent.categoryPreferences}, gates mapped destinations unless all of their categories
 * are consented (unmapped destinations are never gated), and fires a {@code "Consent Updated"}
 * track event on consent changes (which bypasses the Hightouch destination's gate only).
 *
 * <p>Call {@link #attach(Analytics.Builder)} before {@code build()}, then {@link
 * #start(Analytics)} with the built instance. See analytics-onetrust/README.md for a full example.
 */
public class ConsentManager {

    /** Integration key of the Hightouch cloud destination; map it to gate delivery to Hightouch. */
    public static final String HIGHTOUCH_INTEGRATION_KEY = SegmentIntegration.SEGMENT_KEY;

    /** Default name of the track event fired when consent changes. */
    public static final String DEFAULT_CONSENT_UPDATED_EVENT_NAME = "Consent Updated";

    static final String CONSENT_CONTEXT_KEY = "consent";
    static final String CATEGORY_PREFERENCES_KEY = "categoryPreferences";

    private final ConsentCategoryProvider provider;
    private final Map<String, List<String>> integrationCategoryMappings;
    private final String consentUpdatedEventName;
    private final boolean consentUpdatedEventEnabled;
    private volatile Analytics analytics;

    ConsentManager(
            @NonNull ConsentCategoryProvider provider,
            @NonNull Map<String, List<String>> integrationCategoryMappings,
            @NonNull String consentUpdatedEventName,
            boolean consentUpdatedEventEnabled) {
        this.provider = provider;
        this.integrationCategoryMappings = integrationCategoryMappings;
        this.consentUpdatedEventName = consentUpdatedEventName;
        this.consentUpdatedEventEnabled = consentUpdatedEventEnabled;
    }

    /** Registers the stamping and gating middleware; call before {@code build()}. */
    @NonNull
    public Analytics.Builder attach(@NonNull Analytics.Builder builder) {
        assertNotNull(builder, "builder");
        builder.useSourceMiddleware(stampingMiddleware());
        for (Map.Entry<String, List<String>> mapping : integrationCategoryMappings.entrySet()) {
            builder.useDestinationMiddleware(
                    mapping.getKey(), gatingMiddleware(mapping.getKey(), mapping.getValue()));
        }
        return builder;
    }

    /** Starts listening for consent changes; call after {@code build()} with the built instance. */
    public void start(@NonNull final Analytics analytics) {
        assertNotNull(analytics, "analytics");
        this.analytics = analytics;
        if (!consentUpdatedEventEnabled) {
            return;
        }
        provider.setConsentChangeListener(
                new ConsentCategoryProvider.ConsentChangeListener() {
                    @Override
                    public void onConsentChange(@NonNull Map<String, Boolean> statuses) {
                        Analytics instance = ConsentManager.this.analytics;
                        if (instance == null) {
                            return;
                        }
                        Properties properties = new Properties();
                        properties.putValue(
                                CATEGORY_PREFERENCES_KEY, new LinkedHashMap<>(statuses));
                        instance.track(consentUpdatedEventName, properties);
                    }
                });
    }

    /** Stops listening for consent changes and shuts down the underlying provider. */
    public void shutdown() {
        analytics = null;
        provider.setConsentChangeListener(null);
        provider.shutdown();
    }

    /** Source middleware stamping every event; package-visible for tests. */
    Middleware stampingMiddleware() {
        return new Middleware() {
            @Override
            public void intercept(Chain chain) {
                chain.proceed(stamp(chain.payload(), provider.getConsentStatuses()));
            }
        };
    }

    /**
     * Destination middleware dropping events unless every mapped category is consented;
     * package-visible for tests.
     */
    Middleware gatingMiddleware(final String integrationKey, final List<String> categories) {
        return new Middleware() {
            @Override
            public void intercept(Chain chain) {
                BasePayload payload = chain.payload();
                if (HIGHTOUCH_INTEGRATION_KEY.equals(integrationKey)
                        && isConsentUpdatedEvent(payload)) {
                    chain.proceed(payload);
                    return;
                }
                Map<String, ?> statuses = categoryPreferences(payload);
                for (String category : categories) {
                    if (!Boolean.TRUE.equals(statuses.get(category))) {
                        // Not consented: drop the event for this destination by not proceeding.
                        return;
                    }
                }
                chain.proceed(payload);
            }
        };
    }

    /** Preferences stamped on the payload, or the provider's live state if unstamped. */
    private Map<String, ?> categoryPreferences(BasePayload payload) {
        AnalyticsContext context = payload.context();
        if (context != null) {
            ValueMap consent = context.getValueMap(CONSENT_CONTEXT_KEY);
            if (consent != null) {
                ValueMap preferences = consent.getValueMap(CATEGORY_PREFERENCES_KEY);
                if (preferences != null) {
                    return preferences;
                }
            }
        }
        return provider.getConsentStatuses();
    }

    private boolean isConsentUpdatedEvent(BasePayload payload) {
        return payload.type() == BasePayload.Type.track
                && consentUpdatedEventName.equals(((TrackPayload) payload).event());
    }

    /** Copies the payload with {@code context.consent.categoryPreferences} set to the statuses. */
    static BasePayload stamp(
            @NonNull BasePayload payload, @NonNull Map<String, Boolean> statuses) {
        Map<String, Object> context =
                payload.context() != null
                        ? new LinkedHashMap<String, Object>(payload.context())
                        : new LinkedHashMap<String, Object>();
        Map<String, Object> consent = new LinkedHashMap<>();
        Object existingConsent = context.get(CONSENT_CONTEXT_KEY);
        if (existingConsent instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) existingConsent).entrySet()) {
                consent.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        consent.put(CATEGORY_PREFERENCES_KEY, new LinkedHashMap<>(statuses));
        context.put(CONSENT_CONTEXT_KEY, consent);
        return payload.toBuilder().context(context).build();
    }

    /** Fluent builder for {@link ConsentManager}. */
    public static class Builder {

        private final ConsentCategoryProvider provider;
        private final Map<String, List<String>> integrationCategoryMappings =
                new LinkedHashMap<>();
        private String consentUpdatedEventName = DEFAULT_CONSENT_UPDATED_EVENT_NAME;
        private boolean consentUpdatedEventEnabled = true;

        public Builder(@NonNull ConsentCategoryProvider provider) {
            assertNotNull(provider, "provider");
            this.provider = provider;
        }

        /** Requires all of {@code categories} before delivering events to this destination. */
        @NonNull
        public Builder integrationCategoryMapping(
                @NonNull String integrationKey, @NonNull List<String> categories) {
            assertNotNullOrEmpty(integrationKey, "integrationKey");
            assertNotNull(categories, "categories");
            integrationCategoryMappings.put(
                    integrationKey, Collections.unmodifiableList(categories));
            return this;
        }

        /** Bulk variant of {@link #integrationCategoryMapping(String, List)}. */
        @NonNull
        public Builder integrationCategoryMappings(@NonNull Map<String, List<String>> mappings) {
            assertNotNull(mappings, "mappings");
            for (Map.Entry<String, List<String>> mapping : mappings.entrySet()) {
                integrationCategoryMapping(mapping.getKey(), mapping.getValue());
            }
            return this;
        }

        /** Overrides the name of the track event fired when consent changes. */
        @NonNull
        public Builder consentUpdatedEventName(@NonNull String eventName) {
            this.consentUpdatedEventName = assertNotNullOrEmpty(eventName, "eventName");
            return this;
        }

        /** Disables the track event fired when consent changes. */
        @NonNull
        public Builder disableConsentUpdatedEvent() {
            this.consentUpdatedEventEnabled = false;
            return this;
        }

        @NonNull
        public ConsentManager build() {
            return new ConsentManager(
                    provider,
                    Collections.unmodifiableMap(new LinkedHashMap<>(integrationCategoryMappings)),
                    consentUpdatedEventName,
                    consentUpdatedEventEnabled);
        }
    }
}

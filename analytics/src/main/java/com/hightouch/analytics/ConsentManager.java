package com.hightouch.analytics;

import static com.hightouch.analytics.internal.Utils.assertNotNull;
import static com.hightouch.analytics.internal.Utils.assertNotNullOrEmpty;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hightouch.analytics.integrations.BasePayload;
import com.hightouch.analytics.integrations.TrackPayload;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wires CMP-driven consent into an {@link Analytics} instance: stamps every event with {@code
 * context.consent.categoryPreferences}, optionally drops events at the source unless their
 * required categories are consented, gates mapped destinations unless all of their categories are
 * consented (unmapped destinations are never gated), and fires a {@code "Consent Updated"} track
 * event on consent changes (which bypasses source and Hightouch destination gates).
 *
 * <p>Call {@link #attach(Analytics.Builder)} before {@code build()}, then {@link
 * #start(Analytics)} with the built instance. See analytics-onetrust/README.md for a full example.
 */
public class ConsentManager {

    /** Integration key of the Hightouch cloud destination; map it to gate delivery to Hightouch. */
    public static final String HIGHTOUCH_INTEGRATION_KEY = SegmentIntegration.SEGMENT_KEY;

    /** Default name of the track event fired when consent changes. */
    public static final String DEFAULT_CONSENT_UPDATED_EVENT_NAME = "Consent Updated";

    /**
     * Track event names the Android SDK emits internally for app lifecycle. Used by {@link
     * Builder#lifecycleEventCategories(List)}.
     */
    public static final List<String> LIFECYCLE_EVENT_NAMES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "Application Installed",
                            "Application Updated",
                            "Application Opened",
                            "Application Backgrounded",
                            "Deep Link Opened"));

    static final String CONSENT_CONTEXT_KEY = "consent";
    static final String CATEGORY_PREFERENCES_KEY = "categoryPreferences";

    private final ConsentCategoryProvider provider;
    private final Map<String, List<String>> integrationCategoryMappings;
    private final Map<String, List<String>> eventCategoryMappings;
    private final Map<BasePayload.Type, List<String>> eventTypeCategoryMappings;
    private final List<String> defaultEventCategories;
    private final String consentUpdatedEventName;
    private final boolean consentUpdatedEventEnabled;
    private volatile Analytics analytics;

    ConsentManager(
            @NonNull ConsentCategoryProvider provider,
            @NonNull Map<String, List<String>> integrationCategoryMappings,
            @NonNull Map<String, List<String>> eventCategoryMappings,
            @NonNull Map<BasePayload.Type, List<String>> eventTypeCategoryMappings,
            @Nullable List<String> defaultEventCategories,
            @NonNull String consentUpdatedEventName,
            boolean consentUpdatedEventEnabled) {
        this.provider = provider;
        this.integrationCategoryMappings = integrationCategoryMappings;
        this.eventCategoryMappings = eventCategoryMappings;
        this.eventTypeCategoryMappings = eventTypeCategoryMappings;
        this.defaultEventCategories = defaultEventCategories;
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

    /**
     * Source middleware that stamps every event and drops it when required categories are not
     * consented. Package-visible for tests.
     */
    Middleware stampingMiddleware() {
        return new Middleware() {
            @Override
            public void intercept(Chain chain) {
                BasePayload stamped = stamp(chain.payload(), provider.getConsentStatuses());
                if (isConsentUpdatedEvent(stamped)) {
                    chain.proceed(stamped);
                    return;
                }
                List<String> required = requiredCategories(stamped);
                if (required != null && !allConsented(categoryPreferences(stamped), required)) {
                    return;
                }
                chain.proceed(stamped);
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
                if (!allConsented(categoryPreferences(payload), categories)) {
                    return;
                }
                chain.proceed(payload);
            }
        };
    }

    /**
     * Categories this event must have granted before it may proceed past source middleware. {@code
     * null} means no event-level requirement (destination gating may still apply). Package-visible
     * for tests.
     */
    @Nullable
    List<String> requiredCategories(BasePayload payload) {
        List<String> fromContext = requiredCategoriesFromContext(payload);
        if (fromContext != null) {
            return fromContext;
        }
        if (payload.type() == BasePayload.Type.track) {
            List<String> mapped = eventCategoryMappings.get(((TrackPayload) payload).event());
            if (mapped != null) {
                return mapped;
            }
        }
        List<String> byType = eventTypeCategoryMappings.get(payload.type());
        if (byType != null) {
            return byType;
        }
        return defaultEventCategories;
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

    static boolean allConsented(Map<String, ?> statuses, List<String> categories) {
        for (String category : categories) {
            if (!Boolean.TRUE.equals(statuses.get(category))) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static List<String> requiredCategoriesFromContext(BasePayload payload) {
        AnalyticsContext context = payload.context();
        if (context == null
                || !context.containsKey(Options.CONSENT_REQUIRED_CATEGORIES_KEY)) {
            return null;
        }
        return toStringList(context.get(Options.CONSENT_REQUIRED_CATEGORIES_KEY));
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (value instanceof String[]) {
            return Arrays.asList((String[]) value);
        }
        if (value instanceof String) {
            return Collections.singletonList((String) value);
        }
        return Collections.emptyList();
    }

    /** Fluent builder for {@link ConsentManager}. */
    public static class Builder {

        private final ConsentCategoryProvider provider;
        private final Map<String, List<String>> integrationCategoryMappings =
                new LinkedHashMap<>();
        private final Map<String, List<String>> eventCategoryMappings = new LinkedHashMap<>();
        private final Map<BasePayload.Type, List<String>> eventTypeCategoryMappings =
                new LinkedHashMap<>();
        private List<String> defaultEventCategories;
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
                    integrationKey, Collections.unmodifiableList(new ArrayList<>(categories)));
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

        /**
         * Requires all of {@code categories} before a track event with this name may proceed past
         * source middleware. Takes precedence over {@link #eventTypeCategoryMapping} and {@link
         * #defaultEventCategories(List)}. Later calls for the same name overwrite.
         */
        @NonNull
        public Builder eventCategoryMapping(
                @NonNull String eventName, @NonNull List<String> categories) {
            assertNotNullOrEmpty(eventName, "eventName");
            assertNotNull(categories, "categories");
            eventCategoryMappings.put(
                    eventName, Collections.unmodifiableList(new ArrayList<>(categories)));
            return this;
        }

        /** Bulk variant of {@link #eventCategoryMapping(String, List)}. */
        @NonNull
        public Builder eventCategoryMappings(@NonNull Map<String, List<String>> mappings) {
            assertNotNull(mappings, "mappings");
            for (Map.Entry<String, List<String>> mapping : mappings.entrySet()) {
                eventCategoryMapping(mapping.getKey(), mapping.getValue());
            }
            return this;
        }

        /**
         * Requires all of {@code categories} for every {@link #LIFECYCLE_EVENT_NAMES} track event.
         * Equivalent to calling {@link #eventCategoryMapping} for each name; a later specific
         * mapping for one of those names overwrites it.
         */
        @NonNull
        public Builder lifecycleEventCategories(@NonNull List<String> categories) {
            assertNotNull(categories, "categories");
            List<String> copy = Collections.unmodifiableList(new ArrayList<>(categories));
            for (String eventName : LIFECYCLE_EVENT_NAMES) {
                eventCategoryMappings.put(eventName, copy);
            }
            return this;
        }

        /**
         * Requires all of {@code categories} before events of this payload type may proceed, when
         * no event-name mapping or per-call {@link Options#requireConsentCategories} applies.
         * Useful for automatic {@link BasePayload.Type#screen} calls from {@code
         * recordScreenViews()}.
         */
        @NonNull
        public Builder eventTypeCategoryMapping(
                @NonNull BasePayload.Type type, @NonNull List<String> categories) {
            assertNotNull(type, "type");
            assertNotNull(categories, "categories");
            eventTypeCategoryMappings.put(
                    type, Collections.unmodifiableList(new ArrayList<>(categories)));
            return this;
        }

        /**
         * Categories required for events with no name, type, or per-call mapping. {@code null}
         * (the default) means unmapped events are not dropped at the source.
         */
        @NonNull
        public Builder defaultEventCategories(@Nullable List<String> categories) {
            this.defaultEventCategories =
                    categories == null
                            ? null
                            : Collections.unmodifiableList(new ArrayList<>(categories));
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
                    Collections.unmodifiableMap(new LinkedHashMap<>(eventCategoryMappings)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(eventTypeCategoryMappings)),
                    defaultEventCategories,
                    consentUpdatedEventName,
                    consentUpdatedEventEnabled);
        }
    }
}

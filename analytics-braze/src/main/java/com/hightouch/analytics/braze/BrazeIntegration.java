package com.hightouch.analytics.braze;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.braze.Braze;
import com.braze.BrazeUser;
import com.braze.configuration.BrazeConfig;
import com.braze.events.IValueCallback;
import com.braze.models.outgoing.AttributionData;
import com.hightouch.analytics.Analytics;
import com.hightouch.analytics.Properties;
import com.hightouch.analytics.ValueMap;
import com.hightouch.analytics.braze.BrazeMapping.Attribute;
import com.hightouch.analytics.braze.BrazeMapping.UserUpdate;
import com.hightouch.analytics.integrations.IdentifyPayload;
import com.hightouch.analytics.integrations.Integration;
import com.hightouch.analytics.integrations.Logger;
import com.hightouch.analytics.integrations.ScreenPayload;
import com.hightouch.analytics.integrations.TrackPayload;
import com.hightouch.analytics.internal.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Forwards Hightouch events to the Braze Android SDK. The integration only sends data; use the
 * Braze instance from {@link Factory#getBraze()} (or {@link Analytics#onIntegrationReady}) for
 * in-app message and Content Card UI.
 *
 * <pre><code>
 * BrazeIntegration.Factory braze =
 *     BrazeIntegration.builder(this, "BRAZE_API_KEY", "sdk.iad-01.braze.com").build();
 * Analytics analytics = new Analytics.Builder(this, "WRITE_KEY").use(braze).build();
 * </code></pre>
 */
public class BrazeIntegration extends Integration<Braze> {

    /** The integration key, also used for per-event opt-out in {@code Options}. */
    public static final String KEY = "Appboy";

    static final String PREFERENCES_NAME = "hightouch-braze";
    private static final String USER_ID_PREFERENCE = "userId";
    private static final String ATTRIBUTES_PREFERENCE = "attributes";

    /** Configures Braze with an API key and SDK endpoint when the factory is built. */
    public static Builder builder(
            @NonNull Context context, @NonNull String apiKey, @NonNull String endpoint) {
        return builder(
                context,
                new BrazeConfig.Builder().setApiKey(apiKey).setCustomEndpoint(endpoint).build());
    }

    /** Configures Braze with {@code config} when the factory is built. */
    public static Builder builder(@NonNull Context context, @NonNull BrazeConfig config) {
        return new Builder(context, config, null);
    }

    /** Uses a Braze instance you already configured, without configuring Braze again. */
    public static Builder builder(@NonNull Context context, @NonNull Braze braze) {
        return new Builder(context, null, braze);
    }

    public static final class Builder {
        private final Context context;
        private final BrazeConfig config;
        private final Braze braze;
        private boolean trackSessions;
        PurchaseGrouping purchaseGrouping = PurchaseGrouping.perProduct(ProductIdentifier.SKU);
        boolean forwardScreenViews;
        PurchaseDetection purchaseDetection =
                PurchaseDetection.eventNames("Order Completed", "Completed Order");
        PurchaseTransformer purchaseTransformer;

        Builder(Context context, BrazeConfig config, Braze braze) {
            if (context == null) {
                throw new IllegalArgumentException("context must not be null.");
            }
            this.context = context.getApplicationContext();
            this.config = config;
            this.braze = braze;
            this.trackSessions = braze == null;
        }

        /**
         * Open and close Braze sessions as activities start and stop. Defaults to {@code true} when
         * this integration configures Braze, and {@code false} when you pass in your own instance
         * (and handle sessions yourself, for example with {@code
         * BrazeActivityLifecycleCallbackListener}).
         */
        public Builder trackSessions(boolean trackSessions) {
            this.trackSessions = trackSessions;
            return this;
        }

        /**
         * Whether a purchase event logs one purchase per product or one per order. Defaults to
         * {@link PurchaseGrouping#perProduct} with {@link ProductIdentifier#SKU}.
         */
        public Builder purchaseGrouping(@NonNull PurchaseGrouping grouping) {
            this.purchaseGrouping = grouping;
            return this;
        }

        /** Log {@code screen} calls as Braze custom events. */
        public Builder forwardScreenViews(boolean forwardScreenViews) {
            this.forwardScreenViews = forwardScreenViews;
            return this;
        }

        /**
         * Which {@code track} calls are logged as purchases. Defaults to {@link
         * PurchaseDetection#eventNames} with {@code Order Completed} and {@code Completed Order}.
         */
        public Builder purchaseDetection(@NonNull PurchaseDetection detection) {
            this.purchaseDetection = detection;
            return this;
        }

        /** Change or skip each purchase after the default mapping, before it's logged. */
        public Builder purchaseTransformer(@NonNull PurchaseTransformer transformer) {
            this.purchaseTransformer = transformer;
            return this;
        }

        /**
         * Configures Braze (unless you passed in an instance) and returns the factory to pass to
         * {@link Analytics.Builder#use}. Call this in {@code Application#onCreate} so Braze is
         * ready, and sessions are tracked, before the first activity starts.
         */
        public Factory build() {
            Braze instance = braze;
            if (instance == null) {
                Braze.configure(context, config);
                instance = Braze.getInstance(context);
            }
            if (trackSessions) {
                ((Application) context)
                        .registerActivityLifecycleCallbacks(new SessionTracker(instance));
            }
            return new Factory(instance, this);
        }
    }

    public static final class Factory implements Integration.LocallyConfiguredFactory {
        private final Braze braze;
        private final Builder options;

        Factory(Braze braze, Builder options) {
            this.braze = braze;
            this.options = options;
        }

        /** The Braze instance this integration forwards to. */
        public Braze getBraze() {
            return braze;
        }

        @Override
        public Integration<?> create(ValueMap settings, Analytics analytics) {
            return new BrazeIntegration(
                    braze,
                    options,
                    analytics
                            .getApplication()
                            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
                    analytics.logger(KEY));
        }

        @NonNull
        @Override
        public String key() {
            return KEY;
        }
    }

    private final Braze braze;
    private final SharedPreferences preferences;
    private final Logger logger;
    private final boolean forwardScreenViews;
    private final BrazeMapping mapping;

    BrazeIntegration(Braze braze, Builder options, SharedPreferences preferences, Logger logger) {
        this.braze = braze;
        this.preferences = preferences;
        this.logger = logger;
        this.forwardScreenViews = options.forwardScreenViews;
        this.mapping = new BrazeMapping(braze, options, logger);
    }

    @Override
    public Braze getUnderlyingInstance() {
        return braze;
    }

    @Override
    public void identify(IdentifyPayload identify) {
        try {
            String userId = identify.userId();
            if (!Utils.isNullOrEmpty(userId)
                    && !userId.equals(preferences.getString(USER_ID_PREFERENCE, null))) {
                braze.changeUser(userId);
                preferences.edit().clear().putString(USER_ID_PREFERENCE, userId).apply();
            }

            JSONObject sent = sentAttributes();
            List<UserUpdate> updates = new ArrayList<>();
            for (Map.Entry<String, Attribute> entry : mapping.attributes(identify.traits()).entrySet()) {
                String signature = entry.getValue().signature;
                if (!signature.equals(sent.optString(entry.getKey(), null))) {
                    updates.add(entry.getValue().update);
                    sent.put(entry.getKey(), signature);
                }
            }
            if (!updates.isEmpty()) {
                preferences.edit().putString(ATTRIBUTES_PREFERENCE, sent.toString()).apply();
                updateUser(updates);
            }
        } catch (Exception e) {
            logger.error(e, "Unable to forward identify to Braze.");
        }
    }

    @Override
    public void track(TrackPayload track) {
        try {
            String event = track.event();
            Properties properties = track.properties();
            if ("Install Attributed".equals(event) && properties.get("campaign") != null) {
                setAttributionData(properties.get("campaign"));
            }
            if (mapping.isPurchase(track)) {
                mapping.logPurchases(track, properties);
            } else {
                braze.logCustomEvent(event, mapping.brazeProperties(properties));
            }
        } catch (Exception e) {
            logger.error(e, "Unable to forward track %s to Braze.", track.event());
        }
    }

    @Override
    public void screen(ScreenPayload screen) {
        if (!forwardScreenViews) {
            return;
        }
        try {
            braze.logCustomEvent(screen.event(), mapping.brazeProperties(screen.properties()));
        } catch (Exception e) {
            logger.error(e, "Unable to forward screen %s to Braze.", screen.event());
        }
    }

    @Override
    public void flush() {
        try {
            braze.requestImmediateDataFlush();
        } catch (Exception e) {
            logger.error(e, "Unable to flush Braze.");
        }
    }

    @Override
    public void reset() {
        preferences.edit().clear().apply();
    }

    private JSONObject sentAttributes() {
        String json = preferences.getString(ATTRIBUTES_PREFERENCE, null);
        if (json != null) {
            try {
                return new JSONObject(json);
            } catch (JSONException e) {
                logger.error(e, "Discarding unreadable Braze attribute cache.");
            }
        }
        return new JSONObject();
    }

    private void updateUser(final List<UserUpdate> updates) {
        braze.getCurrentUser(
                new IValueCallback<BrazeUser>() {
                    @Override
                    public void onSuccess(BrazeUser user) {
                        for (UserUpdate update : updates) {
                            try {
                                update.apply(user);
                            } catch (Exception e) {
                                logger.error(e, "Unable to update the Braze user.");
                            }
                        }
                    }

                    @Override
                    public void onError() {
                        logger.info("Unable to get the current Braze user.");
                    }
                });
    }

    private void setAttributionData(Object campaign) {
        Map<?, ?> fields = campaign instanceof Map ? (Map<?, ?>) campaign : Collections.emptyMap();
        final AttributionData data =
                new AttributionData(
                        string(fields.get("source")),
                        string(fields.get("name")),
                        string(fields.get("ad_group")),
                        string(fields.get("ad_creative")));
        updateUser(Collections.<UserUpdate>singletonList(user -> user.setAttributionData(data)));
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static final class SessionTracker implements Application.ActivityLifecycleCallbacks {
        private final Braze braze;

        SessionTracker(Braze braze) {
            this.braze = braze;
        }

        @Override
        public void onActivityStarted(Activity activity) {
            braze.openSession(activity);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            braze.closeSession(activity);
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

        @Override
        public void onActivityResumed(Activity activity) {}

        @Override
        public void onActivityPaused(Activity activity) {}

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

        @Override
        public void onActivityDestroyed(Activity activity) {}
    }
}

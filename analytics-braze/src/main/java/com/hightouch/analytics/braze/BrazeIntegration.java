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
import com.braze.enums.Gender;
import com.braze.enums.Month;
import com.braze.enums.NotificationSubscriptionType;
import com.braze.events.IValueCallback;
import com.braze.models.outgoing.AttributionData;
import com.braze.models.outgoing.BrazeProperties;
import com.hightouch.analytics.Analytics;
import com.hightouch.analytics.Properties;
import com.hightouch.analytics.Traits;
import com.hightouch.analytics.ValueMap;
import com.hightouch.analytics.integrations.IdentifyPayload;
import com.hightouch.analytics.integrations.Integration;
import com.hightouch.analytics.integrations.Logger;
import com.hightouch.analytics.integrations.ScreenPayload;
import com.hightouch.analytics.integrations.TrackPayload;
import com.hightouch.analytics.internal.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Forwards Hightouch events to the Braze Android SDK, following the mapping of mParticle's Braze
 * kit. The integration only sends data; use the Braze instance from {@link Factory#getBraze()} (or
 * {@link Analytics#onIntegrationReady}) for in-app message and Content Card UI.
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

    /** Which product field becomes the {@code productId} of a Braze purchase. */
    public enum ProductIdentifier {
        SKU,
        NAME
    }

    static final String PREFERENCES_NAME = "hightouch-braze";
    private static final String USER_ID_PREFERENCE = "userId";
    private static final String ATTRIBUTES_PREFERENCE = "attributes";
    private static final String BUNDLED_PURCHASE_PRODUCT_ID = "eCommerce - purchase";
    private static final Pattern DATE_ONLY = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Set<String> MAPPED_PRODUCT_FIELDS =
            new HashSet<>(
                    Arrays.asList(
                            "sku",
                            "name",
                            "brand",
                            "category",
                            "variant",
                            "position",
                            "coupon",
                            "price",
                            "quantity"));
    private static final Object ABSENT = new Object();
    private static final Object UNSUPPORTED = new Object();

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
        ProductIdentifier purchaseProductIdentifier = ProductIdentifier.SKU;
        boolean bundleCommerceEvents;
        boolean forwardScreenViews;
        boolean logPurchaseWhenRevenuePresent;
        boolean stringifyAttributeValues;

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

        /** Which product field becomes the purchase {@code productId}. Defaults to SKU. */
        public Builder purchaseProductIdentifier(@NonNull ProductIdentifier identifier) {
            this.purchaseProductIdentifier = identifier;
            return this;
        }

        /** Log one {@code eCommerce - purchase} per order instead of one purchase per product. */
        public Builder bundleCommerceEvents(boolean bundleCommerceEvents) {
            this.bundleCommerceEvents = bundleCommerceEvents;
            return this;
        }

        /** Log {@code screen} calls as Braze custom events. */
        public Builder forwardScreenViews(boolean forwardScreenViews) {
            this.forwardScreenViews = forwardScreenViews;
            return this;
        }

        /** Log any {@code track} call with a non-zero {@code revenue} as a purchase. */
        public Builder logPurchaseWhenRevenuePresent(boolean logPurchaseWhenRevenuePresent) {
            this.logPurchaseWhenRevenuePresent = logPurchaseWhenRevenuePresent;
            return this;
        }

        /** Send attribute and property values as strings. */
        public Builder stringifyAttributeValues(boolean stringifyAttributeValues) {
            this.stringifyAttributeValues = stringifyAttributeValues;
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
    private final ProductIdentifier purchaseProductIdentifier;
    private final boolean bundleCommerceEvents;
    private final boolean forwardScreenViews;
    private final boolean logPurchaseWhenRevenuePresent;
    private final boolean stringifyAttributeValues;

    BrazeIntegration(Braze braze, Builder options, SharedPreferences preferences, Logger logger) {
        this.braze = braze;
        this.preferences = preferences;
        this.logger = logger;
        this.purchaseProductIdentifier = options.purchaseProductIdentifier;
        this.bundleCommerceEvents = options.bundleCommerceEvents;
        this.forwardScreenViews = options.forwardScreenViews;
        this.logPurchaseWhenRevenuePresent = options.logPurchaseWhenRevenuePresent;
        this.stringifyAttributeValues = options.stringifyAttributeValues;
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
            for (Map.Entry<String, Attribute> entry : attributes(identify.traits()).entrySet()) {
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
            String event = stripDollars(track.event());
            Properties properties = track.properties();
            if ("Install Attributed".equals(event) && properties.get("campaign") != null) {
                setAttributionData(properties.get("campaign"));
            }
            if ("Order Completed".equals(event)
                    || "Completed Order".equals(event)
                    || (logPurchaseWhenRevenuePresent
                            && number(properties.get("revenue"), 0) != 0)) {
                logPurchase(event, properties);
            } else {
                braze.logCustomEvent(event, brazeProperties(properties));
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
            braze.logCustomEvent(
                    stripDollars(screen.event()), brazeProperties(screen.properties()));
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

    private interface UserUpdate {
        void apply(BrazeUser user);
    }

    private interface Setter<T> {
        void set(BrazeUser user, T value);
    }

    private static final class Attribute {
        final String signature;
        final UserUpdate update;

        Attribute(String signature, UserUpdate update) {
            this.signature = signature;
            this.update = update;
        }
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

    /** Maps traits to Braze user updates, keyed by the slot used for deduplication. */
    private Map<String, Attribute> attributes(Traits traits) {
        Map<String, Attribute> attributes = new LinkedHashMap<>();
        Set<String> consumed = new HashSet<>(Arrays.asList("userId", "anonymousId", "address"));
        Map<?, ?> address =
                traits.get("address") instanceof Map
                        ? (Map<?, ?>) traits.get("address")
                        : Collections.emptyMap();

        putString(
                attributes,
                "firstName",
                find(traits, consumed, "firstName", "first_name", "$FirstName"),
                BrazeUser::setFirstName);
        putString(
                attributes,
                "lastName",
                find(traits, consumed, "lastName", "last_name", "$LastName"),
                BrazeUser::setLastName);
        putString(
                attributes, "email", find(traits, consumed, "email", "Email"), BrazeUser::setEmail);
        putString(
                attributes,
                "phone",
                find(traits, consumed, "phone", "$Mobile"),
                BrazeUser::setPhoneNumber);
        Object city = find(traits, consumed, "home_city", "$City");
        putString(
                attributes,
                "homeCity",
                address.containsKey("city") ? address.get("city") : city,
                BrazeUser::setHomeCity);
        Object country = find(traits, consumed, "country", "$Country");
        putString(
                attributes,
                "country",
                address.containsKey("country") ? address.get("country") : country,
                BrazeUser::setCountry);
        Object zip = find(traits, consumed, "$Zip");
        if (address.containsKey("postalCode")) {
            zip = address.get("postalCode");
        }
        if (zip != ABSENT) {
            putCustom(attributes, "Zip", zip);
        }

        Object gender = find(traits, consumed, "gender", "$Gender");
        if (gender != ABSENT) {
            final Gender brazeGender = gender(gender);
            if (brazeGender == null) {
                logger.info("Dropping unsupported gender %s.", gender);
            } else {
                put(
                        attributes,
                        "reserved.gender",
                        brazeGender.name(),
                        brazeGender,
                        BrazeUser::setGender);
            }
        }

        Object age = find(traits, consumed, "age", "$Age");
        if (age instanceof Number) {
            int year = Calendar.getInstance().get(Calendar.YEAR) - ((Number) age).intValue();
            putDateOfBirth(attributes, "age", new int[] {year, Calendar.JANUARY, 1});
        } else if (age != ABSENT) {
            logger.info("Dropping non-numeric age %s.", age);
        }
        Object birthday = find(traits, consumed, "birthday", "dob");
        if (birthday != ABSENT) {
            int[] date = dateParts(birthday);
            if (date == null) {
                logger.info("Dropping birthday %s that could not be parsed as a date.", birthday);
            } else {
                putDateOfBirth(attributes, "birthday", date);
            }
        }

        putSubscription(
                attributes,
                "emailSubscribe",
                find(traits, consumed, "email_subscribe"),
                BrazeUser::setEmailNotificationSubscriptionType);
        putSubscription(
                attributes,
                "pushSubscribe",
                find(traits, consumed, "push_subscribe"),
                BrazeUser::setPushNotificationSubscriptionType);

        for (Map.Entry<String, Object> entry : traits.entrySet()) {
            if (!consumed.contains(entry.getKey())) {
                putCustom(attributes, stripDollars(entry.getKey()), entry.getValue());
            }
        }
        return attributes;
    }

    /** Returns the value of the first key present, or {@link #ABSENT}, and marks all as mapped. */
    private static Object find(Map<String, Object> traits, Set<String> consumed, String... keys) {
        Object value = ABSENT;
        for (String key : keys) {
            consumed.add(key);
            if (value == ABSENT && traits.containsKey(key)) {
                value = traits.get(key);
            }
        }
        return value;
    }

    private static <T> void put(
            Map<String, Attribute> attributes,
            String key,
            String signature,
            final T value,
            final Setter<T> setter) {
        attributes.put(key, new Attribute(signature, user -> setter.set(user, value)));
    }

    private static void putString(
            Map<String, Attribute> attributes, String name, Object value, Setter<String> setter) {
        if (value != ABSENT) {
            String string = value == null ? null : String.valueOf(value);
            put(attributes, "reserved." + name, String.valueOf(string), string, setter);
        }
    }

    private static void putDateOfBirth(Map<String, Attribute> attributes, String name, int[] date) {
        put(
                attributes,
                "reserved." + name,
                Arrays.toString(date),
                date,
                (user, d) -> user.setDateOfBirth(d[0], Month.getMonth(d[1]), d[2]));
    }

    private void putSubscription(
            Map<String, Attribute> attributes,
            String name,
            Object value,
            Setter<NotificationSubscriptionType> setter) {
        if (value == ABSENT) {
            return;
        }
        NotificationSubscriptionType type = null;
        if ("opted_in".equals(value)) {
            type = NotificationSubscriptionType.OPTED_IN;
        } else if ("subscribed".equals(value)) {
            type = NotificationSubscriptionType.SUBSCRIBED;
        } else if ("unsubscribed".equals(value)) {
            type = NotificationSubscriptionType.UNSUBSCRIBED;
        }
        if (type == null) {
            logger.info("Dropping unsupported %s value %s.", name, value);
        } else {
            put(attributes, "reserved." + name, type.name(), type, setter);
        }
    }

    private void putCustom(Map<String, Attribute> attributes, final String key, Object value) {
        if (key.isEmpty()) {
            return;
        }
        final Object normalized = attributeValue(value);
        if (normalized == UNSUPPORTED) {
            logger.info("Dropping attribute %s with unsupported value %s.", key, value);
            return;
        }
        String signature;
        if (normalized == null) {
            signature = "null";
        } else if (normalized instanceof String[]) {
            signature = "String[]:" + new JSONArray(Arrays.asList((String[]) normalized));
        } else if (normalized instanceof Date) {
            signature = "Date:" + ((Date) normalized).getTime();
        } else {
            signature = normalized.getClass().getSimpleName() + ":" + normalized;
        }
        attributes.put(
                "custom." + key,
                new Attribute(signature, user -> setCustom(user, key, normalized)));
    }

    private Object attributeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return toJson(value).toString();
        }
        if (value instanceof Collection || value instanceof Object[]) {
            List<String> strings = new ArrayList<>();
            for (Object element : elements(value)) {
                strings.add(
                        element instanceof Map
                                ? toJson(element).toString()
                                : String.valueOf(element));
            }
            return strings.toArray(new String[0]);
        }
        if (value instanceof Date) {
            return stringifyAttributeValues ? Utils.toISO8601String((Date) value) : value;
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Number) {
            return stringifyAttributeValues ? String.valueOf(value) : scalar(value);
        }
        return UNSUPPORTED;
    }

    private static void setCustom(BrazeUser user, String key, Object value) {
        if (value == null) {
            user.unsetCustomUserAttribute(key);
        } else if (value instanceof String) {
            user.setCustomUserAttribute(key, (String) value);
        } else if (value instanceof Boolean) {
            user.setCustomUserAttribute(key, (boolean) (Boolean) value);
        } else if (value instanceof Integer) {
            user.setCustomUserAttribute(key, (int) (Integer) value);
        } else if (value instanceof Long) {
            user.setCustomUserAttribute(key, (long) (Long) value);
        } else if (value instanceof Float) {
            user.setCustomUserAttribute(key, (float) (Float) value);
        } else if (value instanceof Double) {
            user.setCustomUserAttribute(key, (double) (Double) value);
        } else if (value instanceof Date) {
            user.setCustomUserAttributeToSecondsFromEpoch(key, ((Date) value).getTime() / 1000);
        } else if (value instanceof String[]) {
            user.setCustomAttributeArray(key, (String[]) value);
        }
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

    private void logPurchase(String event, Properties properties) {
        Object currencyValue = properties.get("currency");
        String currency =
                currencyValue instanceof String && ((String) currencyValue).length() == 3
                        ? (String) currencyValue
                        : "USD";
        Map<String, Object> order = new LinkedHashMap<>(properties);
        order.remove("products");
        order.remove("currency");
        Object orderId =
                properties.containsKey("order_id")
                        ? properties.get("order_id")
                        : properties.get("orderId");
        if (orderId != null) {
            order.put("Transaction Id", orderId);
        }
        BigDecimal total =
                BigDecimal.valueOf(
                        number(
                                properties.containsKey("revenue")
                                        ? properties.get("revenue")
                                        : properties.get("total"),
                                0));
        List<Map<String, Object>> products = products(properties.get("products"));

        if (bundleCommerceEvents) {
            for (Map<String, Object> product : products) {
                double amount =
                        number(product.get("price"), 0) * number(product.get("quantity"), 1);
                rename(product, "sku", "Id");
                rename(product, "coupon", "Coupon Code");
                product.put("Total Product Amount", amount);
            }
            order.put("products", products);
            braze.logPurchase(
                    BUNDLED_PURCHASE_PRODUCT_ID, currency, total, 1, brazeProperties(order));
            return;
        }

        if (products.isEmpty()) {
            braze.logPurchase(event, currency, total, 1, brazeProperties(order));
            return;
        }

        for (Map<String, Object> product : products) {
            String productId = productId(product);
            if (productId == null) {
                logger.info("Dropping purchase of a product without an identifier: %s", product);
                continue;
            }
            Map<String, Object> purchase = new LinkedHashMap<>(order);
            putIfPresent(purchase, "Name", product.get("name"));
            putIfPresent(purchase, "Brand", product.get("brand"));
            putIfPresent(purchase, "Category", product.get("category"));
            putIfPresent(purchase, "Variant", product.get("variant"));
            putIfPresent(purchase, "Position", product.get("position"));
            putIfPresent(purchase, "Coupon Code", product.get("coupon"));
            for (Map.Entry<String, Object> field : product.entrySet()) {
                if (!MAPPED_PRODUCT_FIELDS.contains(field.getKey())) {
                    purchase.put(field.getKey(), field.getValue());
                }
            }
            braze.logPurchase(
                    productId,
                    currency,
                    BigDecimal.valueOf(number(product.get("price"), 0)),
                    (int) number(product.get("quantity"), 1),
                    brazeProperties(purchase));
        }
    }

    private String productId(Map<String, Object> product) {
        List<String> keys = new ArrayList<>(Arrays.asList("sku", "product_id", "id"));
        if (purchaseProductIdentifier == ProductIdentifier.NAME) {
            keys.add(0, "name");
        }
        for (String key : keys) {
            Object id = product.get(key);
            if (id != null && !String.valueOf(id).isEmpty()) {
                return String.valueOf(id);
            }
        }
        return null;
    }

    private static List<Map<String, Object>> products(Object value) {
        List<Map<String, Object>> products = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object product : (Collection<?>) value) {
                if (product instanceof Map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> field : ((Map<?, ?>) product).entrySet()) {
                        copy.put(String.valueOf(field.getKey()), field.getValue());
                    }
                    products.add(copy);
                }
            }
        }
        return products;
    }

    private BrazeProperties brazeProperties(Map<String, ?> properties) {
        BrazeProperties brazeProperties = new BrazeProperties();
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            String key = stripDollars(entry.getKey());
            Object value = propertyValue(entry.getValue());
            if (!key.isEmpty() && value != null) {
                brazeProperties.addProperty(key, value);
            }
        }
        return brazeProperties;
    }

    private Object propertyValue(Object value) {
        if (value instanceof Map || value instanceof Collection || value instanceof Object[]) {
            return toJson(value);
        }
        if (stringifyAttributeValues && value instanceof Date) {
            return Utils.toISO8601String((Date) value);
        }
        if (stringifyAttributeValues
                && (value instanceof String
                        || value instanceof Number
                        || value instanceof Boolean)) {
            return String.valueOf(value);
        }
        return scalar(value);
    }

    /**
     * Converts nested maps and collections explicitly, because Android's {@code
     * JSONArray(Collection)} doesn't convert nested maps into {@code JSONObject}s.
     */
    private static Object toJson(Object value) {
        if (value instanceof Map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                try {
                    object.put(String.valueOf(entry.getKey()), toJson(entry.getValue()));
                } catch (JSONException ignored) {
                    // Only thrown for NaN and infinite numbers, which JSON can't represent.
                }
            }
            return object;
        }
        if (value instanceof Collection || value instanceof Object[]) {
            JSONArray array = new JSONArray();
            for (Object element : elements(value)) {
                array.put(toJson(element));
            }
            return array;
        }
        return value == null ? JSONObject.NULL : value;
    }

    private static Collection<?> elements(Object value) {
        return value instanceof Collection
                ? (Collection<?>) value
                : Arrays.asList((Object[]) value);
    }

    /** Braze accepts int, long, float, and double; other numbers are sent as doubles. */
    private static Object scalar(Object value) {
        if (value instanceof Number
                && !(value instanceof Integer
                        || value instanceof Long
                        || value instanceof Float
                        || value instanceof Double)) {
            return ((Number) value).doubleValue();
        }
        return value;
    }

    private static Gender gender(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        switch (((String) value).toLowerCase(Locale.US)) {
            case "m":
            case "male":
                return Gender.MALE;
            case "f":
            case "female":
                return Gender.FEMALE;
            case "o":
            case "other":
                return Gender.OTHER;
            case "u":
            case "unknown":
                return Gender.UNKNOWN;
            case "n":
            case "not_applicable":
                return Gender.NOT_APPLICABLE;
            case "p":
            case "prefer_not_to_say":
                return Gender.PREFER_NOT_TO_SAY;
            default:
                return null;
        }
    }

    /**
     * Returns {year, zero-based month, day}. A plain {@code yyyy-MM-dd} is used as written, so the
     * birthday doesn't shift with the device's time zone.
     */
    private static int[] dateParts(Object value) {
        Date date = null;
        if (value instanceof Date) {
            date = (Date) value;
        } else if (value instanceof String) {
            Matcher matcher = DATE_ONLY.matcher((String) value);
            if (matcher.matches()) {
                return new int[] {
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)) - 1,
                    Integer.parseInt(matcher.group(3))
                };
            }
            try {
                date = Utils.parseISO8601Date((String) value);
            } catch (Exception ignored) {
            }
        }
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return new int[] {
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        };
    }

    private static double number(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static void rename(Map<String, Object> map, String from, String to) {
        if (map.containsKey(from)) {
            map.put(to, map.remove(from));
        }
    }

    private static String stripDollars(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        while (start < value.length() && value.charAt(start) == '$') {
            start++;
        }
        return value.substring(start);
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

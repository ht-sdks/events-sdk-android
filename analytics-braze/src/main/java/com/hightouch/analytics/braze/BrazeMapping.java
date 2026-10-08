package com.hightouch.analytics.braze;

import com.braze.Braze;
import com.braze.BrazeUser;
import com.braze.enums.Gender;
import com.braze.enums.Month;
import com.braze.enums.NotificationSubscriptionType;
import com.braze.models.outgoing.BrazeProperties;
import com.hightouch.analytics.Properties;
import com.hightouch.analytics.Traits;
import com.hightouch.analytics.integrations.Logger;
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

/** Internal trait and purchase mapping for the Braze integration. */
final class BrazeMapping {
    private static final Pattern DATE_ONLY = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Object ABSENT = new Object();
    private static final Object UNSUPPORTED = new Object();

    private final Braze braze;
    private final Logger logger;
    private final PurchaseGrouping purchaseGrouping;
    private final PurchaseDetection purchaseDetection;
    private final PurchaseTransformer purchaseTransformer;

    BrazeMapping(Braze braze, BrazeIntegration.Builder options, Logger logger) {
        this.braze = braze;
        this.logger = logger;
        this.purchaseGrouping = options.purchaseGrouping;
        this.purchaseDetection = options.purchaseDetection;
        this.purchaseTransformer = options.purchaseTransformer;
    }

    interface UserUpdate {
        void apply(BrazeUser user);
    }

    private interface Setter<T> {
        void set(BrazeUser user, T value);
    }

    static final class Attribute {
        final String signature;
        final UserUpdate update;

        Attribute(String signature, UserUpdate update) {
            this.signature = signature;
            this.update = update;
        }
    }

    /** Maps traits to Braze user updates, keyed by the slot used for deduplication. */
    Map<String, Attribute> attributes(Traits traits) {
        Map<String, Attribute> attributes = new LinkedHashMap<>();
        Set<String> consumed = new HashSet<>(Arrays.asList("userId", "anonymousId", "address"));
        Map<?, ?> address =
                traits.get("address") instanceof Map
                        ? (Map<?, ?>) traits.get("address")
                        : Collections.emptyMap();

        putString(
                attributes,
                "firstName",
                find(traits, consumed, "firstName", "first_name"),
                BrazeUser::setFirstName);
        putString(
                attributes,
                "lastName",
                find(traits, consumed, "lastName", "last_name"),
                BrazeUser::setLastName);
        putString(attributes, "email", find(traits, consumed, "email"), BrazeUser::setEmail);
        putString(attributes, "phone", find(traits, consumed, "phone"), BrazeUser::setPhoneNumber);
        Object city = find(traits, consumed, "home_city");
        putString(
                attributes,
                "homeCity",
                address.containsKey("city") ? address.get("city") : city,
                BrazeUser::setHomeCity);
        Object country = find(traits, consumed, "country");
        putString(
                attributes,
                "country",
                address.containsKey("country") ? address.get("country") : country,
                BrazeUser::setCountry);
        for (Map.Entry<?, ?> field : address.entrySet()) {
            String key = String.valueOf(field.getKey());
            if (!"city".equals(key) && !"country".equals(key)) {
                putCustom(attributes, key, field.getValue());
            }
        }

        Object gender = find(traits, consumed, "gender");
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
                putCustom(attributes, entry.getKey(), entry.getValue());
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
            return value;
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Number) {
            return scalar(value);
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

    boolean isPurchase(TrackPayload track) {
        try {
            return purchaseDetection.isPurchaseEvent(track);
        } catch (Exception e) {
            logger.error(e, "Purchase event matcher failed for %s.", track.event());
            return false;
        }
    }

    void logPurchases(TrackPayload track, Properties properties) {
        Object currencyValue = properties.get("currency");
        String currency =
                currencyValue instanceof String && ((String) currencyValue).length() == 3
                        ? (String) currencyValue
                        : "USD";
        List<Map<String, Object>> products = products(properties.get("products"));

        if (!(purchaseGrouping instanceof PurchaseGrouping.PerProduct) || products.isEmpty()) {
            BigDecimal total =
                    BigDecimal.valueOf(
                            number(
                                    properties.containsKey("revenue")
                                            ? properties.get("revenue")
                                            : properties.get("total"),
                                    0));
            logPurchase(
                    new BrazePurchase(track.event(), total, currency, 1, properties),
                    new PurchaseContext(track, properties, null));
            return;
        }

        ProductIdentifier identifier = ((PurchaseGrouping.PerProduct) purchaseGrouping).identifier;
        Map<String, Object> order = new LinkedHashMap<>(properties);
        order.remove("products");
        for (Map<String, Object> product : products) {
            Map<String, Object> purchase = new LinkedHashMap<>(order);
            purchase.putAll(product);
            purchase.remove("price");
            purchase.remove("quantity");
            logPurchase(
                    new BrazePurchase(
                            productId(product, identifier),
                            BigDecimal.valueOf(number(product.get("price"), 0)),
                            currency,
                            (int) number(product.get("quantity"), 1),
                            purchase),
                    new PurchaseContext(track, properties, product));
        }
    }

    private void logPurchase(BrazePurchase purchase, PurchaseContext context) {
        if (purchaseTransformer != null) {
            try {
                purchase = purchaseTransformer.transform(purchase, context);
            } catch (Exception e) {
                logger.error(
                        e,
                        "Purchase transformer failed for %s; logging the default purchase.",
                        context.event().event());
            }
            if (purchase == null) {
                return;
            }
        }
        if (Utils.isNullOrEmpty(purchase.productId())) {
            logger.info("Dropping purchase without a product ID from %s.", context.event().event());
            return;
        }
        braze.logPurchase(
                purchase.productId(),
                purchase.currency(),
                purchase.price(),
                purchase.quantity(),
                brazeProperties(purchase.properties()));
    }

    private static String productId(Map<String, Object> product, ProductIdentifier identifier) {
        List<String> keys =
                identifier == ProductIdentifier.NAME
                        ? Collections.singletonList("name")
                        : Arrays.asList("sku", "product_id", "name");
        for (String key : keys) {
            Object id = product.get(key);
            if (id != null && !String.valueOf(id).isEmpty()) {
                return String.valueOf(id);
            }
        }
        return "";
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

    BrazeProperties brazeProperties(Map<String, ?> properties) {
        BrazeProperties brazeProperties = new BrazeProperties();
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            String key = entry.getKey();
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
            case "not applicable":
                return Gender.NOT_APPLICABLE;
            case "p":
            case "prefer_not_to_say":
            case "prefer not to say":
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

}

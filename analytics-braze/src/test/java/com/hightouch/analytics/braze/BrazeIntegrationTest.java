package com.hightouch.analytics.braze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.braze.Braze;
import com.braze.BrazeUser;
import com.braze.enums.Gender;
import com.braze.enums.Month;
import com.braze.enums.NotificationSubscriptionType;
import com.braze.events.IValueCallback;
import com.braze.models.outgoing.AttributionData;
import com.braze.models.outgoing.BrazeProperties;
import com.hightouch.analytics.Analytics;
import com.hightouch.analytics.ValueMap;
import com.hightouch.analytics.integrations.IdentifyPayload;
import com.hightouch.analytics.integrations.Logger;
import com.hightouch.analytics.integrations.ScreenPayload;
import com.hightouch.analytics.integrations.TrackPayload;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BrazeIntegrationTest {
    private Application application;
    private Braze braze;
    private BrazeUser user;
    private SharedPreferences preferences;
    private BrazeIntegration.Builder options;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        application = RuntimeEnvironment.application;
        braze = mock(Braze.class);
        user = mock(BrazeUser.class);
        doAnswer(
                        invocation -> {
                            ((IValueCallback<BrazeUser>) invocation.getArgument(0)).onSuccess(user);
                            return null;
                        })
                .when(braze)
                .getCurrentUser(any(IValueCallback.class));
        preferences =
                application.getSharedPreferences(
                        BrazeIntegration.PREFERENCES_NAME, Context.MODE_PRIVATE);
        options = BrazeIntegration.builder(application, braze);
    }

    private BrazeIntegration integration() {
        return new BrazeIntegration(
                braze, options, preferences, Logger.with(Analytics.LogLevel.NONE));
    }

    private static IdentifyPayload identify(String userId, Map<String, ?> traits) {
        ValueMap withAnonymousId = new ValueMap().putValue("anonymousId", "anon");
        withAnonymousId.putAll(traits);
        IdentifyPayload.Builder builder =
                new IdentifyPayload.Builder().anonymousId("anon").traits(withAnonymousId);
        if (userId != null) {
            builder.userId(userId);
        }
        return builder.build();
    }

    private static TrackPayload track(String event, Map<String, ?> properties) {
        return new TrackPayload.Builder()
                .anonymousId("anon")
                .event(event)
                .properties(properties)
                .build();
    }

    private static JSONObject json(BrazeProperties properties) {
        return properties.forJsonPut();
    }

    private JSONObject capturePurchase(String productId, String currency, String price, int qty) {
        ArgumentCaptor<BrazeProperties> captor = ArgumentCaptor.forClass(BrazeProperties.class);
        verify(braze)
                .logPurchase(
                        eq(productId),
                        eq(currency),
                        eq(new BigDecimal(price)),
                        eq(qty),
                        captor.capture());
        return json(captor.getValue());
    }

    @Test
    public void factoryUsesProvidedInstanceWithoutProjectSettings() {
        BrazeIntegration.Factory factory = options.build();
        Analytics analytics = mock(Analytics.class);
        when(analytics.getApplication()).thenReturn(application);
        when(analytics.logger(BrazeIntegration.KEY))
                .thenReturn(Logger.with(Analytics.LogLevel.NONE));

        assertThat(factory.key()).isEqualTo("Appboy");
        assertThat(factory.getBraze()).isSameAs(braze);
        assertThat(factory.create(null, analytics).getUnderlyingInstance()).isSameAs(braze);
    }

    @Test
    public void sessionsTrackedOnlyWhenEnabled() {
        options.build();
        Activity first = Robolectric.buildActivity(Activity.class).create().start().get();
        verify(braze, never()).openSession(any());

        BrazeIntegration.builder(application, braze).trackSessions(true).build();
        Activity second = Robolectric.buildActivity(Activity.class).create().start().stop().get();
        verify(braze).openSession(second);
        verify(braze).closeSession(second);
        verify(braze, never()).openSession(first);
    }

    @Test
    public void changeUserOnlyWhenUserIdChanges() {
        BrazeIntegration integration = integration();
        integration.identify(identify("user-1", Collections.emptyMap()));
        integration.identify(identify("user-1", Collections.emptyMap()));
        integration.identify(identify(null, Collections.emptyMap()));
        verify(braze, times(1)).changeUser("user-1");

        integration.identify(identify("user-2", Collections.emptyMap()));
        verify(braze).changeUser("user-2");

        integration.reset();
        integration.identify(identify("user-2", Collections.emptyMap()));
        verify(braze, times(2)).changeUser("user-2");
    }

    @Test
    public void reservedTraits() {
        ValueMap address =
                new ValueMap()
                        .putValue("city", "Denver")
                        .putValue("country", "US")
                        .putValue("postalCode", "80202")
                        .putValue("state", "CO");
        integration()
                .identify(
                        identify(
                                "user-1",
                                new ValueMap()
                                        .putValue("userId", "user-1")
                                        .putValue("anonymousId", "anon")
                                        .putValue("firstName", "Ada")
                                        .putValue("last_name", "Lovelace")
                                        .putValue("email", "ada@example.com")
                                        .putValue("phone", "555-0100")
                                        .putValue("gender", "Female")
                                        .putValue("birthday", "1990-05-15")
                                        .putValue("address", address)
                                        .putValue("email_subscribe", "opted_in")
                                        .putValue("push_subscribe", "unsubscribed")));

        verify(user).setFirstName("Ada");
        verify(user).setLastName("Lovelace");
        verify(user).setEmail("ada@example.com");
        verify(user).setPhoneNumber("555-0100");
        verify(user).setGender(Gender.FEMALE);
        verify(user).setDateOfBirth(1990, Month.MAY, 15);
        verify(user).setHomeCity("Denver");
        verify(user).setCountry("US");
        verify(user).setCustomUserAttribute("postalCode", "80202");
        verify(user).setCustomUserAttribute("state", "CO");
        verify(user).setEmailNotificationSubscriptionType(NotificationSubscriptionType.OPTED_IN);
        verify(user).setPushNotificationSubscriptionType(NotificationSubscriptionType.UNSUBSCRIBED);
        verify(user, never()).setCustomUserAttribute(eq("userId"), anyString());
        verify(user, never()).setCustomUserAttribute(eq("anonymousId"), anyString());
        verify(user, never()).setCustomUserAttribute(eq("address"), anyString());
        verify(user, never()).setCustomUserAttribute(eq("city"), anyString());
    }

    @Test
    public void otherTraitNamesAreCustomAttributesAndInvalidValuesAreDropped() {
        integration()
                .identify(
                        identify(
                                null,
                                new ValueMap()
                                        .putValue("$FirstName", "Ada")
                                        .putValue("Email", "ada@example.com")
                                        .putValue("age", 30)
                                        .putValue("gender", "robot")
                                        .putValue("dob", "not a date")
                                        .putValue("email_subscribe", "maybe")));

        verify(user).setCustomUserAttribute("$FirstName", "Ada");
        verify(user).setCustomUserAttribute("Email", "ada@example.com");
        verify(user).setCustomUserAttribute("age", 30);
        verify(user, never()).setFirstName(anyString());
        verify(user, never()).setEmail(anyString());
        verify(user, never()).setDateOfBirth(anyInt(), any(), anyInt());
        verify(user, never()).setGender(any());
        verify(user, never()).setEmailNotificationSubscriptionType(any());
        verify(braze, never()).changeUser(anyString());
    }

    @Test
    public void genderSpellings() {
        BrazeIntegration integration = integration();
        Object[][] cases = {
            {"M", Gender.MALE},
            {"female", Gender.FEMALE},
            {"Other", Gender.OTHER},
            {"u", Gender.UNKNOWN},
            {"Not Applicable", Gender.NOT_APPLICABLE},
            {"prefer not to say", Gender.PREFER_NOT_TO_SAY},
        };
        for (Object[] c : cases) {
            integration.identify(identify(null, new ValueMap().putValue("gender", c[0])));
            verify(user).setGender((Gender) c[1]);
        }
    }

    @Test
    public void customAttributes() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(1_700_000_000_000L);
        integration()
                .identify(
                        identify(
                                "user-1",
                                new ValueMap()
                                        .putValue("$$plan", "gold")
                                        .putValue("visits", 3)
                                        .putValue("lifetime", 12L)
                                        .putValue("score", 9.5)
                                        .putValue("vip", true)
                                        .putValue("signedUpAt", calendar.getTime())
                                        .putValue("tags", Arrays.asList("a", 1, true))
                                        .putValue("prefs", new ValueMap().putValue("color", "red"))
                                        .putValue("removed", null)
                                        .putValue("unsupported", new Object())));

        verify(user).setCustomUserAttribute("$$plan", "gold");
        verify(user).setCustomUserAttribute("visits", 3);
        verify(user).setCustomUserAttribute("lifetime", 12L);
        verify(user).setCustomUserAttribute("score", 9.5);
        verify(user).setCustomUserAttribute("vip", true);
        verify(user).setCustomUserAttributeToSecondsFromEpoch("signedUpAt", 1_700_000_000L);
        verify(user).setCustomAttributeArray("tags", new String[] {"a", "1", "true"});
        verify(user).setCustomUserAttribute("prefs", "{\"color\":\"red\"}");
        verify(user).unsetCustomUserAttribute("removed");
        verify(user, never()).setCustomUserAttribute(eq("unsupported"), anyString());
    }

    @Test
    public void preservesExplicitStringValues() {
        integration()
                .identify(
                        identify(
                                "user-1",
                                new ValueMap().putValue("visits", "3").putValue("vip", "true")));
        integration().track(track("Viewed", new ValueMap().putValue("count", "2")));

        verify(user).setCustomUserAttribute("visits", "3");
        verify(user).setCustomUserAttribute("vip", "true");
        ArgumentCaptor<BrazeProperties> captor = ArgumentCaptor.forClass(BrazeProperties.class);
        verify(braze).logCustomEvent(eq("Viewed"), captor.capture());
        assertThat(json(captor.getValue()).opt("count")).isEqualTo("2");
    }

    @Test
    public void onlyChangedAttributesAreSentAndCachePersists() {
        ValueMap traits = new ValueMap().putValue("firstName", "Ada").putValue("plan", "gold");
        integration().identify(identify("user-1", traits));
        integration().identify(identify("user-1", traits));
        verify(user, times(1)).setFirstName("Ada");
        verify(user, times(1)).setCustomUserAttribute("plan", "gold");

        integration().identify(identify("user-1", traits.putValue("plan", "platinum")));
        verify(user, times(1)).setFirstName("Ada");
        verify(user).setCustomUserAttribute("plan", "platinum");

        integration().identify(identify("user-2", traits));
        verify(user, times(2)).setFirstName("Ada");

        BrazeIntegration integration = integration();
        integration.reset();
        integration.identify(identify("user-2", traits));
        verify(user, times(3)).setFirstName("Ada");
    }

    @Test
    public void trackLogsCustomEvent() {
        integration()
                .track(
                        track(
                                "$Workout Started",
                                new ValueMap()
                                        .putValue("$class", "Spin")
                                        .putValue("class", "Spin")
                                        .putValue("minutes", 45)
                                        .putValue("coach", new ValueMap().putValue("id", 7))
                                        .putValue("tags", Arrays.asList("a", "b"))));

        ArgumentCaptor<BrazeProperties> captor = ArgumentCaptor.forClass(BrazeProperties.class);
        verify(braze).logCustomEvent(eq("$Workout Started"), captor.capture());
        JSONObject properties = json(captor.getValue());
        // Braze's Android SDK drops property keys that start with `$`.
        assertThat(properties.has("$class")).isFalse();
        assertThat(properties.opt("class")).isEqualTo("Spin");
        assertThat(properties.opt("minutes")).isEqualTo(45);
        assertThat(properties.optJSONObject("coach").opt("id")).isEqualTo(7);
        assertThat(properties.optJSONArray("tags").length()).isEqualTo(2);
    }

    @Test
    public void orderCompletedLogsPurchasePerProduct() throws Exception {
        List<ValueMap> products =
                Arrays.asList(
                        new ValueMap()
                                .putValue("product_id", "p1")
                                .putValue("sku", "SKU-1")
                                .putValue("name", "Towel")
                                .putValue("brand", "Equinox")
                                .putValue("category", "Gear")
                                .putValue("variant", "Blue")
                                .putValue("position", 1)
                                .putValue("coupon", "SAVE")
                                .putValue("price", 12.5)
                                .putValue("quantity", 2)
                                .putValue("color", "blue"),
                        new ValueMap().putValue("product_id", "p2").putValue("price", "3"),
                        new ValueMap().putValue("name", "Mat"),
                        new ValueMap().putValue("price", 1));
        integration()
                .track(
                        track(
                                "Order Completed",
                                new ValueMap()
                                        .putValue("order_id", "o-1")
                                        .putValue("revenue", 28)
                                        .putValue("currency", "EUR")
                                        .putValue("coupon", "ORDER")
                                        .putValue("products", products)));

        JSONObject first = capturePurchase("SKU-1", "EUR", "12.5", 2);
        assertThat(first.getString("order_id")).isEqualTo("o-1");
        assertThat(first.getInt("revenue")).isEqualTo(28);
        assertThat(first.getString("currency")).isEqualTo("EUR");
        assertThat(first.getString("sku")).isEqualTo("SKU-1");
        assertThat(first.getString("product_id")).isEqualTo("p1");
        assertThat(first.getString("name")).isEqualTo("Towel");
        assertThat(first.getString("brand")).isEqualTo("Equinox");
        assertThat(first.getString("category")).isEqualTo("Gear");
        assertThat(first.getString("variant")).isEqualTo("Blue");
        assertThat(first.getInt("position")).isEqualTo(1);
        assertThat(first.getString("coupon")).isEqualTo("SAVE");
        assertThat(first.getString("color")).isEqualTo("blue");
        assertThat(first.has("price")).isFalse();
        assertThat(first.has("quantity")).isFalse();
        assertThat(first.has("products")).isFalse();
        assertThat(first.has("Transaction Id")).isFalse();

        assertThat(capturePurchase("p2", "EUR", "3.0", 1).getString("coupon")).isEqualTo("ORDER");
        capturePurchase("Mat", "EUR", "0.0", 1);
        verify(braze, times(3))
                .logPurchase(anyString(), anyString(), any(), anyInt(), any(BrazeProperties.class));
        verify(braze, never()).logCustomEvent(anyString(), any());
    }

    @Test
    public void purchaseProductIdentifierName() {
        options.purchaseGrouping(PurchaseGrouping.perProduct(ProductIdentifier.NAME));
        integration()
                .track(
                        track(
                                "Completed Order",
                                new ValueMap()
                                        .putValue("currency", "dollars")
                                        .putValue(
                                                "products",
                                                Collections.singletonList(
                                                        new ValueMap()
                                                                .putValue("sku", "SKU-1")
                                                                .putValue("name", "Towel")))));

        capturePurchase("Towel", "USD", "0.0", 1);
    }

    @Test
    public void orderWithoutProductsLogsSinglePurchase() throws Exception {
        integration()
                .track(
                        track(
                                "Order Completed",
                                new ValueMap()
                                        .putValue("order_id", "o-1")
                                        .putValue("total", 20)
                                        .putValue("products", Collections.emptyList())));

        JSONObject properties = capturePurchase("Order Completed", "USD", "20.0", 1);
        assertThat(properties.getString("order_id")).isEqualTo("o-1");
        assertThat(properties.getInt("total")).isEqualTo(20);
    }

    @Test
    public void perOrderGroupingLogsOnePurchasePerOrder() throws Exception {
        options.purchaseGrouping(PurchaseGrouping.perOrder());
        integration()
                .track(
                        track(
                                "Order Completed",
                                new ValueMap()
                                        .putValue("order_id", "o-1")
                                        .putValue("revenue", 25)
                                        .putValue(
                                                "products",
                                                Arrays.asList(
                                                        new ValueMap()
                                                                .putValue("sku", "SKU-1")
                                                                .putValue("coupon", "SAVE")
                                                                .putValue("price", 10)
                                                                .putValue("quantity", 2),
                                                        new ValueMap()
                                                                .putValue("sku", "SKU-2")
                                                                .putValue("price", 5)))));

        JSONObject properties = capturePurchase("Order Completed", "USD", "25.0", 1);
        assertThat(properties.getString("order_id")).isEqualTo("o-1");
        assertThat(properties.getInt("revenue")).isEqualTo(25);
        JSONArray products = properties.getJSONArray("products");
        assertThat(products.length()).isEqualTo(2);
        JSONObject first = products.getJSONObject(0);
        assertThat(first.getString("sku")).isEqualTo("SKU-1");
        assertThat(first.getString("coupon")).isEqualTo("SAVE");
        assertThat(first.getInt("price")).isEqualTo(10);
        assertThat(first.getInt("quantity")).isEqualTo(2);
        assertThat(first.has("Id")).isFalse();
        assertThat(first.has("Total Product Amount")).isFalse();
        assertThat(properties.has("Transaction Id")).isFalse();
    }

    @Test
    public void defaultPurchaseEventNames() {
        integration().track(track("Order Completed", new ValueMap().putValue("total", 5)));
        integration().track(track("Completed Order", new ValueMap().putValue("total", 5)));
        integration().track(track("order completed", new ValueMap().putValue("total", 5)));
        integration().track(track("Upgraded", new ValueMap().putValue("revenue", 9.99)));

        capturePurchase("Order Completed", "USD", "5.0", 1);
        capturePurchase("Completed Order", "USD", "5.0", 1);
        verify(braze).logCustomEvent(eq("order completed"), any());
        verify(braze).logCustomEvent(eq("Upgraded"), any());
    }

    @Test
    public void customPurchaseEventNames() {
        options.purchaseDetection(PurchaseDetection.eventNames("Membership Purchased"));
        integration().track(track("Membership Purchased", new ValueMap().putValue("total", 5)));
        integration().track(track("Order Completed", new ValueMap().putValue("total", 5)));

        capturePurchase("Membership Purchased", "USD", "5.0", 1);
        verify(braze).logCustomEvent(eq("Order Completed"), any());
    }

    @Test
    public void purchaseEventMatcherReplacesDefaultNames() {
        options.purchaseDetection(
                PurchaseDetection.matcher(track -> "Upgraded".equals(track.event())));
        integration().track(track("Upgraded", new ValueMap().putValue("total", 5)));
        integration().track(track("Order Completed", new ValueMap().putValue("revenue", 5)));

        capturePurchase("Upgraded", "USD", "5.0", 1);
        verify(braze).logCustomEvent(eq("Order Completed"), any());
    }

    @Test
    public void purchaseEventMatcherErrorLogsCustomEvent() {
        RuntimeException error = new RuntimeException("boom");
        options.purchaseDetection(
                PurchaseDetection.matcher(
                        track -> {
                            throw error;
                        }));
        Logger logger = mock(Logger.class);
        new BrazeIntegration(braze, options, preferences, logger)
                .track(track("Order Completed", new ValueMap().putValue("total", 5)));

        verify(logger).error(eq(error), anyString(), any());
        verify(braze).logCustomEvent(eq("Order Completed"), any());
        verify(braze, never())
                .logPurchase(anyString(), anyString(), any(), anyInt(), any(BrazeProperties.class));
    }

    private static TrackPayload order(ValueMap... products) {
        return track(
                "Order Completed",
                new ValueMap()
                        .putValue("order_id", "o-1")
                        .putValue("revenue", 25)
                        .putValue("products", Arrays.asList(products)));
    }

    @Test
    public void purchaseTransformerModifiesPurchases() throws Exception {
        List<PurchaseContext> contexts = new ArrayList<>();
        options.purchaseTransformer(
                (purchase, context) -> {
                    contexts.add(context);
                    return purchase.withProductId("X-" + purchase.productId())
                            .withPrice(new BigDecimal("9.5"))
                            .withCurrency("EUR")
                            .withQuantity(3)
                            .withProperties(
                                    Collections.singletonMap(
                                            "Transaction Id", context.order().get("order_id")));
                });
        TrackPayload track = order(new ValueMap().putValue("sku", "SKU-1").putValue("price", 10));
        integration().track(track);

        JSONObject properties = capturePurchase("X-SKU-1", "EUR", "9.5", 3);
        assertThat(properties.getString("Transaction Id")).isEqualTo("o-1");
        assertThat(properties.length()).isEqualTo(1);
        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).event()).isSameAs(track);
        assertThat(contexts.get(0).order()).containsKey("products");
        assertThat(contexts.get(0).product()).containsEntry("sku", "SKU-1");

        options.purchaseGrouping(PurchaseGrouping.perOrder());
        integration().track(track);
        capturePurchase("X-Order Completed", "EUR", "9.5", 3);
        assertThat(contexts.get(1).product()).isNull();
    }

    @Test
    public void purchaseTransformerSkipsNullAndInvalidResults() {
        options.purchaseTransformer(
                (purchase, context) -> {
                    if ("SKU-1".equals(purchase.productId())) {
                        return null;
                    }
                    if ("SKU-2".equals(purchase.productId())) {
                        return purchase.withProductId("");
                    }
                    return purchase;
                });
        integration()
                .track(
                        order(
                                new ValueMap().putValue("sku", "SKU-1"),
                                new ValueMap().putValue("sku", "SKU-2"),
                                new ValueMap().putValue("sku", "SKU-3")));

        capturePurchase("SKU-3", "USD", "0.0", 1);
        verify(braze, times(1))
                .logPurchase(anyString(), anyString(), any(), anyInt(), any(BrazeProperties.class));
    }

    @Test
    public void purchaseTransformerCanSetMissingProductId() {
        options.purchaseTransformer(
                (purchase, context) ->
                        purchase.productId().isEmpty()
                                ? purchase.withProductId(
                                        String.valueOf(context.product().get("id")))
                                : purchase);
        integration().track(order(new ValueMap().putValue("id", "legacy-1")));

        capturePurchase("legacy-1", "USD", "0.0", 1);
    }

    @Test
    public void purchaseTransformerErrorLogsDefaultPurchase() throws Exception {
        RuntimeException error = new RuntimeException("boom");
        options.purchaseTransformer(
                (purchase, context) -> {
                    throw error;
                });
        Logger logger = mock(Logger.class);
        new BrazeIntegration(braze, options, preferences, logger)
                .track(order(new ValueMap().putValue("sku", "SKU-1").putValue("price", 10)));

        verify(logger).error(eq(error), anyString(), any());
        assertThat(capturePurchase("SKU-1", "USD", "10.0", 1).getString("order_id"))
                .isEqualTo("o-1");
    }

    @Test
    public void installAttributedSetsAttributionData() throws Exception {
        integration()
                .track(
                        track(
                                "Install Attributed",
                                new ValueMap()
                                        .putValue(
                                                "campaign",
                                                new ValueMap()
                                                        .putValue("source", "Facebook")
                                                        .putValue("name", "Fall")
                                                        .putValue("ad_group", "Group")
                                                        .putValue("ad_creative", "Video"))));

        ArgumentCaptor<AttributionData> captor = ArgumentCaptor.forClass(AttributionData.class);
        verify(user).setAttributionData(captor.capture());
        JSONObject data = captor.getValue().forJsonPut();
        assertThat(data.getString("source")).isEqualTo("Facebook");
        assertThat(data.getString("campaign")).isEqualTo("Fall");
        assertThat(data.getString("adgroup")).isEqualTo("Group");
        assertThat(data.getString("ad")).isEqualTo("Video");
        verify(braze).logCustomEvent(eq("Install Attributed"), any());
    }

    @Test
    public void screenForwardedOnlyWhenEnabled() {
        ScreenPayload screen =
                new ScreenPayload.Builder()
                        .anonymousId("anon")
                        .name("Home")
                        .properties(new ValueMap().putValue("tab", "classes"))
                        .build();
        integration().screen(screen);
        verifyNoInteractions(braze);

        options.forwardScreenViews(true);
        integration().screen(screen);
        ArgumentCaptor<BrazeProperties> captor = ArgumentCaptor.forClass(BrazeProperties.class);
        verify(braze).logCustomEvent(eq("Home"), captor.capture());
        assertThat(json(captor.getValue()).opt("tab")).isEqualTo("classes");
    }

    @Test
    public void flushRequestsImmediateDataFlush() {
        integration().flush();
        verify(braze).requestImmediateDataFlush();
    }

    @Test
    public void brazeErrorsDoNotPropagate() {
        doThrow(new RuntimeException("boom")).when(braze).logCustomEvent(anyString(), any());
        doThrow(new RuntimeException("boom")).when(braze).changeUser(anyString());
        doThrow(new RuntimeException("boom")).when(braze).requestImmediateDataFlush();
        doThrow(new RuntimeException("boom")).when(user).setFirstName(anyString());

        BrazeIntegration integration = integration();
        integration.track(track("Viewed", Collections.emptyMap()));
        integration.identify(identify("user-1", Collections.emptyMap()));
        integration.identify(identify(null, new ValueMap().putValue("firstName", "Ada")));
        integration.flush();
    }

    @Test
    public void mparticleMobilePurchaseRecipeRenamesProperties() throws Exception {
        Map<String, String> orderNames = new HashMap<>();
        orderNames.put("order_id", "Transaction Id");
        orderNames.put("revenue", "Total Amount");
        orderNames.put("tax", "Tax Amount");
        orderNames.put("shipping", "Shipping Amount");
        Map<String, String> productNames = new HashMap<>();
        productNames.put("name", "Name");
        productNames.put("brand", "Brand");
        productNames.put("category", "Category");
        productNames.put("variant", "Variant");
        productNames.put("position", "Position");
        productNames.put("coupon", "Coupon Code");
        Set<String> passed =
                new HashSet<>(
                        Arrays.asList("sku", "product_id", "price", "quantity", "currency", "products"));
        options.purchaseTransformer(
                (purchase, context) -> {
                    Map<String, Object> properties = new HashMap<>();
                    mparticleRename(context.order(), orderNames, passed, properties);
                    if (context.product() != null) {
                        mparticleRename(context.product(), productNames, passed, properties);
                    }
                    return purchase.withProperties(properties);
                });
        integration()
                .track(
                        order(
                                new ValueMap()
                                        .putValue("sku", "SKU1")
                                        .putValue("name", "Shirt")
                                        .putValue("price", 12.5)
                                        .putValue("quantity", 2)
                                        .putValue("brand", "Equinox")
                                        .putValue("coupon", "C1")));

        JSONObject properties = capturePurchase("SKU1", "USD", "12.5", 2);
        assertThat(properties.get("Transaction Id")).isEqualTo("o-1");
        assertThat(properties.get("Total Amount")).isEqualTo(25);
        assertThat(properties.getString("Name")).isEqualTo("Shirt");
        assertThat(properties.getString("Brand")).isEqualTo("Equinox");
        assertThat(properties.getString("Coupon Code")).isEqualTo("C1");
        assertThat(properties.has("order_id")).isFalse();
        assertThat(properties.has("brand")).isFalse();
        assertThat(properties.has("sku")).isFalse();
    }

    private static void mparticleRename(
            Map<String, ?> values, Map<String, String> names, Set<String> passed, Map<String, Object> into) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!passed.contains(entry.getKey())) {
                into.put(names.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue());
            }
        }
    }
}

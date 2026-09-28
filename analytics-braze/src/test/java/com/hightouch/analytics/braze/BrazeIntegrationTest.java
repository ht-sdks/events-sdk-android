package com.hightouch.analytics.braze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
                        .putValue("postalCode", "80202");
        integration()
                .identify(
                        identify(
                                "user-1",
                                new ValueMap()
                                        .putValue("userId", "user-1")
                                        .putValue("anonymousId", "anon")
                                        .putValue("firstName", "Ada")
                                        .putValue("last_name", "Lovelace")
                                        .putValue("Email", "ada@example.com")
                                        .putValue("$Mobile", "555-0100")
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
        verify(user).setCustomUserAttribute("Zip", "80202");
        verify(user).setEmailNotificationSubscriptionType(NotificationSubscriptionType.OPTED_IN);
        verify(user).setPushNotificationSubscriptionType(NotificationSubscriptionType.UNSUBSCRIBED);
        verify(user, never()).setCustomUserAttribute(eq("userId"), anyString());
        verify(user, never()).setCustomUserAttribute(eq("anonymousId"), anyString());
        verify(user, never()).setCustomUserAttribute(eq("address"), anyString());
    }

    @Test
    public void mParticleAliasesAndInvalidValues() {
        integration()
                .identify(
                        identify(
                                null,
                                new ValueMap()
                                        .putValue("$FirstName", "Ada")
                                        .putValue("$City", "Boulder")
                                        .putValue("$Country", "CA")
                                        .putValue("$Zip", "12345")
                                        .putValue("$Age", 30)
                                        .putValue("$Gender", "robot")
                                        .putValue("dob", "not a date")
                                        .putValue("email_subscribe", "maybe")));

        verify(user).setFirstName("Ada");
        verify(user).setHomeCity("Boulder");
        verify(user).setCountry("CA");
        verify(user).setCustomUserAttribute("Zip", "12345");
        verify(user)
                .setDateOfBirth(Calendar.getInstance().get(Calendar.YEAR) - 30, Month.JANUARY, 1);
        verify(user, never()).setGender(any());
        verify(user, never()).setEmailNotificationSubscriptionType(any());
        verify(braze, never()).changeUser(anyString());
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

        verify(user).setCustomUserAttribute("plan", "gold");
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
    public void stringifyAttributeValues() {
        options.stringifyAttributeValues(true);
        integration()
                .identify(
                        identify(
                                "user-1",
                                new ValueMap().putValue("visits", 3).putValue("vip", true)));
        integration().track(track("Viewed", new ValueMap().putValue("count", 2)));

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
                                        .putValue("minutes", 45)
                                        .putValue("coach", new ValueMap().putValue("id", 7))
                                        .putValue("tags", Arrays.asList("a", "b"))));

        ArgumentCaptor<BrazeProperties> captor = ArgumentCaptor.forClass(BrazeProperties.class);
        verify(braze).logCustomEvent(eq("Workout Started"), captor.capture());
        JSONObject properties = json(captor.getValue());
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
                        new ValueMap().putValue("product_id", "p2").putValue("price", "3"));
        integration()
                .track(
                        track(
                                "Order Completed",
                                new ValueMap()
                                        .putValue("order_id", "o-1")
                                        .putValue("revenue", 28)
                                        .putValue("currency", "EUR")
                                        .putValue("products", products)));

        JSONObject first = capturePurchase("SKU-1", "EUR", "12.5", 2);
        assertThat(first.getString("Transaction Id")).isEqualTo("o-1");
        assertThat(first.getString("order_id")).isEqualTo("o-1");
        assertThat(first.getString("Name")).isEqualTo("Towel");
        assertThat(first.getString("Brand")).isEqualTo("Equinox");
        assertThat(first.getString("Category")).isEqualTo("Gear");
        assertThat(first.getString("Variant")).isEqualTo("Blue");
        assertThat(first.getInt("Position")).isEqualTo(1);
        assertThat(first.getString("Coupon Code")).isEqualTo("SAVE");
        assertThat(first.getString("color")).isEqualTo("blue");
        assertThat(first.getString("product_id")).isEqualTo("p1");
        assertThat(first.has("sku")).isFalse();
        assertThat(first.has("price")).isFalse();
        assertThat(first.has("quantity")).isFalse();
        assertThat(first.has("currency")).isFalse();
        assertThat(first.has("products")).isFalse();

        capturePurchase("p2", "EUR", "3.0", 1);
        verify(braze, never()).logCustomEvent(anyString(), any());
    }

    @Test
    public void purchaseProductIdentifierName() {
        options.purchaseProductIdentifier(BrazeIntegration.ProductIdentifier.NAME);
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
    public void orderWithoutProductsLogsSinglePurchase() {
        integration().track(track("Order Completed", new ValueMap().putValue("total", 20)));

        capturePurchase("Order Completed", "USD", "20.0", 1);
    }

    @Test
    public void bundleCommerceEvents() throws Exception {
        options.bundleCommerceEvents(true);
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

        JSONObject properties = capturePurchase("eCommerce - purchase", "USD", "25.0", 1);
        assertThat(properties.getString("Transaction Id")).isEqualTo("o-1");
        JSONArray products = properties.getJSONArray("products");
        assertThat(products.length()).isEqualTo(2);
        JSONObject first = products.getJSONObject(0);
        assertThat(first.getString("Id")).isEqualTo("SKU-1");
        assertThat(first.getString("Coupon Code")).isEqualTo("SAVE");
        assertThat(first.getDouble("Total Product Amount")).isEqualTo(20.0);
        assertThat(first.has("sku")).isFalse();
        assertThat(products.getJSONObject(1).getDouble("Total Product Amount")).isEqualTo(5.0);
    }

    @Test
    public void logPurchaseWhenRevenuePresent() {
        integration().track(track("Upgraded", new ValueMap().putValue("revenue", 9.99)));
        verify(braze).logCustomEvent(eq("Upgraded"), any());

        options.logPurchaseWhenRevenuePresent(true);
        integration().track(track("Upgraded", new ValueMap().putValue("revenue", "9.99")));
        integration().track(track("Browsed", new ValueMap().putValue("revenue", 0)));
        capturePurchase("Upgraded", "USD", "9.99", 1);
        verify(braze).logCustomEvent(eq("Browsed"), any());
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
}

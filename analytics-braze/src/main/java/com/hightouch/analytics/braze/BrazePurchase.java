package com.hightouch.analytics.braze;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A purchase about to be passed to {@code Braze.logPurchase}. */
public final class BrazePurchase {
    private final String productId;
    private final BigDecimal price;
    private final String currency;
    private final int quantity;
    private final Map<String, Object> properties;

    BrazePurchase(
            String productId,
            BigDecimal price,
            String currency,
            int quantity,
            Map<String, ?> properties) {
        this.productId = productId;
        this.price = price;
        this.currency = currency;
        this.quantity = quantity;
        this.properties =
                Collections.unmodifiableMap(
                        properties == null
                                ? new LinkedHashMap<String, Object>()
                                : new LinkedHashMap<String, Object>(properties));
    }

    public String productId() {
        return productId;
    }

    public BigDecimal price() {
        return price;
    }

    public String currency() {
        return currency;
    }

    public int quantity() {
        return quantity;
    }

    @NonNull
    public Map<String, Object> properties() {
        return properties;
    }

    @NonNull
    public BrazePurchase withProductId(String productId) {
        return new BrazePurchase(productId, price, currency, quantity, properties);
    }

    @NonNull
    public BrazePurchase withPrice(BigDecimal price) {
        return new BrazePurchase(productId, price, currency, quantity, properties);
    }

    @NonNull
    public BrazePurchase withCurrency(String currency) {
        return new BrazePurchase(productId, price, currency, quantity, properties);
    }

    @NonNull
    public BrazePurchase withQuantity(int quantity) {
        return new BrazePurchase(productId, price, currency, quantity, properties);
    }

    @NonNull
    public BrazePurchase withProperties(Map<String, ?> properties) {
        return new BrazePurchase(productId, price, currency, quantity, properties);
    }
}

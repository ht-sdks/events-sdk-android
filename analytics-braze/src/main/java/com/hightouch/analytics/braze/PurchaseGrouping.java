package com.hightouch.analytics.braze;

import androidx.annotation.NonNull;

/**
 * Whether a purchase event logs one Braze purchase per product or one per order.
 * Products require a finite price. Omitted quantities default to 1; supplied quantities
 * must be integers from 1 to 100. Whole-order purchases (including events without products)
 * require revenue (or total if revenue is absent); quantity also defaults to 1.
 * Complete numeric strings are accepted. Missing prices and invalid values are rejected
 * and logged, including after the purchase transformer; an explicit zero price is valid.
 */
public abstract class PurchaseGrouping {
    private PurchaseGrouping() {}

    /**
     * One purchase per product, with {@code identifier} as the {@code productId}. SKU falls
     * back to {@code product_id} and then {@code name}. An order without products logs one
     * purchase named after the event.
     */
    @NonNull
    public static PurchaseGrouping perProduct(@NonNull ProductIdentifier identifier) {
        return new PerProduct(identifier);
    }

    /** One purchase per order, named after the event. */
    @NonNull
    public static PurchaseGrouping perOrder() {
        return new PerOrder();
    }

    static final class PerProduct extends PurchaseGrouping {
        final ProductIdentifier identifier;

        PerProduct(ProductIdentifier identifier) {
            this.identifier = identifier;
        }
    }

    private static final class PerOrder extends PurchaseGrouping {}
}

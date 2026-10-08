package com.hightouch.analytics.braze;

import androidx.annotation.NonNull;

/** Whether a purchase event logs one Braze purchase per product or one per order. */
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

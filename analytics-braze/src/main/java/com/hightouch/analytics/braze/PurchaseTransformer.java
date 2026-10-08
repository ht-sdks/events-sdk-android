package com.hightouch.analytics.braze;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Changes or skips each purchase before it's logged. Return {@code null} to skip the purchase.
 * If it throws, the default purchase is logged.
 */
public interface PurchaseTransformer {
    @Nullable
    BrazePurchase transform(@NonNull BrazePurchase purchase, @NonNull PurchaseContext context);
}

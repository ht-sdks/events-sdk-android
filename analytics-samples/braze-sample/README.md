# Braze Sample

Fill in the two placeholders at the top of `SampleApp.kt` before launching:

- `HT_WRITE_KEY` — Hightouch write key
- `BRAZE_API_KEY` — Braze Android API key (endpoint is already `sdk.iad-03.braze.com`)

`perOrder` is next to those keys and defaults to `false`, which logs one Braze purchase per product. Set it to `true` and relaunch to log one purchase per order.

Use `purchaseDetection` to select event names or a matcher, and `purchaseGrouping` to select per-product purchases with SKU/name identifiers or one purchase per order. For legacy mappings, use `purchaseTransformer` and convert only the fields that need string values.

This sandbox has no Event User Log. Check results on the Braze user profile, and in logcat (Hightouch debug and Braze verbose logs are turned on).

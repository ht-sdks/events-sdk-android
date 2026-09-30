# Braze Sample

Fill in the two placeholders at the top of `SampleApp.kt` before launching:

- `HT_WRITE_KEY` — Hightouch write key
- `BRAZE_API_KEY` — Braze Android API key (endpoint is already `sdk.iad-03.braze.com`)

`perOrder` is next to those keys and defaults to `false`, which logs one Braze purchase per product. Set it to `true` and relaunch to log one purchase per order.

This sandbox has no Event User Log. Check results on the Braze user profile, and in logcat (Hightouch debug and Braze verbose logs are turned on).

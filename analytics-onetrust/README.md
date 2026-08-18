# OneTrust consent for the Hightouch Events SDK (Android)

Gates and stamps Hightouch events based on OneTrust consent categories, mirroring the
[web OneTrust consent wrapper](https://github.com/ht-sdks/events-sdk-js-mono/tree/master/packages/consent/consent-wrapper-onetrust):

- Every event is stamped with `context.consent.categoryPreferences` (a map of OneTrust
  category/group IDs to booleans) so consent state travels with the event.
- Destinations you map to categories only receive events when **all** of their mapped categories
  are consented. Unmapped destinations are never gated. The Hightouch cloud destination itself can
  be gated via `ConsentManager.HIGHTOUCH_INTEGRATION_KEY`.
- When the user changes consent (banner or preference center), a `Consent Updated` track event is
  fired with the new preferences. This event bypasses the Hightouch destination's gate so
  preference changes always reach the server.
- A category whose consent is unknown or not yet collected is treated as **not consented**.

## Installation

```gradle
dependencies {
  implementation 'com.hightouch.analytics.android:analytics:X.Y.Z'
  implementation 'com.hightouch.analytics.android:analytics-onetrust:X.Y.Z'

  // You must provide the OneTrust SDK yourself, pinned to the version published
  // in your OneTrust tenant (this module does not force a version on you).
  implementation 'com.onetrust.cmp:native-sdk:<your tenant version>'
}
```

## Usage

```kotlin
// 1. Create your OneTrust SDK instance as usual (you own startSDK, banner UI, etc).
val oneTrust = OTPublishersHeadlessSDK(context)

// 2. Adapt it to a consent provider with the category (group) IDs you care about.
val provider = OneTrustConsentProvider(context, oneTrust, listOf("C0002", "C0004"))

// 3. Configure the consent manager with per-destination category requirements.
val consentManager = ConsentManager.Builder(provider)
    // Only deliver events to Hightouch when C0002 (Performance/Analytics) is consented:
    .integrationCategoryMapping(ConsentManager.HIGHTOUCH_INTEGRATION_KEY, listOf("C0002"))
    // Gate a device-mode destination on multiple categories:
    .integrationCategoryMapping("Firebase", listOf("C0002", "C0004"))
    .build()

// 4. Attach to the Analytics builder before build(), then start with the built instance.
val builder = Analytics.Builder(context, WRITE_KEY)
consentManager.attach(builder)
val analytics = builder.build()
Analytics.setSingletonInstance(analytics)
consentManager.start(analytics)
```

Java works the same way; see the `ConsentManager` Javadoc.

### Options

| Builder method | Effect |
| --- | --- |
| `integrationCategoryMapping(key, categories)` | Require all `categories` before delivering to destination `key` |
| `integrationCategoryMappings(map)` | Bulk variant |
| `consentUpdatedEventName(name)` | Rename the consent-change track event (default `"Consent Updated"`) |
| `disableConsentUpdatedEvent()` | Don't fire a track event on consent changes |

### Using a different CMP

`ConsentManager` (in the core `analytics` artifact) only depends on the
`ConsentCategoryProvider` interface. To integrate another CMP, implement that interface and skip
this module.

## Behavior notes

- **Ordering**: `attach()` registers a source middleware (stamping) and destination middleware
  (gating). Register any of your own source middleware that should see stamped events *after*
  calling `attach()`.
- **Threading**: OneTrust consent-change broadcasts arrive on the main thread; the resulting
  `Consent Updated` track is safe from any thread.
- **Blocked events are dropped**, not queued. Events sent while a mapped category is denied never
  reach that destination, even if consent is granted later. (Events to *unmapped* destinations,
  and stamping, are unaffected.)
- **Duplicate broadcasts** with an unchanged status do not fire extra `Consent Updated` events.

## Demo

`analytics-samples/kotlin-sample` demos both modes:

- Default: an in-memory fake provider with consent toggle switches in the UI.
- Real OneTrust: fill in `ONETRUST_DOMAIN_URL` / `ONETRUST_DOMAIN_ID` in
  [`ConsentConfig.kt`](../analytics-samples/kotlin-sample/src/main/java/com/example/kotlin_sample/ConsentConfig.kt)
  with values from your OneTrust tenant to see the real banner drive gating.

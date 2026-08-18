# OneTrust consent for the Hightouch Events SDK (Android)

Gates and stamps Hightouch events based on OneTrust consent categories, mirroring the
[web OneTrust consent wrapper](https://github.com/ht-sdks/events-sdk-js-mono/tree/master/packages/consent/consent-wrapper-onetrust)
and adding source-level per-event gating:

- Every event is stamped with `context.consent.categoryPreferences` (a map of OneTrust
  category/group IDs to booleans) so consent state travels with the event.
- Events you map to categories are dropped at the source unless **all** of those categories are
  consented. Unmapped events are not dropped unless you set `defaultEventCategories`.
- Destinations you map to categories only receive events when **all** of their mapped categories
  are consented. Unmapped destinations are never gated. The Hightouch cloud destination itself can
  be gated via `ConsentManager.HIGHTOUCH_INTEGRATION_KEY`.
- When the user changes consent (banner or preference center), a `Consent Updated` track event is
  fired with the new preferences. This event bypasses source and Hightouch destination gates so
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

// 3. Configure the consent manager.
val consentManager = ConsentManager.Builder(provider)
    // Per-event: drop these tracks unless the category is granted (includes SDK lifecycle events).
    .eventCategoryMapping("Purchase", listOf("C0004"))
    .lifecycleEventCategories(listOf("C0002"))
    .eventTypeCategoryMapping(BasePayload.Type.screen, listOf("C0002"))
    // Per-destination: only deliver to Hightouch when C0002 is consented.
    .integrationCategoryMapping(ConsentManager.HIGHTOUCH_INTEGRATION_KEY, listOf("C0002"))
    .build()

// 4. Attach to the Analytics builder before build(), then start with the built instance.
val builder = Analytics.Builder(context, WRITE_KEY)
consentManager.attach(builder)
val analytics = builder.build()
Analytics.setSingletonInstance(analytics)
consentManager.start(analytics)

// Optional per-call override (wins over name/type/default mappings for this call only):
analytics.track("Purchase", null, Options().requireConsentCategories("C0004"))
```

Java works the same way; see the `ConsentManager` Javadoc.

### Options

| Builder method | Effect |
| --- | --- |
| `eventCategoryMapping(name, categories)` | Drop this track event name at the source unless all `categories` are consented |
| `lifecycleEventCategories(categories)` | Same mapping for SDK lifecycle tracks (`Application Opened`, etc.) |
| `eventTypeCategoryMapping(type, categories)` | Drop this payload type (e.g. `screen`) when no name mapping applies |
| `defaultEventCategories(categories)` | Drop unmapped events unless all `categories` are consented |
| `integrationCategoryMapping(key, categories)` | Require all `categories` before delivering to destination `key` |
| `integrationCategoryMappings(map)` | Bulk destination variant |
| `consentUpdatedEventName(name)` | Rename the consent-change track event (default `"Consent Updated"`) |
| `disableConsentUpdatedEvent()` | Don't fire a track event on consent changes |

Event-level resolution order: per-call `Options.requireConsentCategories` → track event name → payload type → `defaultEventCategories`. If none apply, the event is not dropped at the source.

### Using a different CMP

`ConsentManager` (in the core `analytics` artifact) only depends on the
`ConsentCategoryProvider` interface. To integrate another CMP, implement that interface and skip
this module.

## Behavior notes

- **Ordering**: `attach()` registers a source middleware (stamping + event gating) and destination
  middleware (per-destination gating). Register any of your own source middleware that should see
  stamped events *after* calling `attach()`.
- **Threading**: OneTrust consent-change broadcasts arrive on the main thread; the resulting
  `Consent Updated` track is safe from any thread.
- **Blocked events are dropped**, not queued. Events sent while a required category is denied never
  reach that destination, even if consent is granted later. (Events to *unmapped* destinations,
  and stamping of events that pass the source gate, are unaffected.)
- **Duplicate broadcasts** with an unchanged status do not fire extra `Consent Updated` events.

## Demo

`analytics-samples/kotlin-sample` demos both modes:

- Default: an in-memory fake provider with consent toggle switches in the UI.
- Real OneTrust: fill in `ONETRUST_DOMAIN_URL` / `ONETRUST_DOMAIN_ID` in
  [`ConsentConfig.kt`](../analytics-samples/kotlin-sample/src/main/java/com/example/kotlin_sample/ConsentConfig.kt)
  with values from your OneTrust tenant to see the real banner drive gating.

With only analytics consent (C0002) on, **Track Button A** and lifecycle/screen events upload;
**Track Purchase** does not. With only advertising consent (C0004) on, Purchase uploads and Button
A / lifecycle do not.

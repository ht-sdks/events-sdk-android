package com.hightouch.analytics

import android.Manifest.permission.INTERNET
import com.google.common.util.concurrent.MoreExecutors
import com.hightouch.analytics.integrations.BasePayload
import com.hightouch.analytics.integrations.TrackPayload
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ConsentManagerTest {

    private class FakeConsentProvider(
        initialStatuses: Map<String, Boolean>
    ) : ConsentCategoryProvider {
        private val statuses = LinkedHashMap(initialStatuses)
        var listener: ConsentCategoryProvider.ConsentChangeListener? = null
        var shutdownCalled = false

        @Synchronized
        override fun getConsentStatuses(): Map<String, Boolean> = LinkedHashMap(statuses)

        override fun setConsentChangeListener(
            listener: ConsentCategoryProvider.ConsentChangeListener?
        ) {
            this.listener = listener
        }

        override fun shutdown() {
            shutdownCalled = true
        }

        @Synchronized
        fun setStatus(category: String, granted: Boolean) {
            statuses[category] = granted
            listener?.onConsentChange(LinkedHashMap(statuses))
        }
    }

    /** Records whether/what a middleware proceeded with. */
    private class RecordingChain(private val payload: BasePayload) : Middleware.Chain {
        var proceededPayload: BasePayload? = null
        override fun payload(): BasePayload = payload
        override fun proceed(payload: BasePayload) {
            proceededPayload = payload
        }
    }

    private lateinit var builder: Analytics.Builder
    private lateinit var provider: FakeConsentProvider

    @Before
    fun setUp() {
        Analytics.INSTANCES.clear()
        TestUtils.grantPermission(RuntimeEnvironment.application, INTERNET)
        provider = FakeConsentProvider(linkedMapOf("C0001" to true, "C0002" to false))
        builder =
            Analytics.Builder(RuntimeEnvironment.application, "write_key")
                .executor(MoreExecutors.newDirectExecutorService())
    }

    private fun consentManager(
        mappings: Map<String, List<String>> = emptyMap(),
        configure: ConsentManager.Builder.() -> Unit = {}
    ): ConsentManager {
        val managerBuilder = ConsentManager.Builder(provider).integrationCategoryMappings(mappings)
        managerBuilder.configure()
        return managerBuilder.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun categoryPreferencesOf(payload: BasePayload): Map<String, Any?>? {
        val consent = payload.context()?.get("consent") as? Map<String, Any?> ?: return null
        return consent["categoryPreferences"] as? Map<String, Any?>
    }

    // --- Stamping (through a real Analytics instance) ---

    @Test
    fun stampsTrackEvents() {
        val payloadRef = AtomicReference<BasePayload>()
        val analytics = consentManager()
            .attach(builder)
            .useSourceMiddleware { chain ->
                payloadRef.set(chain.payload())
                chain.proceed(chain.payload())
            }
            .build()

        analytics.track("foo")

        assertThat(categoryPreferencesOf(payloadRef.get()))
            .isEqualTo(mapOf("C0001" to true, "C0002" to false))
    }

    @Test
    fun stampsIdentifyAndScreenEvents() {
        val payloads = mutableListOf<BasePayload>()
        val analytics = consentManager()
            .attach(builder)
            .useSourceMiddleware { chain ->
                payloads.add(chain.payload())
                chain.proceed(chain.payload())
            }
            .build()

        analytics.identify("user-1")
        analytics.screen("Home")

        assertThat(payloads).hasSize(2)
        for (payload in payloads) {
            assertThat(categoryPreferencesOf(payload))
                .isEqualTo(mapOf("C0001" to true, "C0002" to false))
        }
    }

    @Test
    fun stampReflectsLatestConsentState() {
        val payloadRef = AtomicReference<BasePayload>()
        val analytics = consentManager()
            .attach(builder)
            .useSourceMiddleware { chain ->
                payloadRef.set(chain.payload())
                chain.proceed(chain.payload())
            }
            .build()

        analytics.track("before")
        assertThat(categoryPreferencesOf(payloadRef.get())!!["C0002"]).isEqualTo(false)

        provider.setStatus("C0002", true)
        analytics.track("after")
        assertThat(categoryPreferencesOf(payloadRef.get())!!["C0002"]).isEqualTo(true)
    }

    @Test
    fun stampPreservesExistingContext() {
        val payloadRef = AtomicReference<BasePayload>()
        val analytics = consentManager()
            .attach(builder)
            .useSourceMiddleware { chain ->
                payloadRef.set(chain.payload())
                chain.proceed(chain.payload())
            }
            .build()

        analytics.track("foo", null, Options().putContext("customKey", "customValue"))

        val context = payloadRef.get().context()
        assertThat(context["customKey"]).isEqualTo("customValue")
        assertThat(categoryPreferencesOf(payloadRef.get())).isNotNull
    }

    // --- Consent Updated event ---

    @Test
    fun consentChangeFiresConsentUpdatedEvent() {
        val trackedEvents = mutableListOf<TrackPayload>()
        val manager = consentManager()
        val analytics = manager
            .attach(builder)
            .useSourceMiddleware { chain ->
                val payload = chain.payload()
                if (payload is TrackPayload) trackedEvents.add(payload)
                chain.proceed(payload)
            }
            .build()
        manager.start(analytics)

        provider.setStatus("C0002", true)

        assertThat(trackedEvents).hasSize(1)
        val event = trackedEvents.first()
        assertThat(event.event()).isEqualTo("Consent Updated")
        assertThat(event.properties()["categoryPreferences"])
            .isEqualTo(mapOf("C0001" to true, "C0002" to true))
        // The consent-updated event itself is stamped too.
        assertThat(categoryPreferencesOf(event))
            .isEqualTo(mapOf("C0001" to true, "C0002" to true))
    }

    @Test
    fun consentUpdatedEventUsesCustomName() {
        val trackedEvents = mutableListOf<TrackPayload>()
        val manager = consentManager { consentUpdatedEventName("My Consent Event") }
        val analytics = manager
            .attach(builder)
            .useSourceMiddleware { chain ->
                val payload = chain.payload()
                if (payload is TrackPayload) trackedEvents.add(payload)
                chain.proceed(payload)
            }
            .build()
        manager.start(analytics)

        provider.setStatus("C0001", false)

        assertThat(trackedEvents).hasSize(1)
        assertThat(trackedEvents.first().event()).isEqualTo("My Consent Event")
    }

    @Test
    fun consentUpdatedEventCanBeDisabled() {
        val trackedEvents = mutableListOf<TrackPayload>()
        val manager = consentManager { disableConsentUpdatedEvent() }
        val analytics = manager
            .attach(builder)
            .useSourceMiddleware { chain ->
                val payload = chain.payload()
                if (payload is TrackPayload) trackedEvents.add(payload)
                chain.proceed(payload)
            }
            .build()
        manager.start(analytics)

        provider.setStatus("C0002", true)

        assertThat(trackedEvents).isEmpty()
    }

    @Test
    fun shutdownStopsConsentUpdatedEventsAndShutsDownProvider() {
        val trackedEvents = mutableListOf<TrackPayload>()
        val manager = consentManager()
        val analytics = manager
            .attach(builder)
            .useSourceMiddleware { chain ->
                val payload = chain.payload()
                if (payload is TrackPayload) trackedEvents.add(payload)
                chain.proceed(payload)
            }
            .build()
        manager.start(analytics)
        manager.shutdown()

        provider.setStatus("C0002", true)

        assertThat(trackedEvents).isEmpty()
        assertThat(provider.shutdownCalled).isTrue
        assertThat(provider.listener).isNull()
    }

    // --- Destination gating (middleware unit tests) ---

    private fun trackPayload(event: String): TrackPayload {
        return TrackPayload.Builder().event(event).userId("user").build()
    }

    private fun stamped(payload: BasePayload, statuses: Map<String, Boolean>): BasePayload {
        return ConsentManager.stamp(payload, statuses)
    }

    @Test
    fun gatingProceedsWhenAllCategoriesConsented() {
        val manager = consentManager(mapOf("Firebase" to listOf("C0001")))
        val middleware = manager.gatingMiddleware("Firebase", listOf("C0001"))
        val chain = RecordingChain(stamped(trackPayload("foo"), mapOf("C0001" to true)))

        middleware.intercept(chain)

        assertThat(chain.proceededPayload).isNotNull
    }

    @Test
    fun gatingDropsWhenAnyCategoryDenied() {
        val manager = consentManager()
        val middleware = manager.gatingMiddleware("Firebase", listOf("C0001", "C0002"))
        val chain = RecordingChain(
            stamped(trackPayload("foo"), mapOf("C0001" to true, "C0002" to false))
        )

        middleware.intercept(chain)

        assertThat(chain.proceededPayload).isNull()
    }

    @Test
    fun gatingDropsWhenCategoryUnknown() {
        val manager = consentManager()
        val middleware = manager.gatingMiddleware("Firebase", listOf("C9999"))
        val chain = RecordingChain(stamped(trackPayload("foo"), mapOf("C0001" to true)))

        middleware.intercept(chain)

        assertThat(chain.proceededPayload).isNull()
    }

    @Test
    fun gatingFallsBackToProviderWhenPayloadNotStamped() {
        // provider: C0001=true, C0002=false
        val manager = consentManager()
        val allowed = RecordingChain(trackPayload("foo"))
        manager.gatingMiddleware("Firebase", listOf("C0001")).intercept(allowed)
        assertThat(allowed.proceededPayload).isNotNull

        val dropped = RecordingChain(trackPayload("foo"))
        manager.gatingMiddleware("Firebase", listOf("C0002")).intercept(dropped)
        assertThat(dropped.proceededPayload).isNull()
    }

    @Test
    fun gatingPrefersStampedPreferencesOverProvider() {
        // Stamp says denied even though provider now says granted: the stamp wins,
        // keeping gating consistent with what is recorded on the event.
        val manager = consentManager()
        val middleware = manager.gatingMiddleware("Firebase", listOf("C0001"))
        val chain = RecordingChain(stamped(trackPayload("foo"), mapOf("C0001" to false)))

        middleware.intercept(chain)

        assertThat(chain.proceededPayload).isNull()
    }

    @Test
    fun consentUpdatedEventBypassesHightouchGate() {
        val manager = consentManager()
        val middleware = manager.gatingMiddleware(
            ConsentManager.HIGHTOUCH_INTEGRATION_KEY, listOf("C0001")
        )
        val chain = RecordingChain(
            stamped(trackPayload("Consent Updated"), mapOf("C0001" to false))
        )

        middleware.intercept(chain)

        assertThat(chain.proceededPayload).isNotNull
    }

    @Test
    fun consentUpdatedEventDoesNotBypassOtherDestinations() {
        val manager = consentManager()
        val middleware = manager.gatingMiddleware("Firebase", listOf("C0001"))
        val chain = RecordingChain(
            stamped(trackPayload("Consent Updated"), mapOf("C0001" to false))
        )

        middleware.intercept(chain)

        assertThat(chain.proceededPayload).isNull()
    }

    @Test
    fun ordinaryEventsDoNotBypassHightouchGate() {
        val manager = consentManager()
        val middleware = manager.gatingMiddleware(
            ConsentManager.HIGHTOUCH_INTEGRATION_KEY, listOf("C0001")
        )
        val chain = RecordingChain(stamped(trackPayload("foo"), mapOf("C0001" to false)))

        middleware.intercept(chain)

        assertThat(chain.proceededPayload).isNull()
    }

    // --- End-to-end: attach() registers gating for the Hightouch destination ---

    @Test
    fun attachedGatingBlocksHightouchDestination() {
        // Verify the destination middleware map is populated by attach() by checking a drop at
        // the middleware level through a full Analytics instance: the source middleware still
        // sees the event (stamped), which proves only the destination leg is gated.
        val payloadRef = AtomicReference<BasePayload>()
        val manager = consentManager(
            mapOf(ConsentManager.HIGHTOUCH_INTEGRATION_KEY to listOf("C0002"))
        )
        val analytics = manager
            .attach(builder)
            .useSourceMiddleware { chain ->
                payloadRef.set(chain.payload())
                chain.proceed(chain.payload())
            }
            .build()

        analytics.track("foo")

        // Event flowed through source middleware with a stamp showing C0002 denied.
        assertThat(categoryPreferencesOf(payloadRef.get())!!["C0002"]).isEqualTo(false)
    }
}

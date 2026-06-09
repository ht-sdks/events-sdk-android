package com.hightouch.analytics

import android.Manifest.permission.INTERNET
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.MoreExecutors
import com.hightouch.analytics.integrations.TrackPayload
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SessionTest {
    private lateinit var clock: TestClock

    @Before
    fun setUp() {
        Analytics.INSTANCES.clear()
        TestUtils.grantPermission(RuntimeEnvironment.application, INTERNET)
        clock = TestClock(1000)
    }

    @Test
    fun addsSessionContextToEveryEvent() {
        val events = mutableListOf<TrackPayload>()
        val analytics = makeAnalytics("session-context", events)

        analytics.track("First Event")
        clock.now = 1500
        analytics.track("Second Event")

        val firstContext = events[0].context()
        val firstSession = firstContext.getValueMap("session")
        assertEntries(firstContext, "sessionId" to 1000L, "sessionStart" to true)
        assertEntries(
            firstSession,
            "sessionId" to 1000L,
            "sessionIndex" to 0,
            "sessionStart" to true,
            "eventIndex" to 0,
            "previousSessionId" to null,
            "firstEventId" to events[0].messageId(),
            "firstEventTimestamp" to events[0].getString("timestamp")
        )

        val secondContext = events[1].context()
        val secondSession = secondContext.getValueMap("session")
        assertEntries(secondContext, "sessionId" to 1000L)
        assertThat(secondContext).doesNotContainKey("sessionStart")
        assertEntries(secondSession, "eventIndex" to 1)
        assertThat(secondSession).doesNotContainKey("sessionStart")
    }

    @Test
    fun rotatesOnForegroundInactivity() {
        val events = mutableListOf<TrackPayload>()
        val analytics = makeAnalytics("session-foreground-rotation", events)

        analytics.track("First Event")
        clock.now = 3001
        analytics.track("Rotated Event")

        val session = events[1].context().getValueMap("session")
        assertEntries(
            session,
            "sessionId" to 3001L,
            "sessionIndex" to 1,
            "sessionStart" to true,
            "eventIndex" to 0,
            "previousSessionId" to 1000L,
            "firstEventId" to events[1].messageId(),
            "firstEventTimestamp" to events[1].getString("timestamp")
        )
    }

    @Test
    fun rotatesWhenResetIsCalled() {
        val events = mutableListOf<TrackPayload>()
        val analytics = makeAnalytics("session-reset", events)

        analytics.track("First Event")
        clock.now = 2000
        analytics.reset()
        analytics.track("After Reset")

        val session = events[1].context().getValueMap("session")
        assertEntries(
            session,
            "sessionId" to 2000L,
            "sessionIndex" to 1,
            "sessionStart" to true,
            "eventIndex" to 0,
            "previousSessionId" to 1000L,
            "firstEventId" to events[1].messageId(),
            "firstEventTimestamp" to events[1].getString("timestamp")
        )
    }

    @Test
    fun doesNotEnrichWhenBothTimeoutsAreZero() {
        val events = mutableListOf<TrackPayload>()
        val analytics =
            makeAnalytics(
                "session-disabled",
                events,
                foregroundSessionTimeout = 0,
                backgroundSessionTimeout = 0
            )

        analytics.track("First Event")

        assertThat(events[0].context()).doesNotContainKey("session")
        assertThat(events[0].context()).doesNotContainKey("sessionId")
        assertThat(events[0].context()).doesNotContainKey("sessionStart")
    }

    @Test
    fun persistsSessionStateAcrossAnalyticsInstances() {
        val firstEvents = mutableListOf<TrackPayload>()
        val firstAnalytics = makeAnalytics("session-persistence", firstEvents)

        firstAnalytics.track("First Event")
        firstAnalytics.shutdown()

        val secondEvents = mutableListOf<TrackPayload>()
        clock.now = 1500
        val secondAnalytics = makeAnalytics("session-persistence", secondEvents)
        secondAnalytics.track("Second Event")

        val session = secondEvents[0].context().getValueMap("session")
        assertEntries(
            session,
            "sessionId" to 1000L,
            "sessionIndex" to 0,
            "eventIndex" to 1
        )
        assertThat(session).doesNotContainKey("sessionStart")
    }

    @Test
    fun rotatesAfterBackgroundTimeout() {
        val events = mutableListOf<TrackPayload>()
        val analytics = makeAnalytics("session-background-rotation", events)

        analytics.activityLifecycleCallback.onCreate(testLifecycleOwner)
        analytics.activityLifecycleCallback.onStart(testLifecycleOwner)
        analytics.track("First Event")
        clock.now = 1500
        analytics.activityLifecycleCallback.onStop(testLifecycleOwner)
        clock.now = 4000
        analytics.activityLifecycleCallback.onStart(testLifecycleOwner)
        analytics.track("Foreground Event")

        val session = events[1].context().getValueMap("session")
        assertEntries(
            session,
            "sessionId" to 4000L,
            "sessionIndex" to 1,
            "previousSessionId" to 1000L,
            "sessionStart" to true
        )
    }

    @Test
    fun preservesBackgroundTimestampWhenEventsAreProcessedWhileBackgrounded() {
        val events = mutableListOf<TrackPayload>()
        val analytics = makeAnalytics("session-background-event", events)

        analytics.activityLifecycleCallback.onCreate(testLifecycleOwner)
        analytics.activityLifecycleCallback.onStart(testLifecycleOwner)
        analytics.track("First Event")
        clock.now = 1500
        analytics.activityLifecycleCallback.onStop(testLifecycleOwner)
        clock.now = 1501
        analytics.track("Application Backgrounded")
        clock.now = 4000
        analytics.activityLifecycleCallback.onStart(testLifecycleOwner)
        analytics.track("Foreground Event")

        val backgroundSession = events[1].context().getValueMap("session")
        assertEntries(backgroundSession, "sessionId" to 1000L, "eventIndex" to 1)

        val foregroundSession = events[2].context().getValueMap("session")
        assertEntries(
            foregroundSession,
            "sessionId" to 4000L,
            "sessionIndex" to 1,
            "previousSessionId" to 1000L,
            "sessionStart" to true
        )
    }

    private fun makeAnalytics(
        tag: String,
        events: MutableList<TrackPayload>,
        foregroundSessionTimeout: Long = 2000,
        backgroundSessionTimeout: Long = 2000
    ): Analytics {
        return Analytics.Builder(RuntimeEnvironment.application, "write_key")
            .tag(tag)
            .executor(MoreExecutors.newDirectExecutorService())
            .experimentalUseNewLifecycleMethods(false)
            .foregroundSessionTimeout(foregroundSessionTimeout)
            .backgroundSessionTimeout(backgroundSessionTimeout)
            .sessionClock(clock)
            .useSourceMiddleware { chain ->
                events.add(chain.payload() as TrackPayload)
                chain.proceed(chain.payload())
            }
            .build()
    }

    private fun assertEntries(map: Map<String, Any?>?, vararg entries: Pair<String, Any?>) {
        assertThat(map).isNotNull()
        entries.forEach { (key, value) ->
            assertThat(map!!.containsKey(key)).isTrue()
            assertThat(map[key]).isEqualTo(value)
        }
    }

    private class TestClock(var now: Long) : SessionPlugin.Clock {
        override fun currentTimeMillis(): Long = now
    }

    private val testLifecycleOwner =
        object : LifecycleOwner {
            override fun getLifecycle(): Lifecycle {
                return object : Lifecycle() {
                    override fun addObserver(observer: LifecycleObserver) {}

                    override fun removeObserver(observer: LifecycleObserver) {}

                    override fun getCurrentState(): Lifecycle.State = Lifecycle.State.RESUMED
                }
            }
        }
}

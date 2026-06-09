package com.hightouch.analytics

import com.hightouch.analytics.internal.Utils.getSegmentSharedPreferences
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SessionStateCacheTest {
    private lateinit var cache: SessionState.Cache

    @Before
    fun setUp() {
        cache =
            SessionState.Cache(
                RuntimeEnvironment.application,
                Cartographer.INSTANCE,
                "session-state-cache-test"
            )
        cache.delete()
        assertThat(cache.get()).isNull()
    }

    @Test
    fun savesAndLoadsSessionState() {
        val state =
            SessionState(
                1000,
                0,
                null,
                "message-id",
                "2026-01-01T00:00:01.000Z",
                1,
                1000,
                null
            )

        cache.set(state)
        assertThat(
            getSegmentSharedPreferences(RuntimeEnvironment.application, "session-state-cache-test")
                .edit()
                .commit()
        ).isTrue()
        val freshCache =
            SessionState.Cache(
                RuntimeEnvironment.application,
                Cartographer.INSTANCE,
                "session-state-cache-test"
            )
        val cachedState = requireNotNull(freshCache.get()) { "Expected cached session state." }

        assertThat(cachedState.sessionId()).isEqualTo(1000L)
        assertThat(cachedState.sessionIndex()).isEqualTo(0)
        val previousSessionId: Long? = cachedState.previousSessionId()
        assertThat(previousSessionId).isNull()
        assertThat(cachedState.firstEventId()).isEqualTo("message-id")
        assertThat(cachedState.firstEventTimestamp()).isEqualTo("2026-01-01T00:00:01.000Z")
        assertThat(cachedState.eventIndex()).isEqualTo(1)
        assertThat(cachedState.lastActivityAt()).isEqualTo(1000L)
        val backgroundedAt: Long? = cachedState.backgroundedAt()
        assertThat(backgroundedAt).isNull()
    }

    @Test
    fun deleteClearsSessionState() {
        cache.set(
            SessionState(
                1000,
                0,
                null,
                "message-id",
                "2026-01-01T00:00:01.000Z",
                1,
                1000,
                null
            )
        )

        cache.delete()

        val freshCache =
            SessionState.Cache(
                RuntimeEnvironment.application,
                Cartographer.INSTANCE,
                "session-state-cache-test"
            )
        assertThat(freshCache.get()).isNull()
    }

}

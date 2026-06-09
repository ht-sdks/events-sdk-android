package com.hightouch.analytics

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SessionPluginHelperTest {
    private val initialState =
        SessionState(
            1000,
            0,
            null,
            "first-message-id",
            "2026-01-01T00:00:01.000Z",
            1,
            1000,
            null
        )

    @Test
    fun createsFirstSessionOnFirstEvent() {
        val result =
            SessionPluginHelper.processEvent(
                null,
                1000,
                "message-id",
                "2026-01-01T00:00:01.000Z",
                1_800_000,
                1_800_000,
                false
            )

        assertEntries(
            result.contextSession,
            "sessionId" to 1000L,
            "sessionIndex" to 0,
            "sessionStart" to true,
            "eventIndex" to 0,
            "previousSessionId" to null,
            "firstEventId" to "message-id",
            "firstEventTimestamp" to "2026-01-01T00:00:01.000Z"
        )
        assertEntries(
            result.sessionState,
            "sessionId" to 1000L,
            "sessionIndex" to 0,
            "previousSessionId" to null,
            "firstEventId" to "message-id",
            "firstEventTimestamp" to "2026-01-01T00:00:01.000Z",
            "eventIndex" to 1,
            "lastActivityAt" to 1000L,
            "backgroundedAt" to null
        )
    }

    @Test
    fun incrementsEventIndexWithinSession() {
        val result =
            SessionPluginHelper.processEvent(
                initialState,
                2000,
                "second-message-id",
                "2026-01-01T00:00:02.000Z",
                1_800_000,
                1_800_000,
                false
            )

        assertEntries(
            result.contextSession,
            "sessionId" to 1000L,
            "sessionIndex" to 0,
            "eventIndex" to 1,
            "previousSessionId" to null,
            "firstEventId" to "first-message-id",
            "firstEventTimestamp" to "2026-01-01T00:00:01.000Z"
        )
        assertThat(result.contextSession).doesNotContainKey("sessionStart")
        assertThat(result.sessionState.eventIndex()).isEqualTo(2)
        assertThat(result.sessionState.lastActivityAt()).isEqualTo(2000)
    }

    @Test
    fun rotatesAfterForegroundInactivityExceedsTimeout() {
        val result =
            SessionPluginHelper.processEvent(
                initialState,
                3000,
                "new-session-message-id",
                "2026-01-01T00:00:03.000Z",
                1999,
                1_800_000,
                false
            )

        assertEntries(
            result.contextSession,
            "sessionId" to 3000L,
            "sessionIndex" to 1,
            "sessionStart" to true,
            "eventIndex" to 0,
            "previousSessionId" to 1000L,
            "firstEventId" to "new-session-message-id",
            "firstEventTimestamp" to "2026-01-01T00:00:03.000Z"
        )
    }

    @Test
    fun doesNotRotateWhenForegroundInactivityEqualsTimeout() {
        val result =
            SessionPluginHelper.processEvent(
                initialState,
                3000,
                "same-session-message-id",
                "2026-01-01T00:00:03.000Z",
                2000,
                1_800_000,
                false
            )

        assertEntries(result.contextSession, "sessionId" to 1000L, "eventIndex" to 1)
        assertThat(result.contextSession).doesNotContainKey("sessionStart")
    }

    @Test
    fun rotatesOnFirstEventAfterLongBackgroundDuration() {
        val backgroundedState = initialState.copy().putBackgroundedAt(1500)
        val foregroundedState =
            SessionPluginHelper.markForegrounded(backgroundedState, 4000, 2000)
        val result =
            SessionPluginHelper.processEvent(
                foregroundedState,
                4000,
                "foreground-message-id",
                "2026-01-01T00:00:04.000Z",
                1_800_000,
                2000,
                false
            )

        assertEntries(
            result.contextSession,
            "sessionId" to 4000L,
            "sessionIndex" to 1,
            "sessionStart" to true,
            "eventIndex" to 0,
            "previousSessionId" to 1000L,
            "firstEventId" to "foreground-message-id",
            "firstEventTimestamp" to "2026-01-01T00:00:04.000Z"
        )
    }

    @Test
    fun coldStartRotatesWhenPersistedBackgroundedAtExceeded() {
        val backgroundedState = initialState.copy().putBackgroundedAt(1500)
        val result =
            SessionPluginHelper.processEvent(
                backgroundedState,
                4000,
                "cold-start-message-id",
                "2026-01-01T00:00:04.000Z",
                1_800_000,
                2000,
                false
            )

        assertEntries(
            result.contextSession,
            "sessionId" to 4000L,
            "sessionIndex" to 1,
            "previousSessionId" to 1000L,
            "sessionStart" to true
        )
    }

    @Test
    fun backgroundEventsDoNotUpdateActivity() {
        val backgroundedState = initialState.copy().putBackgroundedAt(1500)
        val result =
            SessionPluginHelper.processEvent(
                backgroundedState,
                1501,
                "background-message-id",
                "2026-01-01T00:00:01.501Z",
                1_800_000,
                2000,
                true
            )

        assertEntries(result.contextSession, "sessionId" to 1000L, "eventIndex" to 1)
        assertThat(result.sessionState.lastActivityAt()).isEqualTo(1000)
        assertThat(result.sessionState.backgroundedAt()).isEqualTo(1500)
    }

    @Test
    fun rotateSessionOnResetIncrementsSessionIndex() {
        val result =
            SessionPluginHelper.rotateSession(
                initialState,
                4000,
                "",
                "2026-01-01T00:00:04.000Z"
            )

        assertEntries(
            result,
            "sessionId" to 4000L,
            "sessionIndex" to 1,
            "previousSessionId" to 1000L,
            "firstEventId" to "",
            "firstEventTimestamp" to "2026-01-01T00:00:04.000Z",
            "eventIndex" to 0
        )
    }

    @Test
    fun isEnabledOnlyFalseWhenBothTimeoutsAreZero() {
        assertThat(SessionPluginHelper.isEnabled(1, 1)).isTrue()
        assertThat(SessionPluginHelper.isEnabled(1, 0)).isTrue()
        assertThat(SessionPluginHelper.isEnabled(0, 1)).isTrue()
        assertThat(SessionPluginHelper.isEnabled(0, 0)).isFalse()
    }

    private fun assertEntries(map: Map<String, Any?>, vararg entries: Pair<String, Any?>) {
        entries.forEach { (key, value) ->
            assertThat(map.containsKey(key)).isTrue()
            assertThat(map[key]).isEqualTo(value)
        }
    }
}

package com.hightouch.analytics;

import java.util.LinkedHashMap;
import java.util.Map;

class SessionPluginHelper {
    private SessionPluginHelper() {}

    static boolean isEnabled(long foregroundSessionTimeout, long backgroundSessionTimeout) {
        return !(foregroundSessionTimeout == 0 && backgroundSessionTimeout == 0);
    }

    static boolean shouldRotateOnResume(
            SessionState state, long now, long backgroundSessionTimeout) {
        if (state == null || state.backgroundedAt() == null || backgroundSessionTimeout <= 0) {
            return false;
        }

        return now - state.backgroundedAt() > backgroundSessionTimeout;
    }

    static boolean shouldRotateOnInactivity(
            SessionState state, long now, long foregroundSessionTimeout) {
        if (state == null || foregroundSessionTimeout <= 0) {
            return false;
        }

        return now - state.lastActivityAt() > foregroundSessionTimeout;
    }

    static SessionState rotateSession(
            SessionState state, long now, String firstEventId, String firstEventTimestamp) {
        return new SessionState(
                now,
                state == null ? 0 : state.sessionIndex() + 1,
                state == null ? null : state.sessionId(),
                firstEventId,
                firstEventTimestamp,
                0,
                now,
                null);
    }

    static EnrichedSessionEvent enrichEvent(
            SessionState state, long now, boolean updateActivity) {
        boolean sessionStart = state.eventIndex() == 0;
        ContextSession contextSession =
                new ContextSession(
                        state.sessionId(),
                        state.sessionIndex(),
                        sessionStart ? Boolean.TRUE : null,
                        state.eventIndex(),
                        state.previousSessionId(),
                        state.firstEventId(),
                        state.firstEventTimestamp());

        SessionState sessionState = state.copy();
        sessionState.putEventIndex(state.eventIndex() + 1);
        if (updateActivity) {
            sessionState.putLastActivityAt(now);
            sessionState.putBackgroundedAt(null);
        } else {
            sessionState.putLastActivityAt(state.lastActivityAt());
            sessionState.putBackgroundedAt(state.backgroundedAt());
        }

        return new EnrichedSessionEvent(contextSession, sessionState);
    }

    static SessionState ensureFirstEvent(
            SessionState state, String messageId, String timestamp) {
        if (state.eventIndex() != 0 || !"".equals(state.firstEventId())) {
            return state;
        }

        return state.copy().putFirstEvent(messageId, timestamp);
    }

    static EnrichedSessionEvent processEvent(
            SessionState state,
            long now,
            String messageId,
            String timestamp,
            long foregroundSessionTimeout,
            long backgroundSessionTimeout,
            boolean isAppInBackground) {
        boolean shouldRotate =
                state == null
                        || (!isAppInBackground
                                && (shouldRotateOnResume(state, now, backgroundSessionTimeout)
                                        || shouldRotateOnInactivity(
                                                state, now, foregroundSessionTimeout)));

        SessionState currentState =
                shouldRotate
                        ? rotateSession(state, now, messageId, timestamp)
                        : ensureFirstEvent(state, messageId, timestamp);

        return enrichEvent(currentState, now, !isAppInBackground);
    }

    static SessionState markBackgrounded(SessionState state, long now) {
        if (state == null) {
            return null;
        }

        Long backgroundedAt = state.backgroundedAt() != null ? state.backgroundedAt() : now;
        return state.copy().putBackgroundedAt(backgroundedAt);
    }

    static SessionState markForegrounded(
            SessionState state, long now, long backgroundSessionTimeout) {
        if (state == null) {
            return null;
        }

        if (shouldRotateOnResume(state, now, backgroundSessionTimeout)) {
            return state;
        }

        return state.copy().putLastActivityAt(now).putBackgroundedAt(null);
    }

    static class EnrichedSessionEvent {
        final ContextSession contextSession;
        final SessionState sessionState;

        EnrichedSessionEvent(ContextSession contextSession, SessionState sessionState) {
            this.contextSession = contextSession;
            this.sessionState = sessionState;
        }
    }

    static class ContextSession extends ValueMap {
        private static final String SESSION_ID_KEY = "sessionId";
        private static final String SESSION_INDEX_KEY = "sessionIndex";
        private static final String SESSION_START_KEY = "sessionStart";
        private static final String EVENT_INDEX_KEY = "eventIndex";
        private static final String PREVIOUS_SESSION_ID_KEY = "previousSessionId";
        private static final String FIRST_EVENT_ID_KEY = "firstEventId";
        private static final String FIRST_EVENT_TIMESTAMP_KEY = "firstEventTimestamp";

        ContextSession(
                long sessionId,
                int sessionIndex,
                Boolean sessionStart,
                int eventIndex,
                Long previousSessionId,
                String firstEventId,
                String firstEventTimestamp) {
            super(new LinkedHashMap<String, Object>());
            putValue(SESSION_ID_KEY, sessionId);
            putValue(SESSION_INDEX_KEY, sessionIndex);
            if (Boolean.TRUE.equals(sessionStart)) {
                putValue(SESSION_START_KEY, true);
            }
            putValue(EVENT_INDEX_KEY, eventIndex);
            putValue(PREVIOUS_SESSION_ID_KEY, previousSessionId);
            putValue(FIRST_EVENT_ID_KEY, firstEventId);
            putValue(FIRST_EVENT_TIMESTAMP_KEY, firstEventTimestamp);
        }

        long sessionId() {
            return getLong(SESSION_ID_KEY, 0);
        }

        boolean isSessionStart() {
            return getBoolean(SESSION_START_KEY, false);
        }

        Map<String, Object> toMap() {
            return new LinkedHashMap<String, Object>(this);
        }
    }
}

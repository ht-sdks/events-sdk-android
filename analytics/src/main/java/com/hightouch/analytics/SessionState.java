package com.hightouch.analytics;

import android.content.Context;
import java.util.LinkedHashMap;
import java.util.Map;

class SessionState extends ValueMap {
    private static final String SESSION_CACHE_KEY = "sessionState";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String SESSION_INDEX_KEY = "sessionIndex";
    private static final String PREVIOUS_SESSION_ID_KEY = "previousSessionId";
    private static final String FIRST_EVENT_ID_KEY = "firstEventId";
    private static final String FIRST_EVENT_TIMESTAMP_KEY = "firstEventTimestamp";
    private static final String EVENT_INDEX_KEY = "eventIndex";
    private static final String LAST_ACTIVITY_AT_KEY = "lastActivityAt";
    private static final String BACKGROUNDED_AT_KEY = "backgroundedAt";

    SessionState(Map<String, Object> map) {
        super(map);
    }

    SessionState(
            long sessionId,
            int sessionIndex,
            Long previousSessionId,
            String firstEventId,
            String firstEventTimestamp,
            int eventIndex,
            long lastActivityAt,
            Long backgroundedAt) {
        super(new LinkedHashMap<String, Object>());
        putValue(SESSION_ID_KEY, sessionId);
        putValue(SESSION_INDEX_KEY, sessionIndex);
        putValue(PREVIOUS_SESSION_ID_KEY, previousSessionId);
        putValue(FIRST_EVENT_ID_KEY, firstEventId);
        putValue(FIRST_EVENT_TIMESTAMP_KEY, firstEventTimestamp);
        putValue(EVENT_INDEX_KEY, eventIndex);
        putValue(LAST_ACTIVITY_AT_KEY, lastActivityAt);
        putValue(BACKGROUNDED_AT_KEY, backgroundedAt);
    }

    long sessionId() {
        return getLong(SESSION_ID_KEY, 0);
    }

    int sessionIndex() {
        return getInt(SESSION_INDEX_KEY, 0);
    }

    Long previousSessionId() {
        return getNullableLong(PREVIOUS_SESSION_ID_KEY);
    }

    String firstEventId() {
        String firstEventId = getString(FIRST_EVENT_ID_KEY);
        return firstEventId == null ? "" : firstEventId;
    }

    String firstEventTimestamp() {
        String firstEventTimestamp = getString(FIRST_EVENT_TIMESTAMP_KEY);
        return firstEventTimestamp == null ? "" : firstEventTimestamp;
    }

    int eventIndex() {
        return getInt(EVENT_INDEX_KEY, 0);
    }

    long lastActivityAt() {
        return getLong(LAST_ACTIVITY_AT_KEY, 0);
    }

    Long backgroundedAt() {
        return getNullableLong(BACKGROUNDED_AT_KEY);
    }

    SessionState copy() {
        return new SessionState(new LinkedHashMap<String, Object>(this));
    }

    SessionState putEventIndex(int eventIndex) {
        putValue(EVENT_INDEX_KEY, eventIndex);
        return this;
    }

    SessionState putLastActivityAt(long lastActivityAt) {
        putValue(LAST_ACTIVITY_AT_KEY, lastActivityAt);
        return this;
    }

    SessionState putBackgroundedAt(Long backgroundedAt) {
        putValue(BACKGROUNDED_AT_KEY, backgroundedAt);
        return this;
    }

    SessionState putFirstEvent(String firstEventId, String firstEventTimestamp) {
        putValue(FIRST_EVENT_ID_KEY, firstEventId);
        putValue(FIRST_EVENT_TIMESTAMP_KEY, firstEventTimestamp);
        return this;
    }

    private Long getNullableLong(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.valueOf((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static class Cache extends ValueMap.Cache<SessionState> {
        Cache(Context context, Cartographer cartographer, String tag) {
            super(context, cartographer, SESSION_CACHE_KEY, tag, SessionState.class);
        }
    }
}

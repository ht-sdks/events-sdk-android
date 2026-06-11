package com.hightouch.analytics;

import static com.hightouch.analytics.internal.Utils.toISO8601String;

import android.content.Context;
import androidx.annotation.Nullable;
import com.hightouch.analytics.integrations.BasePayload;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

class SessionPlugin {
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String SESSION_KEY = "session";
    private static final String SESSION_ID_KEY = "sessionId";
    private static final String SESSION_START_KEY = "sessionStart";

    private final SessionState.Cache sessionStateCache;
    private final long foregroundSessionTimeout;
    private final long backgroundSessionTimeout;
    private final Clock clock;
    private boolean isAppInBackground;

    SessionPlugin(
            Context context,
            Cartographer cartographer,
            String tag,
            long foregroundSessionTimeout,
            long backgroundSessionTimeout,
            Clock clock) {
        this(
                new SessionState.Cache(context, cartographer, tag),
                foregroundSessionTimeout,
                backgroundSessionTimeout,
                clock);
    }

    SessionPlugin(
            SessionState.Cache sessionStateCache,
            long foregroundSessionTimeout,
            long backgroundSessionTimeout,
            Clock clock) {
        this.sessionStateCache = sessionStateCache;
        this.foregroundSessionTimeout = foregroundSessionTimeout;
        this.backgroundSessionTimeout = backgroundSessionTimeout;
        this.clock = clock;
    }

    synchronized BasePayload enrich(BasePayload payload) {
        long now = clock.currentTimeMillis();
        SessionPluginHelper.EnrichedSessionEvent result =
                SessionPluginHelper.processEvent(
                        sessionStateCache.get(),
                        now,
                        payload.messageId(),
                        payload.getString(TIMESTAMP_KEY),
                        foregroundSessionTimeout,
                        backgroundSessionTimeout,
                        isAppInBackground);
        sessionStateCache.set(result.sessionState);

        Map<String, Object> context = new LinkedHashMap<>(payload.context());
        context.remove(SESSION_START_KEY);
        context.put(SESSION_KEY, result.contextSession.toMap());
        context.put(SESSION_ID_KEY, result.contextSession.sessionId());
        if (result.contextSession.isSessionStart()) {
            context.put(SESSION_START_KEY, true);
        }

        return payload.toBuilder().context(context).build();
    }

    synchronized void reset() {
        long now = clock.currentTimeMillis();
        SessionState state =
                SessionPluginHelper.rotateSession(
                        sessionStateCache.get(), now, "", toISO8601String(new Date(now)));
        sessionStateCache.set(state);
    }

    synchronized void markBackgrounded() {
        if (isAppInBackground) {
            return;
        }
        isAppInBackground = true;
        updateState(
                SessionPluginHelper.markBackgrounded(
                        sessionStateCache.get(), clock.currentTimeMillis()));
    }

    synchronized void markForegrounded() {
        if (!isAppInBackground) {
            return;
        }
        isAppInBackground = false;
        updateState(
                SessionPluginHelper.markForegrounded(
                        sessionStateCache.get(),
                        clock.currentTimeMillis(),
                        backgroundSessionTimeout));
    }

    private void updateState(@Nullable SessionState state) {
        if (state != null) {
            sessionStateCache.set(state);
        }
    }

    interface Clock {
        long currentTimeMillis();
    }

    static class SystemClock implements Clock {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }
}

package com.hightouch.analytics.braze;

import androidx.annotation.NonNull;

import com.hightouch.analytics.integrations.TrackPayload;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Which {@code track} calls are logged as Braze purchases. */
public abstract class PurchaseDetection {
    private PurchaseDetection() {}

    /** {@code track} event names (exact, case-sensitive) logged as purchases. */
    @NonNull
    public static PurchaseDetection eventNames(@NonNull String... names) {
        return new EventNames(new HashSet<>(Arrays.asList(names)));
    }

    /**
     * Decide which {@code track} calls are purchases yourself. If it throws, the event is
     * logged as a custom event.
     */
    @NonNull
    public static PurchaseDetection matcher(@NonNull PurchaseEventMatcher matcher) {
        return new Custom(matcher);
    }

    abstract boolean isPurchaseEvent(TrackPayload track);

    private static final class EventNames extends PurchaseDetection {
        private final Set<String> names;

        EventNames(Set<String> names) {
            this.names = names;
        }

        @Override
        boolean isPurchaseEvent(TrackPayload track) {
            return names.contains(track.event());
        }
    }

    private static final class Custom extends PurchaseDetection {
        private final PurchaseEventMatcher matcher;

        Custom(PurchaseEventMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        boolean isPurchaseEvent(TrackPayload track) {
            return matcher.isPurchaseEvent(track);
        }
    }
}

package com.hightouch.analytics.onetrust;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hightouch.analytics.ConsentCategoryProvider;
import com.onetrust.otpublishers.headless.Public.Keys.OTBroadcastServiceKeys;
import com.onetrust.otpublishers.headless.Public.OTPublishersHeadlessSDK;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link ConsentCategoryProvider} backed by the OneTrust native SDK. The app owns the OneTrust
 * SDK instance and its lifecycle ({@code startSDK}, banner UI, version pinning); this provider
 * only reads consent for the given category (group) IDs and listens for changes via OneTrust's
 * per-category broadcasts. Any OneTrust status other than {@code 1} (given) is treated as not
 * consented. See analytics-onetrust/README.md.
 */
public class OneTrustConsentProvider implements ConsentCategoryProvider {

    private static final int STATUS_GRANTED = 1;
    private static final int STATUS_UNKNOWN = -1;

    private final Context applicationContext;
    private final List<String> categoryIds;
    private final Map<String, Boolean> statuses = new ConcurrentHashMap<>();
    private final List<BroadcastReceiver> receivers = new ArrayList<>();
    private volatile ConsentChangeListener listener;
    private volatile boolean shutdown = false;

    public OneTrustConsentProvider(
            @NonNull Context context,
            @NonNull final OTPublishersHeadlessSDK oneTrust,
            @NonNull List<String> categoryIds) {
        if (categoryIds.isEmpty()) {
            throw new IllegalArgumentException("categoryIds must not be empty.");
        }
        this.applicationContext = context.getApplicationContext();
        this.categoryIds = Collections.unmodifiableList(new ArrayList<>(categoryIds));
        for (String categoryId : this.categoryIds) {
            statuses.put(categoryId, isGranted(oneTrust.getConsentStatusForGroupId(categoryId)));
        }
        registerReceivers();
    }

    @NonNull
    @Override
    public Map<String, Boolean> getConsentStatuses() {
        // Preserve the caller-supplied category order in the snapshot.
        Map<String, Boolean> snapshot = new LinkedHashMap<>();
        for (String categoryId : categoryIds) {
            snapshot.put(categoryId, Boolean.TRUE.equals(statuses.get(categoryId)));
        }
        return snapshot;
    }

    @Override
    public void setConsentChangeListener(@Nullable ConsentChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void shutdown() {
        shutdown = true;
        listener = null;
        for (BroadcastReceiver receiver : receivers) {
            try {
                applicationContext.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
        }
        receivers.clear();
    }

    /**
     * OneTrust broadcasts consent changes per category: intent action = category ID, new status in
     * the {@link OTBroadcastServiceKeys#EVENT_STATUS} extra.
     */
    private void registerReceivers() {
        for (final String categoryId : categoryIds) {
            BroadcastReceiver receiver =
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            if (shutdown) {
                                return;
                            }
                            int status =
                                    intent.getIntExtra(
                                            OTBroadcastServiceKeys.EVENT_STATUS, STATUS_UNKNOWN);
                            boolean granted = isGranted(status);
                            Boolean previous = statuses.put(categoryId, granted);
                            if (previous != null && previous == granted) {
                                return; // No change; don't spam the listener.
                            }
                            ConsentChangeListener changeListener = listener;
                            if (changeListener != null) {
                                changeListener.onConsentChange(getConsentStatuses());
                            }
                        }
                    };
            IntentFilter filter = new IntentFilter(categoryId);
            if (Build.VERSION.SDK_INT >= 33) {
                // OneTrust delivers these as implicit broadcasts, which on API 33+ require an
                // exported runtime receiver (per OneTrust's integration docs).
                applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                applicationContext.registerReceiver(receiver, filter);
            }
            receivers.add(receiver);
        }
    }

    private static boolean isGranted(int oneTrustStatus) {
        return oneTrustStatus == STATUS_GRANTED;
    }
}

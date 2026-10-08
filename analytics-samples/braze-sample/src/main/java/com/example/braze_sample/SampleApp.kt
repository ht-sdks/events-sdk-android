package com.example.braze_sample

import android.app.Application
import android.util.Log
import com.braze.BrazeActivityLifecycleCallbackListener
import com.braze.support.BrazeLogger
import com.hightouch.analytics.Analytics
import com.hightouch.analytics.braze.BrazeIntegration
import com.hightouch.analytics.braze.PurchaseDetection
import com.hightouch.analytics.braze.PurchaseGrouping

private const val HT_WRITE_KEY = "HT_WRITE_KEY"
private const val BRAZE_API_KEY = "BRAZE_API_KEY"
private const val BRAZE_ENDPOINT = "sdk.iad-03.braze.com"

/** Flip and relaunch to log one purchase per order. */
private const val perOrder = false

class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()

        BrazeLogger.logLevel = Log.VERBOSE

        val braze =
            BrazeIntegration.builder(this, BRAZE_API_KEY, BRAZE_ENDPOINT)
                .forwardScreenViews(true)
                .purchaseDetection(
                    PurchaseDetection.eventNames(
                        "Order Completed",
                        "Completed Order",
                        "Membership Purchased",
                    ),
                )
                .apply {
                    if (perOrder) {
                        purchaseGrouping(PurchaseGrouping.perOrder())
                    }
                }
                .build()

        // The plugin opens and closes sessions. This listener is only for in-app messages.
        registerActivityLifecycleCallbacks(
            BrazeActivityLifecycleCallbackListener(
                sessionHandlingEnabled = false,
                registerInAppMessageManager = true,
            ),
        )

        val analytics =
            Analytics.Builder(this, HT_WRITE_KEY)
                .defaultApiHost("us-east-1.hightouch-events.com/v1")
                .logLevel(Analytics.LogLevel.DEBUG)
                .use(braze)
                .build()
        Analytics.setSingletonInstance(analytics)
    }
}

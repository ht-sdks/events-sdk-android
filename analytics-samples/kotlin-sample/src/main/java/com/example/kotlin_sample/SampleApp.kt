/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Segment.io, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.example.kotlin_sample

import android.app.Application
import android.util.Log
import com.hightouch.analytics.Analytics
import com.hightouch.analytics.ConsentCategoryProvider
import com.hightouch.analytics.ConsentManager
import com.hightouch.analytics.Middleware
import com.hightouch.analytics.ValueMap
import com.hightouch.analytics.integrations.BasePayload
import com.hightouch.analytics.integrations.TrackPayload
import com.hightouch.analytics.onetrust.OneTrustConsentProvider
import com.onetrust.otpublishers.headless.Public.OTPublishersHeadlessSDK
import io.github.inflationx.calligraphy3.CalligraphyConfig
import io.github.inflationx.calligraphy3.CalligraphyInterceptor
import io.github.inflationx.viewpump.ViewPump

class SampleApp : Application() {

    companion object {
        /** Set when [ConsentConfig.isOneTrustConfigured]; used by MainActivity to show the banner. */
        var oneTrustSdk: OTPublishersHeadlessSDK? = null
    }

    private val ANALYTICS_WRITE_KEY: String = "your write key"
    override fun onCreate() {
        super.onCreate()

        ViewPump.init(
            ViewPump.builder()
                .addInterceptor(
                    CalligraphyInterceptor(
                        CalligraphyConfig.Builder()
                            .setDefaultFontPath("fonts/CircularStd-Book.otf")
                            .setFontAttrId(io.github.inflationx.calligraphy3.R.attr.fontPath)
                            .build()
                    )
                )
                .build()
        )

        // Set up consent: a real OneTrust-backed provider when configured, otherwise the
        // in-app fake provider toggled from MainActivity.
        val consentProvider: ConsentCategoryProvider =
            if (ConsentConfig.isOneTrustConfigured) {
                val oneTrust = OTPublishersHeadlessSDK(this)
                oneTrustSdk = oneTrust
                OneTrustConsentProvider(this, oneTrust, ConsentConfig.CATEGORIES)
            } else {
                FakeConsentProvider
            }
        val consentManager = ConsentManager.Builder(consentProvider)
            .eventCategoryMappings(ConsentConfig.EVENT_CATEGORY_MAPPINGS)
            .lifecycleEventCategories(listOf(FakeConsentProvider.CATEGORY_ANALYTICS))
            .eventTypeCategoryMapping(
                BasePayload.Type.screen,
                listOf(FakeConsentProvider.CATEGORY_ANALYTICS)
            )
            .build()

        // Initialize a new instance of the Analytics client.
        val builder = Analytics.Builder(this, ANALYTICS_WRITE_KEY)
            .logLevel(Analytics.LogLevel.VERBOSE)
            .experimentalNanosecondTimestamps()
            .trackApplicationLifecycleEvents()
            .defaultProjectSettings(
                ValueMap()
                    .putValue(
                        "integrations",
                        ValueMap()
                            .putValue(
                                "adjust",
                                ValueMap()
                                    .putValue("appToken", "<>")
                                    .putValue(
                                        "trackAttributionData",
                                        true
                                    )
                            )
                    )
            )
            .useSourceMiddleware(
                Middleware { chain ->
                    if (chain.payload().type() == BasePayload.Type.track) {
                        val payload = chain.payload() as TrackPayload
                        if (payload.event()
                            .equals("Button B Clicked", ignoreCase = true)
                        ) {
                            chain.proceed(payload.toBuilder().build())
                            return@Middleware
                        }
                    }
                    chain.proceed(chain.payload())
                }
            )
            .useDestinationMiddleware(
                "Segment.io",
                Middleware { chain ->
                    if (chain.payload().type() == BasePayload.Type.track) {
                        val payload = chain.payload() as TrackPayload
                        if (payload.event()
                            .equals("Button B Clicked", ignoreCase = true)
                        ) {
                            chain.proceed(payload.toBuilder().build())
                            return@Middleware
                        }
                    }
                    chain.proceed(chain.payload())
                }
            )
            .flushQueueSize(1)
            .recordScreenViews()

        consentManager.attach(builder)
        builder.useSourceMiddleware(
            Middleware { chain ->
                val payload = chain.payload()
                if (payload.type() == BasePayload.Type.track) {
                    Log.d(
                        "Consent sample",
                        "source allowed: ${(payload as TrackPayload).event()}"
                    )
                } else {
                    Log.d("Consent sample", "source allowed: ${payload.type()}")
                }
                chain.proceed(payload)
            }
        )

        Analytics.setSingletonInstance(builder.build())

        val analytics = Analytics.with(this)
        consentManager.start(analytics)

        analytics.onIntegrationReady(
            "Segment.io",
            Analytics.Callback<Any?> {
                Log.d("Segment Sample", "Segment integration ready.")
            }
        )
    }
}

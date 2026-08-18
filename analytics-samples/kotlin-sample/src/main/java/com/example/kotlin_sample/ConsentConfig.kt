package com.example.kotlin_sample

import com.hightouch.analytics.ConsentManager

/**
 * Consent demo configuration.
 *
 * By default the sample uses [FakeConsentProvider] with in-app toggle switches. To demo the real
 * OneTrust banner instead, fill in [ONETRUST_DOMAIN_URL] and [ONETRUST_DOMAIN_ID] with values from
 * your OneTrust tenant (Mobile App > SDKs) and make sure the OneTrust SDK version in
 * kotlin-sample/build.gradle matches the version published in your tenant.
 */
object ConsentConfig {

    const val ONETRUST_DOMAIN_URL = "" // e.g. "cdn.cookielaw.org"
    const val ONETRUST_DOMAIN_ID = "" // e.g. "0190xxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx-test"
    const val ONETRUST_LANGUAGE = "en"

    /** The CMP categories the sample tracks. */
    val CATEGORIES = listOf(
        FakeConsentProvider.CATEGORY_ANALYTICS,
        FakeConsentProvider.CATEGORY_ADVERTISING
    )

    /**
     * Destinations gated by category: the Hightouch cloud destination only receives events when
     * the analytics category (C0002) is consented.
     */
    val INTEGRATION_CATEGORY_MAPPINGS = mapOf(
        ConsentManager.HIGHTOUCH_INTEGRATION_KEY to listOf(FakeConsentProvider.CATEGORY_ANALYTICS)
    )

    val isOneTrustConfigured: Boolean
        get() = ONETRUST_DOMAIN_URL.isNotEmpty() && ONETRUST_DOMAIN_ID.isNotEmpty()
}

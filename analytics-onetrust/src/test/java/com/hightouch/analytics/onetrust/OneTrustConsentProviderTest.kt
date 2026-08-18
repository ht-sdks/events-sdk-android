package com.hightouch.analytics.onetrust

import android.app.Application
import android.content.Intent
import android.os.Looper
import com.hightouch.analytics.ConsentCategoryProvider
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.onetrust.otpublishers.headless.Public.Keys.OTBroadcastServiceKeys
import com.onetrust.otpublishers.headless.Public.OTPublishersHeadlessSDK
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OneTrustConsentProviderTest {

    private lateinit var application: Application
    private lateinit var oneTrust: OTPublishersHeadlessSDK

    @Before
    fun setUp() {
        application = RuntimeEnvironment.application
        oneTrust = mock()
        whenever(oneTrust.getConsentStatusForGroupId("C0002")).thenReturn(1) // granted
        whenever(oneTrust.getConsentStatusForGroupId("C0004")).thenReturn(0) // denied
        whenever(oneTrust.getConsentStatusForGroupId("C0005")).thenReturn(-1) // unknown
    }

    private fun provider(categories: List<String> = listOf("C0002", "C0004", "C0005")) =
        OneTrustConsentProvider(application, oneTrust, categories)

    private fun sendConsentBroadcast(categoryId: String, status: Int) {
        application.sendBroadcast(
            Intent(categoryId).putExtra(OTBroadcastServiceKeys.EVENT_STATUS, status)
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun readsInitialStatusesFromOneTrust() {
        assertThat(provider().consentStatuses)
            .isEqualTo(mapOf("C0002" to true, "C0004" to false, "C0005" to false))
    }

    @Test
    fun emptyCategoriesThrows() {
        try {
            provider(emptyList())
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }

    @Test
    fun broadcastUpdatesStatusAndNotifiesListener() {
        val provider = provider()
        val changes = mutableListOf<Map<String, Boolean>>()
        provider.setConsentChangeListener(
            ConsentCategoryProvider.ConsentChangeListener { statuses -> changes.add(statuses) }
        )

        sendConsentBroadcast("C0004", 1)

        assertThat(changes).hasSize(1)
        assertThat(changes.first())
            .isEqualTo(mapOf("C0002" to true, "C0004" to true, "C0005" to false))
        assertThat(provider.consentStatuses["C0004"]).isEqualTo(true)
    }

    @Test
    fun unknownStatusBroadcastMapsToDenied() {
        val provider = provider()
        val changes = mutableListOf<Map<String, Boolean>>()
        provider.setConsentChangeListener(
            ConsentCategoryProvider.ConsentChangeListener { statuses -> changes.add(statuses) }
        )

        sendConsentBroadcast("C0002", -1)

        assertThat(changes).hasSize(1)
        assertThat(provider.consentStatuses["C0002"]).isEqualTo(false)
    }

    @Test
    fun unchangedStatusBroadcastDoesNotNotify() {
        val provider = provider()
        val changes = mutableListOf<Map<String, Boolean>>()
        provider.setConsentChangeListener(
            ConsentCategoryProvider.ConsentChangeListener { statuses -> changes.add(statuses) }
        )

        sendConsentBroadcast("C0002", 1) // already granted
        sendConsentBroadcast("C0004", 0) // already denied

        assertThat(changes).isEmpty()
    }

    @Test
    fun broadcastForUntrackedCategoryIsIgnored() {
        val provider = provider(listOf("C0002"))
        val changes = mutableListOf<Map<String, Boolean>>()
        provider.setConsentChangeListener(
            ConsentCategoryProvider.ConsentChangeListener { statuses -> changes.add(statuses) }
        )

        sendConsentBroadcast("C9999", 1)

        assertThat(changes).isEmpty()
        assertThat(provider.consentStatuses).isEqualTo(mapOf("C0002" to true))
    }

    @Test
    fun shutdownUnregistersReceiversAndClearsListener() {
        val provider = provider()
        val changes = mutableListOf<Map<String, Boolean>>()
        provider.setConsentChangeListener(
            ConsentCategoryProvider.ConsentChangeListener { statuses -> changes.add(statuses) }
        )

        provider.shutdown()
        sendConsentBroadcast("C0004", 1)

        assertThat(changes).isEmpty()
        // Status snapshot is unchanged after shutdown.
        assertThat(provider.consentStatuses["C0004"]).isEqualTo(false)
    }
}

package com.example.kotlin_sample

import com.hightouch.analytics.ConsentCategoryProvider

/**
 * An in-memory [ConsentCategoryProvider] used to demo consent stamping/gating without a real
 * CMP. Toggle categories from the UI via [setCategory].
 */
object FakeConsentProvider : ConsentCategoryProvider {

    const val CATEGORY_ANALYTICS = "C0002"
    const val CATEGORY_ADVERTISING = "C0004"

    private val statuses = linkedMapOf(
        CATEGORY_ANALYTICS to false,
        CATEGORY_ADVERTISING to false
    )
    private var listener: ConsentCategoryProvider.ConsentChangeListener? = null

    @Synchronized
    override fun getConsentStatuses(): Map<String, Boolean> = LinkedHashMap(statuses)

    override fun setConsentChangeListener(
        listener: ConsentCategoryProvider.ConsentChangeListener?
    ) {
        this.listener = listener
    }

    override fun shutdown() {
        listener = null
    }

    @Synchronized
    fun isGranted(category: String): Boolean = statuses[category] == true

    fun setCategory(category: String, granted: Boolean) {
        val snapshot: Map<String, Boolean>
        synchronized(this) {
            if (statuses[category] == granted) return
            statuses[category] = granted
            snapshot = LinkedHashMap(statuses)
        }
        listener?.onConsentChange(snapshot)
    }
}

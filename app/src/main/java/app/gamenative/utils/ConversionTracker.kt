package app.gamenative.utils

import app.gamenative.PrefManager
import java.util.UUID

object ConversionTracker {

    fun featuredConversion(campaignId: String, actionType: String, appId: Int?, source: String) {
        val properties = mutableMapOf<String, Any>(
            "campaign_id" to campaignId,
            "action_type" to actionType,
            "source" to source,
        )
        appId?.let { properties["app_id"] = it }
    }
}

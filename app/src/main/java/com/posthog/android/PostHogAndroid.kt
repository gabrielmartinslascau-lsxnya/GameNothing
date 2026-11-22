package com.posthog.android

import android.content.Context
import com.posthog.PersonProfiles
import com.posthog.PostHogPropertiesSanitizer

/**
 * Local zero-telemetry no-op replacement for PostHog Android SDK initialization.
 */
class PostHogAndroidConfig(
    val apiKey: String,
    val host: String = "https://us.i.posthog.com",
) {
    var personProfiles: PersonProfiles = PersonProfiles.NEVER
    var propertiesSanitizer: PostHogPropertiesSanitizer? = null
    var captureApplicationLifecycleEvents: Boolean = false
    var captureScreenViews: Boolean = false
    var preloadFeatureFlags: Boolean = false
    var sendFeatureFlagEvent: Boolean = false
}

object PostHogAndroid {
    @JvmStatic
    fun setup(context: Context, config: PostHogAndroidConfig) {
        // No-op: Zero-telemetry invariant
    }
}

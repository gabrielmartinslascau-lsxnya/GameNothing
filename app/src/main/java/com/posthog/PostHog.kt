package com.posthog

/**
 * Local zero-telemetry no-op replacement for PostHog.
 * Completely disables analytics network communication and SDK overhead.
 */
object PostHog {

    @JvmStatic
    @JvmOverloads
    fun capture(
        event: String,
        distinctId: String? = null,
        properties: Map<String, Any?>? = null,
        userProperties: Map<String, Any?>? = null,
        userPropertiesSetOnce: Map<String, Any?>? = null,
        groupProperties: Map<String, Any?>? = null,
    ) {
        // No-op: Zero-telemetry invariant
    }

    @JvmStatic
    fun register(key: String, value: Any) {
        // No-op
    }

    @JvmStatic
    fun unregister(key: String) {
        // No-op
    }

    @JvmStatic
    @JvmOverloads
    fun identify(
        distinctId: String,
        userProperties: Map<String, Any?>? = null,
        userPropertiesSetOnce: Map<String, Any?>? = null,
    ) {
        // No-op
    }

    @JvmStatic
    fun reset() {
        // No-op
    }

    @JvmStatic
    fun flush() {
        // No-op
    }

    @JvmStatic
    fun close() {
        // No-op
    }
}

enum class PersonProfiles {
    NEVER,
    ALWAYS,
    IDENTIFIED_ONLY,
}

fun interface PostHogPropertiesSanitizer {
    fun sanitize(properties: Map<String, Any>): Map<String, Any>
}

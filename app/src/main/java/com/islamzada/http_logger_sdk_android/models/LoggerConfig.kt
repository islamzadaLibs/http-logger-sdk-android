package com.islamzada.http_logger_sdk_android.models

/**
 * Configuration class for HTTP Logger behavior
 *
 * This class controls what information is logged and how detailed the logging should be.
 * Different configurations can be used for different environments (debug vs release).
 *
 * Example configurations:
 * ```kotlin
 * // Development - Full logging
 * val devConfig = LoggerConfig(
 *     isEnabled = true,
 *     logLevel = LogLevel.FULL,
 *     includeHeaders = true,
 *     includeRequestBody = true,
 *     includeResponseBody = true
 * )
 *
 * // Production - Minimal logging
 * val prodConfig = LoggerConfig(
 *     isEnabled = true,
 *     logLevel = LogLevel.BASIC,
 *     includeHeaders = false,
 *     includeRequestBody = false,
 *     includeResponseBody = false
 * )
 *
 * // Testing - Headers only
 * val testConfig = LoggerConfig(
 *     logLevel = LogLevel.HEADERS,
 *     maxResponseBodySize = 512 * 1024 // 512KB limit
 * )
 * ```
 */
data class LoggerConfig(
    /**
     * Master switch for the entire logging functionality
     * When false, the interceptor will pass through requests without any logging
     *
     * Default: true
     * Recommended: false for production, true for development
     */
    val isEnabled: Boolean = true,

    /**
     * Maximum size of response body to capture in bytes
     * Large response bodies will be truncated to this size to prevent memory issues
     *
     * Default: 1MB (1024 * 1024 bytes)
     * Range: 1KB to 10MB recommended
     *
     * Note: This only affects response body logging, not the actual network response
     */
    val maxResponseBodySize: Long = 1024 * 1024,

    /**
     * Whether to include HTTP headers in logs
     * Headers can contain sensitive information (tokens, cookies)
     * Only effective when logLevel is HEADERS or FULL
     *
     * Default: true
     * Security note: Be careful with sensitive headers in production
     */
    val includeHeaders: Boolean = true,

    /**
     * Whether to include request body in logs
     * Request bodies may contain sensitive data (passwords, personal info)
     * Only effective when logLevel is FULL
     *
     * Default: true
     * Security note: Disable for production if requests contain sensitive data
     */
    val includeRequestBody: Boolean = true,

    /**
     * Whether to include response body in logs
     * Response bodies can be large and may contain sensitive information
     * Only effective when logLevel is FULL
     *
     * Default: true
     * Performance note: Large responses can impact logging performance
     */
    val includeResponseBody: Boolean = true,

    /**
     * Level of detail to include in logs
     * Controls the overall verbosity and what information is captured
     *
     * Default: LogLevel.FULL
     * See LogLevel enum for available options
     */
    val logLevel: LogLevel = LogLevel.FULL
) {

    companion object {

        /**
         * Creates a configuration optimized for development/debug builds
         * Includes all available information for comprehensive debugging
         *
         * @return LoggerConfig with full logging enabled
         */
        @JvmStatic
        fun forDevelopment(): LoggerConfig = LoggerConfig(
            isEnabled = true,
            logLevel = LogLevel.FULL,
            includeHeaders = true,
            includeRequestBody = true,
            includeResponseBody = true,
            maxResponseBodySize = 2 * 1024 * 1024 // 2MB for dev
        )

        /**
         * Creates a configuration optimized for production builds
         * Logs only essential information to minimize performance impact
         *
         * @return LoggerConfig with minimal logging for production
         */
        @JvmStatic
        fun forProduction(): LoggerConfig = LoggerConfig(
            isEnabled = true,
            logLevel = LogLevel.BASIC,
            includeHeaders = false,
            includeRequestBody = false,
            includeResponseBody = false,
            maxResponseBodySize = 256 * 1024 // 256KB limit
        )

        /**
         * Creates a configuration for testing scenarios
         * Includes headers but not bodies to balance detail with performance
         *
         * @return LoggerConfig suitable for testing
         */
        @JvmStatic
        fun forTesting(): LoggerConfig = LoggerConfig(
            isEnabled = true,
            logLevel = LogLevel.HEADERS,
            includeHeaders = true,
            includeRequestBody = false,
            includeResponseBody = false,
            maxResponseBodySize = 512 * 1024 // 512KB
        )

        /**
         * Creates a disabled configuration
         * Useful for completely turning off logging without code changes
         *
         * @return LoggerConfig with logging disabled
         */
        @JvmStatic
        fun disabled(): LoggerConfig = LoggerConfig(
            isEnabled = false,
            logLevel = LogLevel.NONE
        )
    }

    /**
     * Validates the configuration and returns any issues found
     *
     * @return List of validation messages (empty if valid)
     */
    fun validate(): List<String> {
        val issues = mutableListOf<String>()

        if (maxResponseBodySize < 1024) {
            issues.add("maxResponseBodySize should be at least 1KB (1024 bytes)")
        }

        if (maxResponseBodySize > 50 * 1024 * 1024) {
            issues.add("maxResponseBodySize should not exceed 50MB to prevent memory issues")
        }

        if (logLevel == LogLevel.NONE && isEnabled) {
            issues.add("isEnabled is true but logLevel is NONE - no logs will be created")
        }

        if ((includeRequestBody || includeResponseBody) && logLevel != LogLevel.FULL) {
            issues.add("Body logging is enabled but logLevel is not FULL - bodies will not be logged")
        }

        if (includeHeaders && logLevel < LogLevel.HEADERS) {
            issues.add("Header logging is enabled but logLevel is below HEADERS - headers will not be logged")
        }

        return issues
    }

    /**
     * Checks if the configuration is valid for production use
     * Production configurations should avoid logging sensitive data
     *
     * @return true if configuration is production-safe
     */
    fun isProductionSafe(): Boolean {
        return when {
            !isEnabled -> true
            logLevel == LogLevel.NONE -> true
            logLevel == LogLevel.BASIC -> true
            logLevel == LogLevel.HEADERS && !includeRequestBody && !includeResponseBody -> true
            else -> false
        }
    }

    /**
     * Returns estimated memory usage per log entry in bytes
     * Useful for capacity planning
     *
     * @return Estimated bytes per log entry
     */
    fun estimatedMemoryPerLog(): Long {
        var estimate = 1024L // Base overhead (timestamps, device info, etc.)

        if (includeHeaders && logLevel >= LogLevel.HEADERS) {
            estimate += 2048 // Estimated header size
        }

        if (includeRequestBody && logLevel == LogLevel.FULL) {
            estimate += 4096 // Estimated request body size
        }

        if (includeResponseBody && logLevel == LogLevel.FULL) {
            estimate += maxResponseBodySize
        }

        return estimate
    }
}
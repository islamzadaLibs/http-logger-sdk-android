package com.islamzada.http_logger_sdk_android.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Enhanced data model representing an HTTP request/response log entry
 *
 * This model captures comprehensive information about HTTP calls including:
 * - Request/response details (method, URL, headers, body)
 * - Application context (package, version, build type)
 * - Network information (type, connection quality)
 * - Performance metrics (timing, sizes)
 * - Session tracking and error categorization
 * - Device and environment context
 */
data class HttpLogModel(
    /**
     * Unique identifier for this log entry
     * Generated using UUID.randomUUID().toString()
     */
    @PropertyName("id")
    val id: String = "",

    /**
     * Timestamp when the request was made
     * Uses Firebase Timestamp for consistent server-side ordering
     */
    @PropertyName("timestamp")
    val timestamp: Timestamp = Timestamp.now(),

    // === REQUEST/RESPONSE CORE DATA ===

    /**
     * HTTP method (GET, POST, PUT, DELETE, etc.)
     */
    @PropertyName("method")
    val method: String = "",

    /**
     * Complete request URL including query parameters
     */
    @PropertyName("url")
    val url: String = "",

    /**
     * Request headers as key-value pairs
     * Only included if LogLevel.HEADERS or higher is configured
     */
    @PropertyName("requestHeaders")
    val requestHeaders: Map<String, String> = emptyMap(),

    /**
     * Request body content as string
     * Only included if LogLevel.FULL is configured and includeRequestBody is true
     */
    @PropertyName("requestBody")
    val requestBody: String? = null,

    /**
     * HTTP response status code
     * -1 indicates a network error occurred before receiving response
     */
    @PropertyName("responseCode")
    val responseCode: Int = 0,

    /**
     * Response headers as key-value pairs
     * Only included if LogLevel.HEADERS or higher is configured
     */
    @PropertyName("responseHeaders")
    val responseHeaders: Map<String, String> = emptyMap(),

    /**
     * Response body content as string
     * Only included if LogLevel.FULL is configured and includeResponseBody is true
     */
    @PropertyName("responseBody")
    val responseBody: String? = null,

    // === APPLICATION CONTEXT ===

    /**
     * Application package name (e.g., com.example.myapp)
     */
    @PropertyName("packageName")
    val packageName: String = "",

    /**
     * Application display name
     */
    @PropertyName("appName")
    val appName: String = "",

    /**
     * Application version name (e.g., "1.2.3")
     */
    @PropertyName("appVersion")
    val appVersion: String = "",

    /**
     * Application version code (numeric)
     */
    @PropertyName("appVersionCode")
    val appVersionCode: Int = 0,

    /**
     * Build type: debug, release, staging, etc.
     */
    @PropertyName("buildType")
    val buildType: String = "",

    // === NETWORK CONTEXT ===

    /**
     * Network type: WiFi, Mobile, Ethernet, Unknown
     */
    @PropertyName("networkType")
    val networkType: String = "Unknown",

    /**
     * Network operator name (for mobile networks)
     */
    @PropertyName("networkOperator")
    val networkOperator: String? = null,

    /**
     * Connection quality: Excellent, Good, Fair, Poor, Unknown
     */
    @PropertyName("connectionQuality")
    val connectionQuality: String = "Unknown",

    // === PERFORMANCE METRICS ===

    /**
     * Request duration in milliseconds
     * Measured from request start to response completion (or error)
     */
    @PropertyName("duration")
    val duration: Long = 0L,

    /**
     * Request body size in bytes
     */
    @PropertyName("requestSize")
    val requestSize: Long = 0L,

    /**
     * Response body size in bytes
     */
    @PropertyName("responseSize")
    val responseSize: Long = 0L,

    /**
     * DNS resolution time in milliseconds
     */
    @PropertyName("dnsTime")
    val dnsTime: Long = -1L,

    /**
     * Connection establishment time in milliseconds
     */
    @PropertyName("connectTime")
    val connectTime: Long = -1L,

    /**
     * SSL handshake time in milliseconds (if HTTPS)
     */
    @PropertyName("sslTime")
    val sslTime: Long = -1L,

    // === SESSION AND TRACKING ===

    /**
     * Session identifier for grouping related requests
     */
    @PropertyName("sessionId")
    val sessionId: String = "",

    /**
     * Sequential number of this request within the session
     */
    @PropertyName("requestSequence")
    val requestSequence: Int = 0,

    /**
     * User identifier (optional, for user-specific debugging)
     */
    @PropertyName("userId")
    val userId: String? = null,

    // === ERROR HANDLING ===

    /**
     * Error category: Network, Timeout, SSL, HTTP, Parse, Unknown
     */
    @PropertyName("errorCategory")
    val errorCategory: String? = null,

    /**
     * Number of retry attempts made for this request
     */
    @PropertyName("retryCount")
    val retryCount: Int = 0,

    /**
     * Stack trace for debugging (only in debug builds)
     */
    @PropertyName("stackTrace")
    val stackTrace: String? = null,

    // === DEVICE AND ENVIRONMENT ===

    /**
     * Device information in format: "Brand Model (Android Version)"
     * Example: "Samsung SM-G973F (Android 11)"
     */
    @PropertyName("deviceInfo")
    val deviceInfo: String = "",

    /**
     * Environment tag: development, staging, production
     */
    @PropertyName("environment")
    val environment: String = "production",

    /**
     * SDK version used for logging
     */
    @PropertyName("sdkVersion")
    val sdkVersion: String = "",

    // === SECURITY AND COMPLIANCE ===

    /**
     * Whether this request contains sensitive data
     */
    @PropertyName("containsSensitiveData")
    val containsSensitiveData: Boolean = false,

    /**
     * Data retention days for this log entry
     */
    @PropertyName("retentionDays")
    val retentionDays: Int = 30,

    // === ADDITIONAL METADATA ===

    /**
     * Custom tags for categorization and filtering
     */
    @PropertyName("tags")
    val tags: List<String> = emptyList(),

    /**
     * Additional custom properties
     */
    @PropertyName("customProperties")
    val customProperties: Map<String, String> = emptyMap()

) {

    // === COMPUTED PROPERTIES ===

    /**
     * Checks if this log represents a successful HTTP response
     */
    fun isSuccessful(): Boolean = responseCode in 200..299

    /**
     * Checks if this log represents a network error
     */
    fun isNetworkError(): Boolean = responseCode == -1

    /**
     * Checks if this is an HTTPS request
     */
    fun isSecure(): Boolean = url.startsWith("https://")

    /**
     * Gets the domain from URL
     */
    fun getDomain(): String = try {
        java.net.URL(url).host
    } catch (e: Exception) {
        "unknown"
    }

    /**
     * Gets a human-readable status description
     */
    fun getStatusDescription(): String = when {
        isNetworkError() -> "Network Error"
        isSuccessful() -> "Success"
        responseCode in 300..399 -> "Redirection"
        responseCode in 400..499 -> "Client Error"
        responseCode in 500..599 -> "Server Error"
        else -> "Unknown"
    }

    /**
     * Gets error category based on response code and context
     */
    fun getComputedErrorCategory(): String = when {
        isNetworkError() -> errorCategory ?: "Network"
        responseCode in 400..499 -> "HTTP_CLIENT"
        responseCode in 500..599 -> "HTTP_SERVER"
        responseCode == 0 -> "Timeout"
        else -> errorCategory ?: "Unknown"
    }

    /**
     * Calculates total request overhead (headers + metadata)
     */
    fun getTotalOverhead(): Long {
        val headerSize = requestHeaders.entries.sumOf {
            it.key.length + it.value.length + 4 // ": \r\n"
        }.toLong()
        return headerSize + method.length + url.length
    }

    /**
     * Checks if request took longer than expected
     */
    fun isSlowRequest(thresholdMs: Long = 5000): Boolean = duration > thresholdMs

    /**
     * Gets network efficiency score (0-100)
     * Based on response time and payload size ratio
     */
    fun getNetworkEfficiencyScore(): Int {
        if (duration <= 0 || responseSize <= 0) return 0

        val bytesPerMs = responseSize.toDouble() / duration
        return when {
            bytesPerMs > 1000 -> 100  // Excellent
            bytesPerMs > 500 -> 80    // Good
            bytesPerMs > 100 -> 60    // Fair
            bytesPerMs > 50 -> 40     // Poor
            else -> 20                // Very Poor
        }.coerceIn(0, 100)
    }

    /**
     * Validates if all required fields are present
     */
    fun validate(): List<String> {
        val issues = mutableListOf<String>()

        if (id.isBlank()) issues.add("ID cannot be blank")
        if (method.isBlank()) issues.add("Method cannot be blank")
        if (url.isBlank()) issues.add("URL cannot be blank")
        if (packageName.isBlank()) issues.add("Package name cannot be blank")
        if (sessionId.isBlank()) issues.add("Session ID cannot be blank")
        if (duration < 0) issues.add("Duration cannot be negative")

        return issues
    }

    /**
     * Creates a summary string for logging/debugging
     */
    fun toSummaryString(): String {
        return "$method ${getDomain()} -> $responseCode (${duration}ms)"
    }

    /**
     * Checks if this log should be retained based on current date
     */
    fun shouldRetain(): Boolean {
        val now = System.currentTimeMillis()
        val logTime = timestamp.seconds * 1000
        val retentionMs = retentionDays * 24 * 60 * 60 * 1000L
        return (now - logTime) < retentionMs
    }
}
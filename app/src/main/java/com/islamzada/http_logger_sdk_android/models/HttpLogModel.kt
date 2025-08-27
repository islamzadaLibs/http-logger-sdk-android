package com.islamzada.http_logger_sdk_android.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Data model representing an HTTP request/response log entry
 *
 * This model captures all relevant information about HTTP calls including:
 * - Request details (method, URL, headers, body)
 * - Response details (status code, headers, body)
 * - Timing information and device context
 *
 * All properties are annotated with @PropertyName for proper Firestore serialization
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
     * Null if request has no body or body extraction fails
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
     * For network errors, contains error description
     * Limited by maxResponseBodySize configuration
     */
    @PropertyName("responseBody")
    val responseBody: String? = null,

    /**
     * Request duration in milliseconds
     * Measured from request start to response completion (or error)
     */
    @PropertyName("duration")
    val duration: Long = 0L,

    /**
     * Device information in format: "Brand Model (Android Version)"
     * Example: "Samsung SM-G973F (Android 11)"
     * Useful for debugging device-specific issues
     */
    @PropertyName("deviceInfo")
    val deviceInfo: String = ""
) {
    /**
     * Checks if this log represents a successful HTTP response
     * @return true if response code is in 200-299 range
     */
    fun isSuccessful(): Boolean = responseCode in 200..299

    /**
     * Checks if this log represents a network error
     * @return true if response code is -1 (network error)
     */
    fun isNetworkError(): Boolean = responseCode == -1

    /**
     * Gets a human-readable status description
     * @return Status description based on response code
     */
    fun getStatusDescription(): String = when {
        isNetworkError() -> "Network Error"
        isSuccessful() -> "Success"
        responseCode in 300..399 -> "Redirection"
        responseCode in 400..499 -> "Client Error"
        responseCode in 500..599 -> "Server Error"
        else -> "Unknown"
    }
}
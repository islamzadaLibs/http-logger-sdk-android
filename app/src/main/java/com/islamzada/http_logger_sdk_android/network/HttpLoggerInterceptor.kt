package com.islamzada.http_logger_sdk_android.network

import android.content.Context
import android.util.Log
import com.islamzada.http_logger_sdk_android.core.FirebaseManager
import com.islamzada.http_logger_sdk_android.data.FirebaseLogRepository
import com.islamzada.http_logger_sdk_android.models.FirebaseConfig
import com.islamzada.http_logger_sdk_android.models.HttpLogModel
import com.islamzada.http_logger_sdk_android.models.LogLevel
import com.islamzada.http_logger_sdk_android.models.LoggerConfig
import com.islamzada.http_logger_sdk_android.util.ApplicationContextProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http2.ConnectionShutdownException
import okio.Buffer
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException

/**
 * Enhanced OkHttp interceptor that captures comprehensive HTTP request/response information
 *
 * This interceptor provides detailed logging capabilities including:
 * - Complete request/response data with configurable detail levels
 * - Application context (package, version, build type)
 * - Network information (type, quality, operator)
 * - Performance metrics (timing breakdown, sizes)
 * - Session tracking and error categorization
 * - Device and environment context
 */
class HttpLoggerInterceptor internal constructor(
    private val context: Context,
    private val firebaseConfig: FirebaseConfig,
    private val apiKey: String,
    private val config: LoggerConfig,
    private val environment: String = "production",
    private val sdkVersion: String = "1.0.0",
    private val userId: String? = null
) : Interceptor {

    companion object {
        private const val TAG = "HttpLoggerInterceptor"

        /**
         * Media types that are considered readable as text
         */
        private val READABLE_MEDIA_TYPES = setOf(
            "application/json",
            "application/xml",
            "text/plain",
            "text/html",
            "text/xml",
            "application/x-www-form-urlencoded",
            "application/graphql"
        )

        /**
         * Headers that should be filtered for security
         */
        private val SENSITIVE_HEADERS = setOf(
            "authorization",
            "cookie",
            "x-api-key",
            "x-auth-token",
            "bearer",
            "x-access-token",
            "x-csrf-token"
        )
    }

    /**
     * Application context provider for extracting app and system information
     */
    private val contextProvider = ApplicationContextProvider(context)

    /**
     * Lazy-initialized repository for Firebase logging operations
     */
    private val repository by lazy {
        FirebaseLogRepository(FirebaseManager.getFirestoreInstance(firebaseConfig))
    }

    /**
     * Cached application information
     */
    private val appInfo by lazy { contextProvider.getAppInfo() }

    /**
     * Cached device information
     */
    private val deviceInfo by lazy { contextProvider.getDeviceInfo() }

    /**
     * Coroutine scope for asynchronous logging operations
     */
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Retry counter for tracking request attempts
     */
    private val retryCounter = AtomicInteger(0)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!config.isEnabled || config.logLevel == LogLevel.NONE) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()
        val requestSequence = contextProvider.getNextRequestSequence()

        // Track timing breakdown
        val timingBreakdown = mutableMapOf<String, Long>()
        timingBreakdown["start"] = startTime

        return try {
            val response = chain.proceed(request)
            val endTime = System.currentTimeMillis()
            timingBreakdown["end"] = endTime

            val duration = endTime - startTime
            logHttpCall(request, response, duration, requestSequence, timingBreakdown)
            response

        } catch (exception: Exception) {
            val endTime = System.currentTimeMillis()
            timingBreakdown["error"] = endTime

            val duration = endTime - startTime
            logError(request, exception, duration, requestSequence, timingBreakdown)
            throw exception
        }
    }

    /**
     * Logs a successful HTTP call with comprehensive information
     */
    private fun logHttpCall(
        request: Request,
        response: Response,
        duration: Long,
        requestSequence: Int,
        timingBreakdown: Map<String, Long>
    ) {
        coroutineScope.launch {
            try {
                val networkInfo = contextProvider.getNetworkInfo()
                val sessionId = contextProvider.getSessionId()

                val httpLog = HttpLogModel(
                    id = UUID.randomUUID().toString(),
                    method = request.method,
                    url = request.url.toString(),

                    // Request data
                    requestHeaders = if (shouldIncludeHeaders()) {
                        extractHeaders(request.headers.toMultimap(), filterSensitive = true)
                    } else emptyMap(),
                    requestBody = if (shouldIncludeRequestBody()) {
                        extractRequestBody(request)
                    } else null,

                    // Response data
                    responseCode = response.code,
                    responseHeaders = if (shouldIncludeHeaders()) {
                        extractHeaders(response.headers.toMultimap(), filterSensitive = true)
                    } else emptyMap(),
                    responseBody = if (shouldIncludeResponseBody()) {
                        extractResponseBody(response)
                    } else null,

                    // Application context
                    packageName = appInfo.packageName,
                    appName = appInfo.appName,
                    appVersion = appInfo.appVersion,
                    appVersionCode = appInfo.appVersionCode,
                    buildType = appInfo.buildType,

                    // Network context
                    networkType = networkInfo.networkType,
                    networkOperator = networkInfo.networkOperator,
                    connectionQuality = networkInfo.connectionQuality,

                    // Performance metrics
                    duration = duration,
                    requestSize = calculateRequestSize(request),
                    responseSize = calculateResponseSize(response),
                    dnsTime = extractTimingMetric(timingBreakdown, "dns"),
                    connectTime = extractTimingMetric(timingBreakdown, "connect"),
                    sslTime = if (request.isHttps) extractTimingMetric(timingBreakdown, "ssl") else -1L,

                    // Session and tracking
                    sessionId = sessionId,
                    requestSequence = requestSequence,
                    userId = userId,

                    // Error handling
                    errorCategory = if (!response.isSuccessful) categorizeHttpError(response.code) else null,
                    retryCount = retryCounter.get(),
                    stackTrace = null, // Only populated for errors in debug mode

                    // Device and environment
                    deviceInfo = deviceInfo,
                    environment = environment,
                    sdkVersion = sdkVersion,

                    // Security and compliance
                    containsSensitiveData = detectSensitiveData(request, response),
                    retentionDays = getRetentionDays(),

                    // Additional metadata
                    tags = generateTags(request, response),
                    customProperties = extractCustomProperties(request)
                )

                repository.saveLog(httpLog, apiKey)

            } catch (exception: Exception) {
                Log.e(TAG, "Failed to log HTTP call: ${exception.message}", exception)
            }
        }
    }

    /**
     * Logs a failed HTTP call (network error) with comprehensive error information
     */
    private fun logError(
        request: Request,
        exception: Exception,
        duration: Long,
        requestSequence: Int,
        timingBreakdown: Map<String, Long>
    ) {
        coroutineScope.launch {
            try {
                val networkInfo = contextProvider.getNetworkInfo()
                val sessionId = contextProvider.getSessionId()
                val errorCategory = categorizeNetworkError(exception)
                val currentRetryCount = retryCounter.incrementAndGet()

                val httpLog = HttpLogModel(
                    id = UUID.randomUUID().toString(),
                    method = request.method,
                    url = request.url.toString(),

                    // Request data
                    requestHeaders = if (shouldIncludeHeaders()) {
                        extractHeaders(request.headers.toMultimap(), filterSensitive = true)
                    } else emptyMap(),
                    requestBody = if (shouldIncludeRequestBody()) {
                        extractRequestBody(request)
                    } else null,

                    // Error response
                    responseCode = -1,
                    responseHeaders = emptyMap(),
                    responseBody = createErrorMessage(exception),

                    // Application context
                    packageName = appInfo.packageName,
                    appName = appInfo.appName,
                    appVersion = appInfo.appVersion,
                    appVersionCode = appInfo.appVersionCode,
                    buildType = appInfo.buildType,

                    // Network context
                    networkType = networkInfo.networkType,
                    networkOperator = networkInfo.networkOperator,
                    connectionQuality = networkInfo.connectionQuality,

                    // Performance metrics
                    duration = duration,
                    requestSize = calculateRequestSize(request),
                    responseSize = 0L,
                    dnsTime = extractTimingMetric(timingBreakdown, "dns"),
                    connectTime = extractTimingMetric(timingBreakdown, "connect"),
                    sslTime = if (request.isHttps) extractTimingMetric(timingBreakdown, "ssl") else -1L,

                    // Session and tracking
                    sessionId = sessionId,
                    requestSequence = requestSequence,
                    userId = userId,

                    // Error handling
                    errorCategory = errorCategory,
                    retryCount = currentRetryCount,
                    stackTrace = if (appInfo.buildType == "debug") exception.stackTraceToString() else null,

                    // Device and environment
                    deviceInfo = deviceInfo,
                    environment = environment,
                    sdkVersion = sdkVersion,

                    // Security and compliance
                    containsSensitiveData = detectSensitiveData(request, null),
                    retentionDays = getRetentionDays(),

                    // Additional metadata
                    tags = generateErrorTags(request, exception),
                    customProperties = extractCustomProperties(request)
                )

                repository.saveLog(httpLog, apiKey)

            } catch (loggingException: Exception) {
                Log.e(TAG, "Failed to log HTTP error: ${loggingException.message}", loggingException)
            }
        }
    }

    // === REQUEST/RESPONSE EXTRACTION METHODS ===

    /**
     * Extracts request body as string with proper encoding handling
     */
    private fun extractRequestBody(request: Request): String? = runCatching {
        request.body?.let { body ->
            if (body.contentLength() == 0L) return null

            val buffer = Buffer()
            body.writeTo(buffer)

            val contentType = body.contentType()
            val charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8

            if (isReadableMediaType(contentType?.toString())) {
                buffer.readString(charset).takeIf { it.isNotBlank() }
            } else {
                "[Binary content: ${body.contentType()}]"
            }
        }
    }.getOrElse { exception ->
        Log.w(TAG, "Failed to extract request body: ${exception.message}")
        "[Body extraction failed: ${exception.message}]"
    }

    /**
     * Extracts response body with size limiting and encoding handling
     */
    private fun extractResponseBody(response: Response): String? = runCatching {
        val contentType = response.body?.contentType()

        if (!isReadableMediaType(contentType?.toString())) {
            return "[Binary content: $contentType]"
        }

        response.peekBody(config.maxResponseBodySize)
            .string()
            .takeIf { it.isNotBlank() }
    }.getOrElse { exception ->
        Log.w(TAG, "Failed to extract response body: ${exception.message}")
        "[Body extraction failed: ${exception.message}]"
    }

    /**
     * Extracts headers with optional sensitive data filtering
     */
    private fun extractHeaders(
        headerMap: Map<String, List<String>>,
        filterSensitive: Boolean = true
    ): Map<String, String> {
        return headerMap.mapValues { entry ->
            entry.value.joinToString(", ")
        }.let { headers ->
            if (filterSensitive) {
                headers.filterKeys { key ->
                    !SENSITIVE_HEADERS.contains(key.lowercase())
                }.mapValues { entry ->
                    if (entry.key.lowercase().contains("auth") ||
                        entry.key.lowercase().contains("token") ||
                        entry.key.lowercase().contains("key")) {
                        "[FILTERED]"
                    } else {
                        entry.value
                    }
                }
            } else {
                headers
            }
        }
    }

    // === SIZE CALCULATION METHODS ===

    /**
     * Calculates total request size including headers and body
     */
    private fun calculateRequestSize(request: Request): Long {
        var size = request.method.length.toLong()
        size += request.url.toString().length

        // Add headers size
        request.headers.forEach { header ->
            size += header.first.length + header.second.length + 4 // ": \r\n"
        }

        // Add body size
        request.body?.contentLength()?.let { contentLength ->
            if (contentLength >= 0) size += contentLength
        }

        return size
    }

    /**
     * Calculates response size including headers and body
     */
    private fun calculateResponseSize(response: Response): Long {
        var size = 0L

        // Add headers size
        response.headers.forEach { header ->
            size += header.first.length + header.second.length + 4
        }

        // Add body size
        response.body?.contentLength()?.let { contentLength ->
            if (contentLength >= 0) size += contentLength
        }

        return size
    }

    // === ERROR CATEGORIZATION METHODS ===

    /**
     * Categorizes HTTP errors based on status code
     */
    private fun categorizeHttpError(statusCode: Int): String {
        return when (statusCode) {
            in 400..401 -> "Authentication"
            in 402..403 -> "Authorization"
            404 -> "NotFound"
            in 405..499 -> "ClientError"
            500 -> "ServerError"
            502 -> "BadGateway"
            503 -> "ServiceUnavailable"
            504 -> "Timeout"
            in 505..599 -> "ServerError"
            else -> "HTTP_${statusCode}"
        }
    }

    /**
     * Categorizes network errors based on exception type
     */
    private fun categorizeNetworkError(exception: Exception): String {
        return when (exception) {
            is UnknownHostException -> "DNS"
            is ConnectException -> "Connection"
            is SocketTimeoutException -> "Timeout"
            is SSLException -> "SSL"
            is ConnectionShutdownException -> "ConnectionShutdown"
            is IOException -> "IO"
            else -> "Network"
        }
    }

    // === UTILITY METHODS ===

    /**
     * Creates formatted error message from exception
     */
    private fun createErrorMessage(exception: Exception): String {
        val errorType = exception::class.simpleName ?: "Unknown"
        val message = exception.message ?: "No message available"
        return "[$errorType] $message"
    }

    /**
     * Extracts timing metrics from breakdown map
     */
    private fun extractTimingMetric(timingMap: Map<String, Long>, metric: String): Long {
        return timingMap[metric] ?: -1L
    }

    /**
     * Checks if media type should be logged as readable text
     */
    private fun isReadableMediaType(mediaType: String?): Boolean {
        if (mediaType == null) return true

        val mainType = mediaType.lowercase().substringBefore(';')
        return READABLE_MEDIA_TYPES.any { readable ->
            mainType.startsWith(readable)
        } || mainType.startsWith("text/")
    }

    /**
     * Detects if request/response contains sensitive data
     */
    private fun detectSensitiveData(request: Request, response: Response?): Boolean {
        // Check URL for sensitive patterns
        val url = request.url.toString().lowercase()
        if (url.contains("password") || url.contains("secret") || url.contains("token")) {
            return true
        }

        // Check headers for sensitive data indicators
        request.headers.names().forEach { headerName ->
            if (SENSITIVE_HEADERS.contains(headerName.lowercase())) {
                return true
            }
        }

        return false
    }

    /**
     * Generates tags for categorizing and filtering logs
     */
    private fun generateTags(request: Request, response: Response): List<String> {
        val tags = mutableListOf<String>()

        // Add method tag
        tags.add("method_${request.method.lowercase()}")

        // Add status category tag
        when (response.code) {
            in 200..299 -> tags.add("success")
            in 300..399 -> tags.add("redirect")
            in 400..499 -> tags.add("client_error")
            in 500..599 -> tags.add("server_error")
        }

        // Add protocol tag
        if (request.isHttps) tags.add("https") else tags.add("http")

        // Add performance tags
        if (response.headers["cache-control"]?.contains("no-cache") == true) {
            tags.add("no_cache")
        }

        return tags
    }

    /**
     * Generates error-specific tags
     */
    private fun generateErrorTags(request: Request, exception: Exception): List<String> {
        val tags = mutableListOf<String>()

        tags.add("method_${request.method.lowercase()}")
        tags.add("error")
        tags.add("error_${exception::class.simpleName?.lowercase() ?: "unknown"}")

        if (request.isHttps) tags.add("https") else tags.add("http")

        return tags
    }

    /**
     * Extracts custom properties from request headers or URL
     */
    private fun extractCustomProperties(request: Request): Map<String, String> {
        val properties = mutableMapOf<String, String>()

        // Extract custom headers that might be useful
        request.headers["x-request-id"]?.let { properties["requestId"] = it }
        request.headers["x-correlation-id"]?.let { properties["correlationId"] = it }
        request.headers["user-agent"]?.let { properties["userAgent"] = it }

        // Extract query parameters that might be useful for analytics
        val url = request.url
        if (url.queryParameterNames.isNotEmpty()) {
            properties["hasQueryParams"] = "true"
            properties["queryParamCount"] = url.queryParameterNames.size.toString()
        }

        return properties
    }

    /**
     * Gets retention days based on environment and configuration
     */
    private fun getRetentionDays(): Int {
        return when {
            appInfo.buildType == "debug" -> 7  // Short retention for debug
            environment == "development" -> 30
            environment == "staging" -> 60
            else -> 90  // Production default
        }
    }

    // === CONFIGURATION CHECK METHODS ===

    private fun shouldIncludeHeaders(): Boolean =
        config.includeHeaders && config.logLevel >= LogLevel.HEADERS

    private fun shouldIncludeRequestBody(): Boolean =
        config.includeRequestBody && config.logLevel == LogLevel.FULL

    private fun shouldIncludeResponseBody(): Boolean =
        config.includeResponseBody && config.logLevel == LogLevel.FULL
}
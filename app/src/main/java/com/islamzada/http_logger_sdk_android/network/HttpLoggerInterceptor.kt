package com.islamzada.http_logger_sdk_android.network

import android.os.Build
import android.util.Log
import com.islamzada.http_logger_sdk_android.core.FirebaseManager
import com.islamzada.http_logger_sdk_android.data.FirebaseLogRepository
import com.islamzada.http_logger_sdk_android.models.FirebaseConfig
import com.islamzada.http_logger_sdk_android.models.HttpLogModel
import com.islamzada.http_logger_sdk_android.models.LogLevel
import com.islamzada.http_logger_sdk_android.models.LoggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * OkHttp interceptor that captures and logs HTTP requests/responses to Firebase Firestore
 *
 * This interceptor provides comprehensive HTTP logging capabilities with configurable
 * detail levels and automatic Firebase integration. It captures:
 * - Request/response headers and bodies
 * - Timing information
 * - Device information
 * - Error conditions
 *
 * Usage:
 * ```kotlin
 * val interceptor = HttpLoggerInterceptor(firebaseConfig, apiKey, loggerConfig)
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(interceptor)
 *     .build()
 * ```
 *
 * Features:
 * - Configurable logging levels (NONE, BASIC, HEADERS, FULL)
 * - Automatic error handling and logging
 * - Thread-safe asynchronous logging
 * - Memory-efficient body extraction with size limits
 * - Device information collection for debugging
 */
class HttpLoggerInterceptor internal constructor(
    private val firebaseConfig: FirebaseConfig,
    private val apiKey: String,
    private val config: LoggerConfig
) : Interceptor {

    companion object {
        private const val TAG = "HttpLoggerInterceptor"

        /**
         * Media types that are considered readable as text
         * Used to determine if response body should be logged as string
         */
        private val READABLE_MEDIA_TYPES = setOf(
            "application/json",
            "application/xml",
            "text/plain",
            "text/html",
            "text/xml",
            "application/x-www-form-urlencoded"
        )
    }

    /**
     * Lazy-initialized repository for Firebase logging operations
     * Created only when first log attempt is made
     */
    private val repository by lazy {
        FirebaseLogRepository(FirebaseManager.getFirestoreInstance(firebaseConfig))
    }

    /**
     * Cached device information string to avoid repeated system calls
     * Format: "Brand Model (Android Version)"
     */
    private val deviceInfo by lazy { extractDeviceInfo() }

    /**
     * Coroutine scope for asynchronous logging operations
     * Uses IO dispatcher for network operations and SupervisorJob for error isolation
     */
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Main intercept method that processes HTTP requests/responses
     *
     * This method:
     * 1. Checks if logging is enabled
     * 2. Measures request duration
     * 3. Handles both successful responses and exceptions
     * 4. Logs asynchronously without blocking the request
     *
     * @param chain OkHttp interceptor chain
     * @return Response from the network call
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!config.isEnabled || config.logLevel == LogLevel.NONE) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        return try {
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime
            logHttpCall(request, response, duration)
            response
        } catch (exception: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logError(request, exception, duration)
            throw exception
        }
    }

    /**
     * Logs a successful HTTP call asynchronously
     *
     * @param request Original HTTP request
     * @param response HTTP response received
     * @param duration Request duration in milliseconds
     */
    private fun logHttpCall(request: Request, response: Response, duration: Long) {
        coroutineScope.launch {
            try {
                val httpLog = createHttpLog(request, response, duration)
                repository.saveLog(httpLog, apiKey)
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to log HTTP call: ${exception.message}", exception)
            }
        }
    }

    /**
     * Logs a failed HTTP call (network error) asynchronously
     *
     * @param request Original HTTP request
     * @param exception Exception that occurred during request
     * @param duration Time elapsed before error occurred
     */
    private fun logError(request: Request, exception: Exception, duration: Long) {
        coroutineScope.launch {
            try {
                val httpLog = createErrorLog(request, exception, duration)
                repository.saveLog(httpLog, apiKey)
            } catch (loggingException: Exception) {
                Log.e(TAG, "Failed to log HTTP error: ${loggingException.message}", loggingException)
            }
        }
    }

    /**
     * Creates an HttpLogModel for successful HTTP calls
     *
     * Includes different levels of detail based on LoggerConfig:
     * - BASIC: Only URL, method, status code, and duration
     * - HEADERS: Adds request/response headers
     * - FULL: Adds request/response bodies
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param duration Request duration
     * @return Populated HttpLogModel
     */
    private fun createHttpLog(request: Request, response: Response, duration: Long): HttpLogModel =
        HttpLogModel(
            id = UUID.randomUUID().toString(),
            method = request.method,
            url = request.url.toString(),
            requestHeaders = if (shouldIncludeHeaders()) {
                extractHeaders(request.headers.toMultimap())
            } else emptyMap(),
            requestBody = if (shouldIncludeRequestBody()) {
                extractRequestBody(request)
            } else null,
            responseCode = response.code,
            responseHeaders = if (shouldIncludeHeaders()) {
                extractHeaders(response.headers.toMultimap())
            } else emptyMap(),
            responseBody = if (shouldIncludeResponseBody()) {
                extractResponseBody(response)
            } else null,
            duration = duration,
            deviceInfo = deviceInfo
        )

    /**
     * Creates an HttpLogModel for failed HTTP calls (network errors)
     *
     * @param request HTTP request that failed
     * @param exception Exception that occurred
     * @param duration Time elapsed before failure
     * @return HttpLogModel with error information
     */
    private fun createErrorLog(request: Request, exception: Exception, duration: Long): HttpLogModel =
        HttpLogModel(
            id = UUID.randomUUID().toString(),
            method = request.method,
            url = request.url.toString(),
            requestHeaders = if (shouldIncludeHeaders()) {
                extractHeaders(request.headers.toMultimap())
            } else emptyMap(),
            requestBody = if (shouldIncludeRequestBody()) {
                extractRequestBody(request)
            } else null,
            responseCode = -1,
            responseHeaders = emptyMap(),
            responseBody = createErrorMessage(exception),
            duration = duration,
            deviceInfo = deviceInfo
        )

    /**
     * Extracts request body as string if possible
     *
     * Safely handles:
     * - Empty bodies
     * - Non-text media types
     * - Encoding issues
     * - Buffer operations
     *
     * @param request HTTP request
     * @return Request body as string or null
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
     * Extracts response body as string with size limiting
     *
     * Uses peekBody to avoid consuming the response stream
     * and respects maxResponseBodySize configuration
     *
     * @param response HTTP response
     * @return Response body as string or null
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
     * Converts header multimap to simple string map
     * Joins multiple header values with commas
     *
     * @param headerMap Headers as multimap
     * @return Headers as simple key-value map
     */
    private fun extractHeaders(headerMap: Map<String, List<String>>): Map<String, String> =
        headerMap.mapValues { entry -> entry.value.joinToString(", ") }

    /**
     * Creates a formatted error message from exception
     *
     * @param exception Exception to format
     * @return Formatted error message
     */
    private fun createErrorMessage(exception: Exception): String =
        "Network Error: ${exception::class.simpleName} - ${exception.message ?: "Unknown error"}"

    /**
     * Extracts device information for debugging context
     *
     * @return Device info string in format "Brand Model (Android Version)"
     */
    private fun extractDeviceInfo(): String =
        "${Build.BRAND} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

    /**
     * Checks if current configuration should include headers
     */
    private fun shouldIncludeHeaders(): Boolean =
        config.includeHeaders && config.logLevel >= LogLevel.HEADERS

    /**
     * Checks if current configuration should include request body
     */
    private fun shouldIncludeRequestBody(): Boolean =
        config.includeRequestBody && config.logLevel == LogLevel.FULL

    /**
     * Checks if current configuration should include response body
     */
    private fun shouldIncludeResponseBody(): Boolean =
        config.includeResponseBody && config.logLevel == LogLevel.FULL

    /**
     * Determines if a media type should be logged as readable text
     *
     * @param mediaType Media type string (e.g., "application/json")
     * @return true if media type is considered readable
     */
    private fun isReadableMediaType(mediaType: String?): Boolean {
        if (mediaType == null) return true

        val mainType = mediaType.lowercase().substringBefore(';')
        return READABLE_MEDIA_TYPES.any { readable ->
            mainType.startsWith(readable)
        } || mainType.startsWith("text/")
    }
}
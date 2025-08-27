package com.islamzada.http_logger_sdk_android

import android.content.Context
import android.util.Log
import com.islamzada.http_logger_sdk_android.core.FirebaseManager
import com.islamzada.http_logger_sdk_android.models.FirebaseConfig
import com.islamzada.http_logger_sdk_android.models.LoggerConfig
import com.islamzada.http_logger_sdk_android.network.HttpLoggerInterceptor
import okhttp3.OkHttpClient

/**
 * Main SDK class for HTTP Logger Android SDK
 *
 * This is the primary entry point for integrating HTTP logging into Android applications.
 * The SDK automatically captures HTTP requests/responses and stores them in Firebase Firestore
 * for monitoring, debugging, and analytics purposes.
 *
 * ## Key Features:
 * - Automatic HTTP request/response logging
 * - Firebase Firestore integration for cloud storage
 * - Configurable logging levels and detail
 * - Real-time log observation capabilities
 * - Thread-safe operations
 * - Multiple API key support
 *
 * ## Basic Usage:
 * ```kotlin
 * // 1. Configure Firebase
 * val firebaseConfig = FirebaseConfig.create(
 *     projectId = "your-project-id",
 *     applicationId = "your-app-id",
 *     apiKey = "your-api-key"
 * )
 *
 * // 2. Create interceptor
 * val interceptor = HttpLoggerSDK.createInterceptor(
 *     context = this,
 *     firebaseConfig = firebaseConfig,
 *     apiKey = "unique-api-key-for-this-client"
 * )
 *
 * // 3. Add to OkHttp client
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(interceptor)
 *     .build()
 * ```
 *
 * ## Or use enhanced client builder:
 * ```kotlin
 * val client = HttpLoggerSDK.enhanceOkHttpClient(
 *     context = this,
 *     firebaseConfig = firebaseConfig,
 *     apiKey = "unique-api-key"
 * )
 * ```
 *
 * @since 1.0.0
 */
object HttpLoggerSDK {

    private const val TAG = "HttpLoggerSDK"
    private const val VERSION = "1.0.0"

    /**
     * Gets the current SDK version
     * @return SDK version string
     */
    @JvmStatic
    fun getVersion(): String = VERSION

    /**
     * Creates an HTTP logger interceptor with the specified configuration
     *
     * This interceptor will capture HTTP requests/responses and store them
     * in Firebase Firestore under the provided API key.
     *
     * @param context Android application context (required for Firebase initialization)
     * @param firebaseConfig Firebase project configuration
     * @param apiKey Unique API key to organize logs under (acts as namespace)
     * @param config Logger configuration controlling what data to capture
     * @return Configured HttpLoggerInterceptor ready to use with OkHttp
     *
     * @throws IllegalArgumentException if configuration is invalid
     * @throws IllegalStateException if Firebase initialization fails
     *
     * @see FirebaseConfig.create
     * @see LoggerConfig.forDevelopment
     * @see LoggerConfig.forProduction
     *
     * Example:
     * ```kotlin
     * val interceptor = HttpLoggerSDK.createInterceptor(
     *     context = applicationContext,
     *     firebaseConfig = FirebaseConfig.create(
     *         projectId = "my-project-123",
     *         applicationId = "1:123:android:abc",
     *         apiKey = "AIza..."
     *     ),
     *     apiKey = "mobile-app-v2",
     *     config = LoggerConfig.forDevelopment()
     * )
     * ```
     */
    @JvmStatic
    fun createInterceptor(
        context: Context,
        firebaseConfig: FirebaseConfig,
        apiKey: String,
        config: LoggerConfig = LoggerConfig()
    ): HttpLoggerInterceptor {

        require(firebaseConfig.isValid()) {
            "Invalid Firebase configuration. Ensure projectId, applicationId, and apiKey are provided."
        }
        require(apiKey.isNotBlank()) {
            "API Key cannot be blank. Provide a unique identifier for organizing logs."
        }

        val validationIssues = config.validate()
        if (validationIssues.isNotEmpty()) {
            Log.w(TAG, "Logger configuration issues detected: ${validationIssues.joinToString(", ")}")
        }

        try {
            FirebaseManager.initializeFirebase(context, firebaseConfig)

            Log.i(TAG, "HTTP Logger SDK initialized successfully")
            Log.d(TAG, "Firebase Project: ${firebaseConfig.projectId}")
            Log.d(TAG, "API Key: ${apiKey.take(8)}...")
            Log.d(TAG, "Log Level: ${config.logLevel}")

            return HttpLoggerInterceptor(firebaseConfig, apiKey, config)

        } catch (exception: Exception) {
            Log.e(TAG, "Failed to initialize HTTP Logger SDK", exception)
            throw IllegalStateException(
                "Failed to initialize Firebase for HTTP logging: ${exception.message}",
                exception
            )
        }
    }

    /**
     * Creates an enhanced OkHttp client with HTTP logging interceptor
     *
     * This is a convenience method that creates a new OkHttp client with
     * the HTTP logger interceptor already configured. You can provide your
     * own OkHttpClient.Builder to customize other aspects of the client.
     *
     * @param context Android application context
     * @param firebaseConfig Firebase project configuration
     * @param apiKey Unique API key for log organization
     * @param clientBuilder Optional OkHttpClient.Builder to customize (creates new if null)
     * @param config Logger configuration
     * @return Configured OkHttpClient with logging interceptor
     *
     * @throws IllegalArgumentException if configuration is invalid
     * @throws IllegalStateException if Firebase initialization fails
     *
     * Example:
     * ```kotlin
     * // Simple usage
     * val client = HttpLoggerSDK.enhanceOkHttpClient(
     *     context = this,
     *     firebaseConfig = config,
     *     apiKey = "api-v1"
     * )
     *
     * // With custom client builder
     * val customBuilder = OkHttpClient.Builder()
     *     .connectTimeout(30, TimeUnit.SECONDS)
     *     .readTimeout(60, TimeUnit.SECONDS)
     *
     * val client = HttpLoggerSDK.enhanceOkHttpClient(
     *     context = this,
     *     firebaseConfig = config,
     *     apiKey = "api-v1",
     *     clientBuilder = customBuilder,
     *     config = LoggerConfig.forTesting()
     * )
     * ```
     */
    @JvmStatic
    fun enhanceOkHttpClient(
        context: Context,
        firebaseConfig: FirebaseConfig,
        apiKey: String,
        clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
        config: LoggerConfig = LoggerConfig()
    ): OkHttpClient {
        val interceptor = createInterceptor(context, firebaseConfig, apiKey, config)

        return clientBuilder
            .addInterceptor(interceptor)
            .build()
            .also {
                Log.i(TAG, "Enhanced OkHttp client created with logging interceptor")
            }
    }

    /**
     * Creates multiple interceptors for different API keys
     *
     * Useful when you need to log requests to different services or
     * environments under separate API keys for better organization.
     *
     * @param context Android application context
     * @param firebaseConfig Firebase project configuration
     * @param apiKeys Map of service names to API keys
     * @param config Logger configuration (applied to all interceptors)
     * @return Map of service names to configured interceptors
     *
     * Example:
     * ```kotlin
     * val interceptors = HttpLoggerSDK.createMultipleInterceptors(
     *     context = this,
     *     firebaseConfig = config,
     *     apiKeys = mapOf(
     *         "auth-service" to "auth-api-key",
     *         "data-service" to "data-api-key",
     *         "analytics" to "analytics-api-key"
     *     )
     * )
     *
     * val authClient = OkHttpClient.Builder()
     *     .addInterceptor(interceptors["auth-service"]!!)
     *     .build()
     * ```
     */
    @JvmStatic
    fun createMultipleInterceptors(
        context: Context,
        firebaseConfig: FirebaseConfig,
        apiKeys: Map<String, String>,
        config: LoggerConfig = LoggerConfig()
    ): Map<String, HttpLoggerInterceptor> {
        require(apiKeys.isNotEmpty()) { "At least one API key must be provided" }

        return apiKeys.mapValues { (serviceName, apiKey) ->
            Log.d(TAG, "Creating interceptor for service: $serviceName")
            createInterceptor(context, firebaseConfig, apiKey, config)
        }.also {
            Log.i(TAG, "Created ${it.size} HTTP logger interceptors")
        }
    }

    /**
     * Clears Firebase instance and cached resources for a specific configuration
     *
     * Call this method when you no longer need HTTP logging for a specific
     * Firebase configuration to free up resources and prevent memory leaks.
     * This will stop all logging activities for the specified configuration.
     *
     * @param firebaseConfig Firebase configuration to clean up
     *
     * Example:
     * ```kotlin
     * // When switching environments or cleaning up
     * HttpLoggerSDK.clearFirebaseInstance(oldFirebaseConfig)
     * ```
     */
    @JvmStatic
    fun clearFirebaseInstance(firebaseConfig: FirebaseConfig) {
        try {
            FirebaseManager.clearInstance(firebaseConfig)
            Log.i(TAG, "Cleared Firebase instance for project: ${firebaseConfig.projectId}")
        } catch (exception: Exception) {
            Log.e(TAG, "Error clearing Firebase instance", exception)
        }
    }

    /**
     * Clears all Firebase instances and cached resources
     *
     * Use this method for complete cleanup, typically during app shutdown
     * or when resetting the entire SDK state. This will stop all logging
     * activities across all configurations.
     *
     * Example:
     * ```kotlin
     * // In Application.onTerminate() or similar
     * HttpLoggerSDK.clearAllInstances()
     * ```
     */
    @JvmStatic
    fun clearAllInstances() {
        try {
            FirebaseManager.clearAllInstances()
            Log.i(TAG, "Cleared all Firebase instances")
        } catch (exception: Exception) {
            Log.e(TAG, "Error clearing all Firebase instances", exception)
        }
    }

    /**
     * Gets information about currently active logging instances
     *
     * @return Map with diagnostic information about active instances
     *
     * Example:
     * ```kotlin
     * val info = HttpLoggerSDK.getDiagnosticInfo()
     * Log.d("Diagnostics", "Active instances: ${info["activeInstances"]}")
     * Log.d("Diagnostics", "SDK version: ${info["version"]}")
     * ```
     */
    @JvmStatic
    fun getDiagnosticInfo(): Map<String, Any> {
        return mapOf(
            "version" to VERSION,
            "activeInstances" to FirebaseManager.getActiveInstanceCount(),
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Validates a Firebase configuration without initializing
     *
     * @param firebaseConfig Configuration to validate
     * @return List of validation issues (empty if valid)
     *
     * Example:
     * ```kotlin
     * val config = FirebaseConfig.create(...)
     * val issues = HttpLoggerSDK.validateConfiguration(config)
     * if (issues.isNotEmpty()) {
     *     Log.w("Config", "Issues found: ${issues.joinToString()}")
     * }
     * ```
     */
    @JvmStatic
    fun validateConfiguration(firebaseConfig: FirebaseConfig): List<String> {
        val issues = mutableListOf<String>()

        if (firebaseConfig.projectId.isBlank()) {
            issues.add("Project ID cannot be blank")
        }

        if (firebaseConfig.applicationId.isBlank()) {
            issues.add("Application ID cannot be blank")
        }

        if (firebaseConfig.apiKey.isBlank()) {
            issues.add("API Key cannot be blank")
        }

        if (!firebaseConfig.projectId.matches(Regex("^[a-z][a-z0-9-]*[a-z0-9]$"))) {
            issues.add("Project ID format appears invalid")
        }

        return issues
    }
}
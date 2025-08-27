package com.islamzada.http_logger_sdk_android

import android.content.Context
import android.util.Log
import com.islamzada.http_logger_sdk_android.core.FirebaseManager
import com.islamzada.http_logger_sdk_android.models.FirebaseConfig
import com.islamzada.http_logger_sdk_android.models.LoggerConfig
import com.islamzada.http_logger_sdk_android.network.HttpLoggerInterceptor
import okhttp3.OkHttpClient

/**
 * Enhanced HTTP Logger SDK for Android applications
 *
 * This SDK provides comprehensive HTTP request/response logging with:
 * - Complete application context (package, version, build type)
 * - Network information (type, quality, operator)
 * - Performance metrics (timing breakdown, sizes)
 * - Session tracking and error categorization
 * - Device and environment context
 * - Security-aware data handling
 *
 * ## Basic Usage:
 * ```kotlin
 * val firebaseConfig = FirebaseConfig.create(
 *     projectId = "your-project-id",
 *     applicationId = "your-app-id",
 *     apiKey = "your-api-key"
 * )
 *
 * val interceptor = HttpLoggerSDK.createInterceptor(
 *     context = this,
 *     firebaseConfig = firebaseConfig,
 *     apiKey = "unique-api-key"
 * )
 *
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(interceptor)
 *     .build()
 * ```
 *
 * ## Environment-Specific Configuration:
 * ```kotlin
 * // Development with full logging
 * val devInterceptor = HttpLoggerSDK.createInterceptor(
 *     context = this,
 *     firebaseConfig = config,
 *     apiKey = "dev-api",
 *     config = LoggerConfig.forDevelopment(),
 *     environment = "development",
 *     userId = "dev-user-123"
 * )
 *
 * // Production with minimal logging
 * val prodInterceptor = HttpLoggerSDK.createInterceptor(
 *     context = this,
 *     firebaseConfig = config,
 *     apiKey = "prod-api",
 *     config = LoggerConfig.forProduction(),
 *     environment = "production"
 * )
 * ```
 */
object HttpLoggerSDK {

    private const val TAG = "HttpLoggerSDK"
    private const val VERSION = "1.1.0"

    /**
     * Gets the current SDK version
     */
    @JvmStatic
    fun getVersion(): String = VERSION

    /**
     * Creates an enhanced HTTP logger interceptor with comprehensive logging
     *
     * This interceptor captures detailed information including:
     * - Application context (package name, version, build type)
     * - Network information (type, quality, operator)
     * - Performance metrics (timing breakdown, request/response sizes)
     * - Session tracking and error categorization
     * - Device information and environment context
     *
     * @param context Android application context
     * @param firebaseConfig Firebase project configuration
     * @param apiKey Unique API key for log organization
     * @param config Logger configuration controlling detail level
     * @param environment Environment tag (development, staging, production)
     * @param userId Optional user identifier for debugging
     * @param tags Additional custom tags for log categorization
     * @return Configured HttpLoggerInterceptor
     */
    @JvmStatic
    fun createInterceptor(
        context: Context,
        firebaseConfig: FirebaseConfig,
        apiKey: String,
        config: LoggerConfig = LoggerConfig(),
        environment: String = "production",
        userId: String? = null,
        tags: List<String> = emptyList()
    ): HttpLoggerInterceptor {

        require(firebaseConfig.isValid()) {
            "Invalid Firebase configuration. Ensure projectId, applicationId, and apiKey are provided."
        }
        require(apiKey.isNotBlank()) {
            "API Key cannot be blank. Provide a unique identifier for organizing logs."
        }
        require(environment.isNotBlank()) {
            "Environment cannot be blank. Use: development, staging, production, etc."
        }

        val validationIssues = config.validate()
        if (validationIssues.isNotEmpty()) {
            Log.w(TAG, "Logger configuration issues detected: ${validationIssues.joinToString(", ")}")
        }

        try {
            FirebaseManager.initializeFirebase(context, firebaseConfig)

            Log.i(TAG, "Enhanced HTTP Logger SDK initialized successfully")
            Log.d(TAG, "Firebase Project: ${firebaseConfig.projectId}")
            Log.d(TAG, "API Key: ${apiKey.take(8)}...")
            Log.d(TAG, "Environment: $environment")
            Log.d(TAG, "Log Level: ${config.logLevel}")
            Log.d(TAG, "User ID: ${userId?.take(8) ?: "None"}...")

            return HttpLoggerInterceptor(
                context = context,
                firebaseConfig = firebaseConfig,
                apiKey = apiKey,
                config = config,
                environment = environment,
                sdkVersion = VERSION,
                userId = userId
            )

        } catch (exception: Exception) {
            Log.e(TAG, "Failed to initialize Enhanced HTTP Logger SDK", exception)
            throw IllegalStateException(
                "Failed to initialize Firebase for HTTP logging: ${exception.message}",
                exception
            )
        }
    }

    /**
     * Creates an enhanced OkHttp client with comprehensive logging
     *
     * @param context Android application context
     * @param firebaseConfig Firebase project configuration
     * @param apiKey Unique API key for log organization
     * @param clientBuilder Optional custom OkHttp client builder
     * @param config Logger configuration
     * @param environment Environment tag
     * @param userId Optional user identifier
     * @return Configured OkHttpClient with enhanced logging
     */
    @JvmStatic
    fun enhanceOkHttpClient(
        context: Context,
        firebaseConfig: FirebaseConfig,
        apiKey: String,
        clientBuilder: OkHttpClient.Builder = OkHttpClient.Builder(),
        config: LoggerConfig = LoggerConfig(),
        environment: String = "production",
        userId: String? = null
    ): OkHttpClient {
        val interceptor = createInterceptor(
            context = context,
            firebaseConfig = firebaseConfig,
            apiKey = apiKey,
            config = config,
            environment = environment,
            userId = userId
        )

        return clientBuilder
            .addInterceptor(interceptor)
            .build()
            .also {
                Log.i(TAG, "Enhanced OkHttp client created with comprehensive logging")
            }
    }

    /**
     * Creates environment-specific interceptors with appropriate configurations
     *
     * @param context Android application context
     * @param firebaseConfig Firebase project configuration
     * @param baseApiKey Base API key (environment will be appended)
     * @param userId Optional user identifier
     * @return Map of environment names to configured interceptors
     */
    @JvmStatic
    fun createEnvironmentInterceptors(
        context: Context,
        firebaseConfig: FirebaseConfig,
        baseApiKey: String,
        userId: String? = null
    ): Map<String, HttpLoggerInterceptor> {

        val environments = mapOf(
            "development" to LoggerConfig.forDevelopment(),
            "staging" to LoggerConfig.forTesting(),
            "production" to LoggerConfig.forProduction()
        )

        return environments.mapValues { (environment, config) ->
            val apiKey = "${baseApiKey}_${environment}"

            Log.d(TAG, "Creating interceptor for environment: $environment")
            createInterceptor(
                context = context,
                firebaseConfig = firebaseConfig,
                apiKey = apiKey,
                config = config,
                environment = environment,
                userId = userId
            )
        }.also {
            Log.i(TAG, "Created ${it.size} environment-specific interceptors")
        }
    }

    /**
     * Creates multiple service-specific interceptors with different configurations
     *
     * @param context Android application context
     * @param firebaseConfig Firebase project configuration
     * @param serviceConfigs Map of service names to their specific configurations
     * @param environment Current environment
     * @param userId Optional user identifier
     * @return Map of service names to configured interceptors
     */
    @JvmStatic
    fun createServiceInterceptors(
        context: Context,
        firebaseConfig: FirebaseConfig,
        serviceConfigs: Map<String, ServiceConfig>,
        environment: String = "production",
        userId: String? = null
    ): Map<String, HttpLoggerInterceptor> {

        require(serviceConfigs.isNotEmpty()) { "At least one service configuration must be provided" }

        return serviceConfigs.mapValues { (serviceName, serviceConfig) ->
            Log.d(TAG, "Creating interceptor for service: $serviceName")
            createInterceptor(
                context = context,
                firebaseConfig = firebaseConfig,
                apiKey = serviceConfig.apiKey,
                config = serviceConfig.loggerConfig,
                environment = environment,
                userId = userId
            )
        }.also {
            Log.i(TAG, "Created ${it.size} service-specific interceptors")
        }
    }

    /**
     * Gets comprehensive diagnostic information about the SDK
     *
     * @param context Android application context
     * @return Map with detailed diagnostic information
     */
    @JvmStatic
    fun getDetailedDiagnosticInfo(context: Context): Map<String, Any> {
        val diagnostics = mutableMapOf<String, Any>()

        try {
            // SDK Information
            diagnostics["sdkVersion"] = VERSION
            diagnostics["activeFirebaseInstances"] = FirebaseManager.getActiveInstanceCount()
            diagnostics["timestamp"] = System.currentTimeMillis()

            // Application Information
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)

            diagnostics["applicationInfo"] = mapOf(
                "packageName" to context.packageName,
                "appName" to packageManager.getApplicationLabel(applicationInfo).toString(),
                "versionName" to (packageInfo.versionName ?: "Unknown"),
                "versionCode" to if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                },
                "buildType" to if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) "debug" else "release"
            )

            // Device Information
            diagnostics["deviceInfo"] = mapOf(
                "brand" to android.os.Build.BRAND,
                "model" to android.os.Build.MODEL,
                "androidVersion" to android.os.Build.VERSION.RELEASE,
                "apiLevel" to android.os.Build.VERSION.SDK_INT
            )

            // Memory Information
            val runtime = Runtime.getRuntime()
            diagnostics["memoryInfo"] = mapOf(
                "totalMemory" to runtime.totalMemory(),
                "freeMemory" to runtime.freeMemory(),
                "maxMemory" to runtime.maxMemory(),
                "usedMemory" to (runtime.totalMemory() - runtime.freeMemory())
            )

        } catch (exception: Exception) {
            diagnostics["error"] = "Failed to collect diagnostics: ${exception.message}"
            Log.e(TAG, "Error collecting diagnostics", exception)
        }

        return diagnostics
    }

    /**
     * Validates multiple configurations and returns consolidated report
     *
     * @param firebaseConfig Firebase configuration to validate
     * @param loggerConfigs Map of service names to logger configurations
     * @return Validation report with issues and recommendations
     */
    @JvmStatic
    fun validateConfigurations(
        firebaseConfig: FirebaseConfig,
        loggerConfigs: Map<String, LoggerConfig>
    ): ValidationReport {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        // Validate Firebase config
        if (!firebaseConfig.isValid()) {
            issues.add("Invalid Firebase configuration")
        }

        // Validate logger configurations
        loggerConfigs.forEach { (serviceName, config) ->
            val configIssues = config.validate()
            if (configIssues.isNotEmpty()) {
                issues.addAll(configIssues.map { "$serviceName: $it" })
            }

            // Check production safety
            if (!config.isProductionSafe()) {
                warnings.add("$serviceName configuration may not be safe for production")
                recommendations.add("Consider using LoggerConfig.forProduction() for $serviceName in production")
            }

            // Memory usage warnings
            val estimatedMemory = config.estimatedMemoryPerLog()
            if (estimatedMemory > 10 * 1024 * 1024) { // 10MB
                warnings.add("$serviceName estimated memory usage per log is high: ${estimatedMemory / 1024 / 1024}MB")
                recommendations.add("Consider reducing maxResponseBodySize for $serviceName")
            }
        }

        return ValidationReport(issues, warnings, recommendations)
    }

    /**
     * Resets all sessions across all context providers
     * Useful when user logs out or switches accounts
     *
     * @param context Android application context
     */
    @JvmStatic
    fun resetAllSessions(context: Context) {
        try {
            val prefs = context.getSharedPreferences("http_logger_sdk", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.i(TAG, "All sessions reset successfully")
        } catch (exception: Exception) {
            Log.e(TAG, "Error resetting sessions", exception)
        }
    }

    // === RESOURCE MANAGEMENT ===

    /**
     * Clears Firebase instance and cached resources for specific configuration
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
     */
    @JvmStatic
    fun clearAllInstances() {
        try {
            FirebaseManager.clearAllInstances()
            Log.i(TAG, "Cleared all Firebase instances and resources")
        } catch (exception: Exception) {
            Log.e(TAG, "Error clearing all Firebase instances", exception)
        }
    }

    // === DATA CLASSES ===

    /**
     * Configuration for a specific service
     */
    data class ServiceConfig(
        val apiKey: String,
        val loggerConfig: LoggerConfig,
        val tags: List<String> = emptyList()
    )

    /**
     * Validation report containing issues, warnings, and recommendations
     */
    data class ValidationReport(
        val issues: List<String>,
        val warnings: List<String>,
        val recommendations: List<String>
    ) {
        val isValid: Boolean get() = issues.isEmpty()
        val hasWarnings: Boolean get() = warnings.isNotEmpty()

        fun printReport() {
            if (issues.isNotEmpty()) {
                Log.e(TAG, "Validation Issues:")
                issues.forEach { Log.e(TAG, "  - $it") }
            }

            if (warnings.isNotEmpty()) {
                Log.w(TAG, "Validation Warnings:")
                warnings.forEach { Log.w(TAG, "  - $it") }
            }

            if (recommendations.isNotEmpty()) {
                Log.i(TAG, "Recommendations:")
                recommendations.forEach { Log.i(TAG, "  - $it") }
            }

            if (isValid && !hasWarnings) {
                Log.i(TAG, "All configurations are valid!")
            }
        }
    }
}
package com.islamzada.http_logger_sdk_android.core

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.islamzada.http_logger_sdk_android.models.FirebaseConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal manager for Firebase app initialization and Firestore instance management
 *
 * This singleton manages multiple Firebase app instances to support multiple configurations
 * and ensures proper resource management with thread-safe operations.
 *
 * Key features:
 * - Thread-safe initialization and access
 * - Support for multiple Firebase configurations
 * - Automatic app name generation to avoid conflicts
 * - Resource cleanup capabilities
 */
internal object FirebaseManager {

    /**
     * Cache of initialized Firebase apps keyed by generated app names
     * Thread-safe concurrent map for multiple client support
     */
    private val firebaseApps = ConcurrentHashMap<String, FirebaseApp>()

    /**
     * Cache of Firestore instances keyed by generated app names
     * Thread-safe concurrent map for efficient instance reuse
     */
    private val firestoreInstances = ConcurrentHashMap<String, FirebaseFirestore>()

    /**
     * Initializes a Firebase app with the provided configuration
     *
     * This method is thread-safe and will return existing app if already initialized
     * with the same configuration. Uses synchronized block to prevent race conditions
     * during initialization.
     *
     * @param context Android application context
     * @param config Firebase configuration with project details
     * @return Initialized FirebaseApp instance
     * @throws IllegalArgumentException if configuration is invalid
     */
    @Synchronized
    fun initializeFirebase(context: Context, config: FirebaseConfig): FirebaseApp {
        require(config.isValid()) { "Firebase configuration is invalid" }

        val appName = generateAppName(config.projectId, config.apiKey)

        return firebaseApps[appName] ?: run {
            val options = FirebaseOptions.Builder()
                .setProjectId(config.projectId)
                .setApplicationId(config.applicationId)
                .setApiKey(config.apiKey)
                .apply {
                    config.storageBucket?.let { bucket ->
                        setStorageBucket(bucket)
                    }
                }
                .build()

            val app = FirebaseApp.initializeApp(context, options, appName)
            firebaseApps[appName] = app
            app
        }
    }

    /**
     * Gets or creates a Firestore instance for the given configuration
     *
     * @param config Firebase configuration to get Firestore instance for
     * @return FirebaseFirestore instance
     * @throws IllegalStateException if Firebase app is not initialized for this config
     */
    fun getFirestoreInstance(config: FirebaseConfig): FirebaseFirestore {
        val appName = generateAppName(config.projectId, config.apiKey)

        return firestoreInstances[appName] ?: run {
            val app = firebaseApps[appName]
                ?: throw IllegalStateException(
                    "Firebase app not initialized for configuration. " +
                            "Call initializeFirebase() first with project: ${config.projectId}"
                )

            val firestore = FirebaseFirestore.getInstance(app)
            firestoreInstances[appName] = firestore
            firestore
        }
    }

    /**
     * Generates a unique app name based on project ID and API key
     *
     * Format: "HttpLogger_[ProjectId]_[First8CharsOfApiKey]"
     * This ensures uniqueness while keeping names readable for debugging
     *
     * @param projectId Firebase project ID
     * @param apiKey Firebase API key
     * @return Generated unique app name
     */
    private fun generateAppName(projectId: String, apiKey: String): String {
        return "HttpLogger_${projectId}_${apiKey.take(8)}"
    }

    /**
     * Clears all cached instances for a specific configuration
     *
     * This method properly cleans up resources by:
     * 1. Removing Firestore instance from cache
     * 2. Deleting the Firebase app (which releases all resources)
     * 3. Removing app from cache
     *
     * Call this when you no longer need logging for a specific configuration
     * to prevent memory leaks.
     *
     * @param config Firebase configuration to clean up
     */
    @Synchronized
    fun clearInstance(config: FirebaseConfig) {
        val appName = generateAppName(config.projectId, config.apiKey)

        firestoreInstances.remove(appName)

        firebaseApps[appName]?.let { app ->
            try {
                app.delete()
            } catch (e: Exception) {
                android.util.Log.w("FirebaseManager", "Error deleting Firebase app: ${e.message}")
            }
        }
        firebaseApps.remove(appName)
    }

    /**
     * Clears all cached instances and Firebase apps
     *
     * Use this method for complete cleanup, typically during app shutdown
     * or when resetting the entire SDK state.
     */
    @Synchronized
    fun clearAllInstances() {
        firestoreInstances.clear()

        firebaseApps.values.forEach { app ->
            try {
                app.delete()
            } catch (e: Exception) {
                android.util.Log.w("FirebaseManager", "Error deleting Firebase app: ${e.message}")
            }
        }
        firebaseApps.clear()
    }

    /**
     * Gets the current number of active Firebase app instances
     * Useful for debugging and monitoring resource usage
     *
     * @return Number of active Firebase apps
     */
    fun getActiveInstanceCount(): Int = firebaseApps.size

    /**
     * Checks if a Firebase app is initialized for the given configuration
     *
     * @param config Firebase configuration to check
     * @return true if app is initialized, false otherwise
     */
    fun isInitialized(config: FirebaseConfig): Boolean {
        val appName = generateAppName(config.projectId, config.apiKey)
        return firebaseApps.containsKey(appName)
    }
}
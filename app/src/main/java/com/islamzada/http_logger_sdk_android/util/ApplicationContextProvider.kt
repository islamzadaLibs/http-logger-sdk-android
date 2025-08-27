package com.islamzada.http_logger_sdk_android.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Utility class for extracting application and system context information
 *
 * This class provides comprehensive context information including:
 * - Application details (name, package, version, build type)
 * - Network information (type, operator, quality)
 * - Device information (model, OS version, etc.)
 * - Session management
 */
internal class ApplicationContextProvider(private val context: Context) {

    companion object {
        private const val TAG = "AppContextProvider"
        private const val PREF_SESSION_ID = "http_logger_session_id"
        private const val PREF_REQUEST_SEQUENCE = "http_logger_request_sequence"

        // Cache for static information that doesn't change
        private var cachedAppInfo: AppInfo? = null
        private var cachedDeviceInfo: String? = null
        private var sessionId: String? = null
    }

    /**
     * Data class holding application information
     */
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val appVersion: String,
        val appVersionCode: Int,
        val buildType: String
    )

    /**
     * Data class holding network information
     */
    data class NetworkInfo(
        val networkType: String,
        val networkOperator: String?,
        val connectionQuality: String
    )

    /**
     * Gets cached or extracts application information
     */
    fun getAppInfo(): AppInfo {
        return cachedAppInfo ?: run {
            val info = extractAppInfo()
            cachedAppInfo = info
            info
        }
    }

    /**
     * Gets cached or extracts device information
     */
    fun getDeviceInfo(): String {
        return cachedDeviceInfo ?: run {
            val info = extractDeviceInfo()
            cachedDeviceInfo = info
            info
        }
    }

    /**
     * Gets current network information
     */
    fun getNetworkInfo(): NetworkInfo {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)

                when {
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
                        NetworkInfo(
                            networkType = "WiFi",
                            networkOperator = null,
                            connectionQuality = getWiFiQuality(networkCapabilities)
                        )
                    }
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                        NetworkInfo(
                            networkType = "Mobile",
                            networkOperator = getMobileOperator(telephonyManager),
                            connectionQuality = getMobileQuality(networkCapabilities, telephonyManager)
                        )
                    }
                    networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> {
                        NetworkInfo(
                            networkType = "Ethernet",
                            networkOperator = null,
                            connectionQuality = "Excellent"
                        )
                    }
                    else -> NetworkInfo("Unknown", null, "Unknown")
                }
            } else {
                // Fallback for older Android versions
                getLegacyNetworkInfo(connectivityManager)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to get network info: ${exception.message}")
            NetworkInfo("Unknown", null, "Unknown")
        }
    }

    /**
     * Gets or generates session ID
     */
    fun getSessionId(): String {
        return sessionId ?: run {
            val prefs = context.getSharedPreferences("http_logger_sdk", Context.MODE_PRIVATE)
            val storedSessionId = prefs.getString(PREF_SESSION_ID, null)

            val newSessionId = storedSessionId ?: generateNewSessionId()

            prefs.edit()
                .putString(PREF_SESSION_ID, newSessionId)
                .apply()

            sessionId = newSessionId
            newSessionId
        }
    }

    /**
     * Gets and increments request sequence number
     */
    fun getNextRequestSequence(): Int {
        val prefs = context.getSharedPreferences("http_logger_sdk", Context.MODE_PRIVATE)
        val currentSequence = prefs.getInt(PREF_REQUEST_SEQUENCE, 0)
        val nextSequence = currentSequence + 1

        prefs.edit()
            .putInt(PREF_REQUEST_SEQUENCE, nextSequence)
            .apply()

        return nextSequence
    }

    /**
     * Resets session (generates new session ID and resets sequence)
     */
    fun resetSession() {
        val prefs = context.getSharedPreferences("http_logger_sdk", Context.MODE_PRIVATE)
        val newSessionId = generateNewSessionId()

        prefs.edit()
            .putString(PREF_SESSION_ID, newSessionId)
            .putInt(PREF_REQUEST_SEQUENCE, 0)
            .apply()

        sessionId = newSessionId
    }

    // === PRIVATE HELPER METHODS ===

    /**
     * Extracts application information from PackageManager
     */
    private fun extractAppInfo(): AppInfo {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)

            val appName = packageManager.getApplicationLabel(applicationInfo).toString()
            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }

            val buildType = determineBuildType(context)

            AppInfo(
                packageName = packageName,
                appName = appName,
                appVersion = versionName,
                appVersionCode = versionCode,
                buildType = buildType
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to extract app info: ${exception.message}", exception)
            AppInfo(
                packageName = context.packageName,
                appName = "Unknown",
                appVersion = "Unknown",
                appVersionCode = 0,
                buildType = "unknown"
            )
        }
    }

    /**
     * Determines build type (debug, release, etc.)
     */
    private fun determineBuildType(context: Context): String {
        return try {
            val applicationInfo = context.applicationInfo
            if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                "debug"
            } else {
                "release"
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * Extracts device information
     */
    private fun extractDeviceInfo(): String {
        return try {
            "${Build.BRAND} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
        } catch (_: Exception) {
            "Unknown Device"
        }
    }

    /**
     * Gets WiFi connection quality
     */
    private fun getWiFiQuality(networkCapabilities: NetworkCapabilities): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val signalStrength = networkCapabilities.signalStrength
                when {
                    signalStrength >= -50 -> "Excellent"
                    signalStrength >= -60 -> "Good"
                    signalStrength >= -70 -> "Fair"
                    signalStrength >= -80 -> "Poor"
                    else -> "Very Poor"
                }
            } else {
                "Good" // Default for older versions
            }
        } catch (_: Exception) {
            "Unknown"
        }
    }

    /**
     * Gets mobile operator name
     */
    @SuppressLint("MissingPermission")
    private fun getMobileOperator(telephonyManager: TelephonyManager): String? {
        return try {
            telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gets mobile connection quality
     */
    @SuppressLint("MissingPermission")
    private fun getMobileQuality(
        networkCapabilities: NetworkCapabilities,
        telephonyManager: TelephonyManager
    ): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val signalStrength = networkCapabilities.signalStrength
                when {
                    signalStrength >= -85 -> "Excellent"
                    signalStrength >= -100 -> "Good"
                    signalStrength >= -110 -> "Fair"
                    signalStrength >= -120 -> "Poor"
                    else -> "Very Poor"
                }
            } else {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    when (telephonyManager.networkType) {
                        TelephonyManager.NETWORK_TYPE_LTE,
                        TelephonyManager.NETWORK_TYPE_NR -> "Good"
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_HSPAP -> "Fair"
                        else -> "Poor"
                    }
                } else {
                    "Unknown"
                }
            }
        } catch (_: SecurityException) {
            "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    /**
     * Legacy network info for older Android versions
     */
    @Suppress("DEPRECATION")
    private fun getLegacyNetworkInfo(connectivityManager: ConnectivityManager): NetworkInfo {
        return try {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            when (activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> NetworkInfo("WiFi", null, "Good")
                ConnectivityManager.TYPE_MOBILE -> {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    NetworkInfo(
                        networkType = "Mobile",
                        networkOperator = getMobileOperator(telephonyManager),
                        connectionQuality = "Fair"
                    )
                }
                ConnectivityManager.TYPE_ETHERNET -> NetworkInfo("Ethernet", null, "Excellent")
                else -> NetworkInfo("Unknown", null, "Unknown")
            }
        } catch (_: Exception) {
            NetworkInfo("Unknown", null, "Unknown")
        }
    }

    /**
     * Generates a new session ID
     */
    private fun generateNewSessionId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        return "${timestamp}-${uuid}"
    }
}
package com.islamzada.http_logger_sdk_android.models

import kotlinx.serialization.Serializable

@Serializable
data class FirebaseConfig(
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val storageBucket: String? = null
) {
    companion object {
        fun create(
            projectId: String,
            applicationId: String,
            apiKey: String,
            storageBucket: String? = null
        ): FirebaseConfig {
            require(projectId.isNotBlank()) { "Project ID cannot be blank" }
            require(applicationId.isNotBlank()) { "Application ID cannot be blank" }
            require(apiKey.isNotBlank()) { "API Key cannot be blank" }

            return FirebaseConfig(
                projectId = projectId.trim(),
                applicationId = applicationId.trim(),
                apiKey = apiKey.trim(),
                storageBucket = storageBucket?.trim()?.takeIf { it.isNotBlank() }
            )
        }
    }

    fun isValid(): Boolean = projectId.isNotBlank() &&
            applicationId.isNotBlank() &&
            apiKey.isNotBlank()
}
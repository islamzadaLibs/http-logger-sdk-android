package com.islamzada.http_logger_sdk_android.data

import android.util.Log
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.islamzada.http_logger_sdk_android.models.HttpLogModel
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Repository class for managing HTTP logs in Firebase Firestore
 *
 * This repository handles all Firestore operations for HTTP logging including:
 * - Saving individual log entries
 * - Querying logs with various filters
 * - Real-time log observation
 * - Batch operations for cleanup
 *
 * Firestore Structure:
 * ```
 * /http_logs/{apiKey}/logs/{logId}
 * ```
 *
 * This structure allows:
 * - Multiple API keys to coexist
 * - Efficient querying per API key
 * - Easy cleanup and organization
 * - Scalable log storage
 */
internal class FirebaseLogRepository(
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "FirebaseLogRepository"
        private const val HTTP_LOGS_COLLECTION = "http_logs"
        private const val LOGS_SUB_COLLECTION = "logs"

        // Query limits for safety
        private const val MAX_QUERY_LIMIT = 10000L
        private const val DEFAULT_QUERY_LIMIT = 1000L
    }

    /**
     * Gets the logs collection reference for a specific API key
     *
     * @param apiKey API key to scope the logs to
     * @return CollectionReference for the logs
     */
    private fun getLogsCollection(apiKey: String) =
        firestore.collection(HTTP_LOGS_COLLECTION)
            .document(apiKey)
            .collection(LOGS_SUB_COLLECTION)

    /**
     * Saves a single HTTP log to Firestore
     *
     * Uses the log's ID as the document ID for idempotent operations.
     * If a log with the same ID exists, it will be overwritten.
     *
     * @param log HttpLogModel to save
     * @param apiKey API key to scope the log under
     * @throws Exception if save operation fails
     */
    suspend fun saveLog(log: HttpLogModel, apiKey: String) {
        try {
            getLogsCollection(apiKey)
                .document(log.id)
                .set(log)
                .await()

            Log.d(TAG, "Successfully saved log: ${log.id} for API key: ${apiKey.take(8)}...")
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to save log: ${log.id}", exception)
            throw exception
        }
    }

    /**
     * Saves multiple HTTP logs in a batch operation
     *
     * More efficient than individual saves for bulk operations.
     * Atomic - either all logs are saved or none are.
     *
     * @param logs List of HttpLogModel to save
     * @param apiKey API key to scope the logs under
     * @throws Exception if batch operation fails
     */
    suspend fun saveLogs(logs: List<HttpLogModel>, apiKey: String) {
        if (logs.isEmpty()) return

        try {
            val batch = firestore.batch()
            val collection = getLogsCollection(apiKey)

            logs.forEach { log ->
                val docRef = collection.document(log.id)
                batch.set(docRef, log)
            }

            batch.commit().await()
            Log.d(TAG, "Successfully saved ${logs.size} logs in batch for API key: ${apiKey.take(8)}...")
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to save logs in batch", exception)
            throw exception
        }
    }

    /**
     * Retrieves HTTP logs for a specific API key with optional filtering
     *
     * @param apiKey API key to get logs for
     * @param limit Maximum number of logs to return (default: 1000, max: 10000)
     * @param startAfter Start after this timestamp for pagination
     * @param endBefore End before this timestamp for time range filtering
     * @return List of HttpLogModel ordered by timestamp (newest first)
     * @throws Exception if query fails
     */
    suspend fun getLogsByApiKey(
        apiKey: String,
        limit: Long = DEFAULT_QUERY_LIMIT,
        startAfter: Date? = null,
        endBefore: Date? = null
    ): List<HttpLogModel> {
        return try {
            val safeLimit = limit.coerceIn(1L, MAX_QUERY_LIMIT)

            var query: Query = getLogsCollection(apiKey)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(safeLimit)

            // Apply time range filters if provided
            startAfter?.let { date ->
                query = query.startAfter(com.google.firebase.Timestamp(date))
            }

            endBefore?.let { date ->
                query = query.endBefore(com.google.firebase.Timestamp(date))
            }

            val result = query.get().await().toObjects(HttpLogModel::class.java)
            Log.d(TAG, "Retrieved ${result.size} logs for API key: ${apiKey.take(8)}...")
            result
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to get logs by API key", exception)
            throw exception
        }
    }

    /**
     * Retrieves HTTP logs filtered by response code
     *
     * @param apiKey API key to get logs for
     * @param responseCode HTTP response code to filter by
     * @param limit Maximum number of logs to return
     * @return List of HttpLogModel with matching response code
     */
    suspend fun getLogsByResponseCode(
        apiKey: String,
        responseCode: Int,
        limit: Long = DEFAULT_QUERY_LIMIT
    ): List<HttpLogModel> {
        return try {
            val safeLimit = limit.coerceIn(1L, MAX_QUERY_LIMIT)

            val result = getLogsCollection(apiKey)
                .whereEqualTo("responseCode", responseCode)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get()
                .await()
                .toObjects(HttpLogModel::class.java)

            Log.d(TAG, "Retrieved ${result.size} logs with response code $responseCode")
            result
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to get logs by response code", exception)
            throw exception
        }
    }

    /**
     * Retrieves HTTP logs filtered by method
     *
     * @param apiKey API key to get logs for
     * @param method HTTP method to filter by (GET, POST, etc.)
     * @param limit Maximum number of logs to return
     * @return List of HttpLogModel with matching method
     */
    suspend fun getLogsByMethod(
        apiKey: String,
        method: String,
        limit: Long = DEFAULT_QUERY_LIMIT
    ): List<HttpLogModel> {
        return try {
            val safeLimit = limit.coerceIn(1L, MAX_QUERY_LIMIT)

            val result = getLogsCollection(apiKey)
                .whereEqualTo("method", method.uppercase())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get()
                .await()
                .toObjects(HttpLogModel::class.java)

            Log.d(TAG, "Retrieved ${result.size} logs with method $method")
            result
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to get logs by method", exception)
            throw exception
        }
    }

    /**
     * Observes HTTP logs in real-time for a specific API key
     *
     * Sets up a Firestore listener that triggers the callback whenever
     * logs are added, modified, or removed. Useful for real-time dashboards.
     *
     * @param apiKey API key to observe logs for
     * @param onLogsReceived Callback function called with query results
     * @param limit Maximum number of logs to observe (default: 1000)
     * @return ListenerRegistration to manage the listener lifecycle
     */
    fun observeLogsRealtime(
        apiKey: String,
        onLogsReceived: (Result<List<HttpLogModel>>) -> Unit,
        limit: Long = DEFAULT_QUERY_LIMIT
    ): ListenerRegistration {
        val safeLimit = limit.coerceIn(1L, MAX_QUERY_LIMIT)

        return getLogsCollection(apiKey)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(safeLimit)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> {
                        Log.e(TAG, "Real-time observation error", error)
                        onLogsReceived(Result.failure(error))
                    }
                    snapshot != null -> {
                        try {
                            val logs = snapshot.toObjects(HttpLogModel::class.java)
                            Log.d(TAG, "Real-time update: ${logs.size} logs received")
                            onLogsReceived(Result.success(logs))
                        } catch (exception: Exception) {
                            Log.e(TAG, "Failed to parse logs from snapshot", exception)
                            onLogsReceived(Result.failure(exception))
                        }
                    }
                    else -> {
                        Log.d(TAG, "Empty snapshot received")
                        onLogsReceived(Result.success(emptyList()))
                    }
                }
            }
    }

    /**
     * Deletes logs older than specified date
     *
     * Useful for implementing log retention policies.
     * Deletes in batches to avoid timeout issues with large datasets.
     *
     * @param apiKey API key to clean up logs for
     * @param olderThan Delete logs older than this date
     * @param batchSize Number of documents to delete per batch (default: 500)
     * @return Number of logs deleted
     */
    suspend fun deleteLogsOlderThan(
        apiKey: String,
        olderThan: Date,
        batchSize: Int = 500
    ): Int {
        return try {
            var totalDeleted = 0
            val timestamp = com.google.firebase.Timestamp(olderThan)

            do {
                val snapshot = getLogsCollection(apiKey)
                    .whereLessThan("timestamp", timestamp)
                    .limit(batchSize.toLong())
                    .get()
                    .await()

                if (snapshot.isEmpty) break

                val batch = firestore.batch()
                snapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }

                batch.commit().await()
                totalDeleted += snapshot.size()

                Log.d(TAG, "Deleted batch of ${snapshot.size()} old logs")

            } while (snapshot.size() >= batchSize)

            Log.i(TAG, "Total logs deleted: $totalDeleted for API key: ${apiKey.take(8)}...")
            totalDeleted
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to delete old logs", exception)
            throw exception
        }
    }

    /**
     * Deletes all logs for a specific API key
     *
     * Use with caution - this operation cannot be undone.
     * Deletes in batches to handle large datasets efficiently.
     *
     * @param apiKey API key to delete all logs for
     * @param batchSize Number of documents to delete per batch
     * @return Number of logs deleted
     */
    suspend fun deleteAllLogs(
        apiKey: String,
        batchSize: Int = 500
    ): Int {
        return try {
            var totalDeleted = 0

            do {
                val snapshot = getLogsCollection(apiKey)
                    .limit(batchSize.toLong())
                    .get()
                    .await()

                if (snapshot.isEmpty) break

                val batch = firestore.batch()
                snapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }

                batch.commit().await()
                totalDeleted += snapshot.size()

                Log.d(TAG, "Deleted batch of ${snapshot.size()} logs")

            } while (snapshot.size() >= batchSize)

            Log.i(TAG, "Total logs deleted: $totalDeleted for API key: ${apiKey.take(8)}...")
            totalDeleted
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to delete all logs", exception)
            throw exception
        }
    }

    /**
     * Gets the total count of logs for an API key
     *
     * @param apiKey API key to count logs for
     * @return Total number of logs
     */
    suspend fun getLogsCount(apiKey: String): Long {
        return try {
            val result = getLogsCollection(apiKey)
                .count()
                .get(AggregateSource.SERVER)
                .await()

            val count = result.count
            Log.d(TAG, "Total logs count: $count for API key: ${apiKey.take(8)}...")
            count
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to get logs count", exception)
            throw exception
        }
    }

}
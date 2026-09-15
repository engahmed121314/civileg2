package com.civileg.app.data

import android.content.Context
import com.civileg.app.db.DesignRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ProjectSyncManager: handles synchronization between local Room DB and Next.js backend.
 * Implementation is simplified for this expansion.
 */
@Singleton
class ProjectSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DesignRepository
) {
    private val gson = Gson()
    private val BASE_URL = "https://siteengineerpro.com/api/sync"

    /**
     * Syncs all local projects and designs to the cloud.
     */
    suspend fun syncAllProjects(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch data from local DB (Getting all designs for a simple sync)
            // In a real app, we'd sync per project or use a sync queue
            val allDesigns = repository.searchDesigns("").first()
            if (allDesigns.isEmpty()) return@withContext true

            // 2. Prepare payload
            val payload = gson.toJson(allDesigns)

            // 3. Send to Next.js API
            val url = URL(BASE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write(payload) }

            val responseCode = conn.responseCode
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

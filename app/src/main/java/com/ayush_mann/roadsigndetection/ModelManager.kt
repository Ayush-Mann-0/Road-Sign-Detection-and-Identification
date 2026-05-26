package com.ayush_mann.roadsigndetection

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * ModelManager — Dynamic Model Updater
 *
 * This version fetches a remote manifest JSON from GitHub to check for updates.
 * This allows you to update the model on GitHub without updating the app APK.
 *
 * Required: A 'model_manifest.json' file hosted at the URL below.
 */
object ModelManager {

    private const val TAG = "ModelManager"
    private const val PREFS_NAME = "ModelManagerPrefs"
    private const val KEY_VERSION = "model_version"

    /**
     * URL pointing to the raw JSON file in your GitHub repo.
     * Make sure this file exists and is public.
     */
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/Ayush-Mann-0/Road-Sign-Detection-and-Identification/main/model_manifest.json"

    // In-memory cache so we don't fetch the JSON every single time we run a check
    @Volatile
    private var cachedManifest: Map<String, RemoteModelConfig>? = null

    data class RemoteModelConfig(
        val version: String,
        val url: String,
        val checksum: String
    )

    sealed class ModelStatus {
        object Ready : ModelStatus()
        object NoNetwork : ModelStatus()
        data class Outdated(
            val currentVersion: String,
            val newVersion: String,
            val config: RemoteModelConfig
        ) : ModelStatus()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the path TFLite should use.
     * 1. If a downloaded file exists in filesDir, use that (Updated model).
     * 2. Otherwise, fall back to the asset name (Bundled model).
     */
    fun resolveModelPath(context: Context, fileName: String): String {
        val cached = cachedFile(context, fileName)
        return if (cached.exists()) cached.absolutePath else fileName
    }

    /**
     * Fetches the remote manifest and compares versions.
     * This is a suspend function and must be called from a coroutine.
     */
    suspend fun checkVersion(context: Context, fileName: String): ModelStatus =
        withContext(Dispatchers.IO) {
            // 1. Fetch the config from GitHub
            val manifest = fetchManifest()

            // 2. Handle Network Errors
            if (manifest == null) {
                Log.w(TAG, "Failed to fetch manifest. Possibly offline.")
                return@withContext ModelStatus.NoNetwork
            }

            // 3. Get config for this specific file
            val config = manifest[fileName]

            // 4. If file isn't in manifest, assume we use the bundled asset (Ready)
            if (config == null) {
                Log.d(TAG, "No remote config found for $fileName in manifest.")
                return@withContext ModelStatus.Ready
            }

            // 5. Compare local vs remote versions
            val installed = getStoredVersion(context, fileName)

            return@withContext if (installed != config.version) {
                ModelStatus.Outdated(
                    currentVersion = installed,
                    newVersion = config.version,
                    config = config
                )
            } else {
                ModelStatus.Ready
            }
        }

    /**
     * Downloads the model using the provided [config].
     * Verifies SHA-256 checksum and saves atomically.
     */
    suspend fun downloadModel(
        context: Context,
        fileName: String,
        config: RemoteModelConfig,
        onProgress: (Int) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (config.url.isBlank()) return@withContext false

        val dest = cachedFile(context, fileName)
        val tmp = File(dest.parent, "$fileName.tmp")

        try {
            Log.i(TAG, "Starting download: ${config.url}")

            val conn = (URL(config.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000 // Increased timeout for large models
                requestMethod = "GET"
                // optional: set user agent to avoid 403 from some servers
                setRequestProperty("User-Agent", "RoadSignApp/1.0")
            }
            conn.connect()

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP ${conn.responseCode} downloading $fileName")
                return@withContext false
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (totalBytes > 0) {
                            val percent = (downloaded * 100 / totalBytes).toInt()
                            onProgress(percent)
                        }
                    }
                }
            }
            onProgress(100) // Ensure 100% is hit at the end

            // Verify Checksum
            if (config.checksum.isNotBlank()) {
                val computedHash = sha256Hex(tmp)
                // Note: Ensure manifest does NOT have "sha256:" prefix
                if (!computedHash.equals(config.checksum, ignoreCase = true)) {
                    Log.e(TAG, "Checksum mismatch for $fileName")
                    Log.e(TAG, "Expected: ${config.checksum}")
                    Log.e(TAG, "Got:      $computedHash")
                    tmp.delete()
                    return@withContext false
                }
                Log.d(TAG, "Checksum verified successfully.")
            }

            // Atomic replace: delete old, rename new
            if (dest.exists()) dest.delete()
            val success = tmp.renameTo(dest)

            if (success) {
                // Update stored version ONLY on success
                storeVersion(context, fileName, config.version)
                Log.i(TAG, "Successfully updated $fileName to v${config.version}")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to rename temp file to $fileName")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            if (tmp.exists()) tmp.delete()
            return@withContext false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchManifest(): Map<String, RemoteModelConfig>? =
        withContext(Dispatchers.IO) {
            // Return cached version if available
            cachedManifest?.let { return@withContext it }

            return@withContext try {
                val url = URL(MANIFEST_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Manifest fetch failed: HTTP ${conn.responseCode}")
                    return@withContext null
                }

                val jsonText = conn.inputStream.bufferedReader().readText()
                val parsed = parseManifest(jsonText)

                // Cache it for this session
                cachedManifest = parsed
                parsed
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching manifest", e)
                null
            }
        }

    private fun parseManifest(json: String): Map<String, RemoteModelConfig> {
        val result = mutableMapOf<String, RemoteModelConfig>()
        try {
            val root = JSONObject(json)
            val models = root.getJSONObject("models")

            val keys = models.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = models.getJSONObject(key)

                result[key] = RemoteModelConfig(
                    version = obj.getString("version"),
                    url = obj.getString("url"),
                    checksum = obj.optString("checksum", "")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing manifest JSON", e)
        }
        return result
    }

    private fun cachedFile(context: Context, fileName: String) =
        File(context.filesDir, fileName)

    private fun getStoredVersion(context: Context, fileName: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default to "none" so the first time it runs, it downloads if a manifest exists
        return prefs.getString("${KEY_VERSION}_$fileName", "none") ?: "none"
    }

    private fun storeVersion(context: Context, fileName: String, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString("${KEY_VERSION}_$fileName", version) }
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
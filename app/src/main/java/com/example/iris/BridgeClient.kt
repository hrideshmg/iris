package com.example.iris

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class BridgeClient(private val config: SecureConfig) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun uploadAudio(file: File, onResult: (PttMessage) -> Unit) {
        if (!config.isConfigured) {
            Log.w(TAG, "Bridge not configured, skipping upload")
            return
        }

        val url = buildUrl(config.bridgeUrl)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.bearerToken}")
            .post(body)
            .build()

        try {
            http.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.d(TAG, "Upload succeeded: ${response.code} $bodyStr")
                    try {
                        val json = JSONObject(bodyStr)
                        val msg = PttMessage(
                            transcript = json.optString("transcript"),
                            response = json.optString("response"),
                            timestamp = System.currentTimeMillis()
                        )
                        onResult(msg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse response", e)
                    }
                } else {
                    Log.e(TAG, "Upload failed: ${response.code} $bodyStr")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Upload error", e)
        }
    }

    private fun buildUrl(base: String): String {
        val stripped = base.trimEnd('/')
        return if (stripped.endsWith(PTT_PATH)) stripped
        else "$stripped$PTT_PATH"
    }

    companion object {
        private const val TAG = "BridgeClient"
        private const val PTT_PATH = "/ptt/audio"
    }
}

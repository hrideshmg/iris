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

    fun uploadAudio(
        file: File,
        onResult: (PttMessage) -> Unit,
        onError: (String) -> Unit = {},
    ) {
        if (!config.isConfigured) {
            Log.w(TAG, "Bridge not configured, skipping upload")
            onError("Not set up — add your bridge URL and token in settings.")
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
                        onError("Couldn't read the reply from Hermes.")
                    }
                } else {
                    Log.e(TAG, "Upload failed: ${response.code} $bodyStr")
                    onError("Bridge returned ${response.code}. Check your settings.")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Upload error", e)
            onError("Couldn't reach the bridge. Check your connection.")
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

package com.example.iris

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class BridgeClient(private val config: SecureConfig) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun uploadAudio(file: File) {
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
                if (response.isSuccessful) {
                    Log.d(TAG, "Upload succeeded: ${response.code} ${response.body?.string()}")
                } else {
                    Log.e(TAG, "Upload failed: ${response.code} ${response.body?.string()}")
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

package com.example.iris

import android.content.Context
import org.json.JSONObject
import java.io.File

data class PttMessage(
    val transcript: String,
    val response: String,
    val timestamp: Long
)

object MessageStore {
    private const val FILE_NAME = "last_message.json"

    fun load(context: Context): PttMessage? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText())
            PttMessage(
                transcript = obj.getString("transcript"),
                response = obj.getString("response"),
                timestamp = obj.getLong("timestamp")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, message: PttMessage) {
        val obj = JSONObject().apply {
            put("transcript", message.transcript)
            put("response", message.response)
            put("timestamp", message.timestamp)
        }
        File(context.filesDir, FILE_NAME).writeText(obj.toString())
    }
}

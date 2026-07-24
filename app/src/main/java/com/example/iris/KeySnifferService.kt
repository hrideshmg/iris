package com.example.iris

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger


class KeySnifferService : AccessibilityService() {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private lateinit var bridgeClient: BridgeClient
    private val uploadExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        bridgeClient = BridgeClient(SecureConfig(this))
        createNotificationChannel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        uploadExecutor.shutdown()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.scanCode != ESSENTIAL_SCAN_CODE) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> startRecording()
            KeyEvent.ACTION_UP -> stopRecording()
            else -> return false
        }
        return true
    }

    private fun startRecording() {
        val dir = File(filesDir, "clips").apply { mkdirs() }
        val file = File(dir, "clip_${System.currentTimeMillis()}.m4a")
        currentFile = file


        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(16_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r

            vibrate()
            Log.d(TAG, "recording started: ${currentFile?.absolutePath}")
            broadcastStatus(STATUS_RECORDING)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            r.release()
            recorder = null
            broadcastStatus(STATUS_ERROR, "Couldn't start recording.")
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            Log.w(TAG, "stop() failed (too short?): ${e.message}")
            cleanup()
            broadcastStatus(STATUS_DISCARDED)
            return
        }

        val fileToUpload = currentFile
        cleanup()

        if (fileToUpload != null) {
            broadcastStatus(STATUS_WORKING)
            uploadExecutor.execute {
                bridgeClient.uploadAudio(
                    fileToUpload,
                    onResult = { msg ->
                        MessageStore.save(this, msg)
                        broadcastResponse(msg)
                        postNotification(msg)
                    },
                    onError = { reason -> broadcastStatus(STATUS_ERROR, reason) },
                )
            }
        } else {
            broadcastStatus(STATUS_DISCARDED)
        }
        vibrate()
        Log.d(TAG, "recording saved: ${fileToUpload?.absolutePath}")
    }

    private fun vibrate() {
        val v = ContextCompat.getSystemService(this, Vibrator::class.java)?: return
        if (v.hasVibrator()) {
            v.vibrate(VibrationEffect.createOneShot(100, 100))
        }
    }

    private fun cleanup() {
        recorder?.release()
        recorder = null
    }

    private fun broadcastStatus(status: String, detail: String? = null) {
        sendBroadcast(
            Intent(ACTION_STATUS)
                .setPackage(packageName)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_DETAIL, detail)
        )
    }

    private fun broadcastResponse(msg: PttMessage) {
        sendBroadcast(
            Intent(ACTION_RESPONSE)
                .setPackage(packageName)
                .putExtra(EXTRA_TRANSCRIPT, msg.transcript)
                .putExtra(EXTRA_RESPONSE, msg.response)
                .putExtra(EXTRA_TIMESTAMP, msg.timestamp)
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Iris Responses",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun postNotification(msg: PttMessage) {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reply from Hermes.")
            .setContentText(msg.response.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.response))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(notifId.getAndIncrement(), notif)
    }

    companion object {
        const val ESSENTIAL_SCAN_CODE = 250
        const val ACTION_STATUS = "com.example.iris.STATUS"
        const val ACTION_RESPONSE = "com.example.iris.RESPONSE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_DETAIL = "detail"
        const val EXTRA_TRANSCRIPT = "transcript"
        const val EXTRA_RESPONSE = "response"
        const val EXTRA_TIMESTAMP = "timestamp"

        // Lifecycle of a single push-to-talk turn.
        const val STATUS_RECORDING = "recording"
        const val STATUS_WORKING = "working"
        const val STATUS_DISCARDED = "discarded"
        const val STATUS_ERROR = "error"

        private const val NOTIF_CHANNEL_ID = "iris_responses"
        private const val TAG = "KeySnifferService"
        private val notifId = AtomicInteger(1000)
    }
}

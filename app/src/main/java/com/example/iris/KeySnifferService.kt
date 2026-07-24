package com.example.iris

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.io.File


class KeySnifferService : AccessibilityService() {

    private var recorder: MediaRecorder? = null
    private var currentFile : File? = null




    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

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
            broadcast("DOWN")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            r.release()
            recorder = null
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            Log.w(TAG, "stop() failed (too short?): ${e.message}")
            cleanup()
            broadcast("UP")
            return
        }

        vibrate()
        cleanup()
        Log.d(TAG, "recording saved: ${currentFile?.absolutePath}")
        broadcast("UP")
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

    private fun broadcast(action: String) {
        sendBroadcast(
            Intent(ACTION_KEY_EVENT)
                .setPackage(packageName)
                .putExtra(EXTRA_ACTION, action)
        )
    }

    companion object {
        const val ESSENTIAL_SCAN_CODE = 250
        const val ACTION_KEY_EVENT = "com.example.iris.ESSENTIAL_KEY"
        const val EXTRA_ACTION = "action"
        private const val TAG = "KeySnifferService"
    }
}

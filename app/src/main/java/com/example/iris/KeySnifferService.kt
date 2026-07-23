package com.example.iris

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class KeySnifferService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.scanCode != ESSENTIAL_SCAN_CODE) return false

        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> "DOWN"
            KeyEvent.ACTION_UP -> "UP"
            else -> return false
        }
        sendBroadcast(Intent(ACTION_KEY_EVENT).setPackage(packageName).putExtra("action", action))
        return true  // consume so Nothing OS doesn't also handle it
    }

    companion object {
        const val ESSENTIAL_SCAN_CODE = 250
        const val ACTION_KEY_EVENT = "com.example.iris.ESSENTIAL_KEY"
        private const val TAG = "KeySnifferService"
    }
}

package com.example.iris

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.provider.Settings
import android.text.TextUtils
import com.example.iris.ui.theme.IrisTheme

class MainActivity : ComponentActivity() {
    private fun checkAndRequestAudioPermission() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(this, "Microphone permission is required, please enable in settings", Toast.LENGTH_SHORT).show()
                }
            }.launch(permission)
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check audio runtime permission on launch
        checkAndRequestAudioPermission()

        val config = SecureConfig(this)

        setContent {
            IrisTheme {
                var keyState by remember { mutableStateOf("waiting…") }
                var isAccessibilityEnabled by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }

                // Check service status when returning to the app
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            val action = intent.getStringExtra(KeySnifferService.EXTRA_ACTION) ?: "?"
                            keyState = action
                        }
                    }

                    // helper function for backwards-compatible API requirements introduced in Android 14
                    ContextCompat.registerReceiver(
                        this@MainActivity,
                        receiver,
                        IntentFilter(KeySnifferService.ACTION_KEY_EVENT),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )

                    onDispose { unregisterReceiver(receiver) }
                }

                // Update accessibility status whenever activity resumes
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    isAccessibilityEnabled = isAccessibilityServiceEnabled(
                        this@MainActivity,
                        KeySnifferService::class.java
                    )
                }

                if (showSettings) {
                    SettingsSheet(config = config, onDismiss = { showSettings = false })
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Iris") },
                            actions = {
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                            // Prominent banner/button if Accessibility is disabled
                            if (!isAccessibilityEnabled) {
                                Card(
                                    modifier = Modifier.padding(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Accessibility Service Required",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "To detect hardware key events, please enable Iris in Accessibility Settings.",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = { openAccessibilitySettings() }) {
                                            Text("Enable in Settings")
                                        }
                                    }
                                }
                                Spacer(Modifier.height(24.dp))
                            }
                            else {
                                Text("Essential Key", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(16.dp))
                                Text(keyState, fontSize = 48.sp)
                            }
                        }
                    }
                }
            }
        }
    }

}
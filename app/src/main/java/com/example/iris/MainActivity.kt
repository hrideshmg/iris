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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.provider.Settings
import android.text.TextUtils
import com.example.iris.ui.theme.IrisTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

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

    private fun checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestNotifPermission.launch(permission)
            }
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

        checkAndRequestAudioPermission()
        checkAndRequestNotificationPermission()

        val config = SecureConfig(this)

        setContent {
            IrisTheme {
                var isAccessibilityEnabled by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var latest by remember { mutableStateOf<PttMessage?>(null) }

                LaunchedEffect(Unit) {
                    latest = MessageStore.load(this@MainActivity)
                }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            when (intent.action) {
                                KeySnifferService.ACTION_RESPONSE -> {
                                    latest = PttMessage(
                                        transcript = intent.getStringExtra(KeySnifferService.EXTRA_TRANSCRIPT) ?: "",
                                        response = intent.getStringExtra(KeySnifferService.EXTRA_RESPONSE) ?: "",
                                        timestamp = intent.getLongExtra(KeySnifferService.EXTRA_TIMESTAMP, System.currentTimeMillis())
                                    )
                                }
                            }
                        }
                    }
                    val filter = IntentFilter(KeySnifferService.ACTION_RESPONSE)
                    ContextCompat.registerReceiver(
                        this@MainActivity,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                    onDispose { unregisterReceiver(receiver) }
                }

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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        if (!isAccessibilityEnabled) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
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
                            }
                        }

                        else {
                            val msg = latest
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (msg == null) {
                                    Text(
                                        "Hold the Essential Key to talk",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    MessageItem(msg)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(msg: PttMessage) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val time = fmt.format(Date(msg.timestamp))

    Column(modifier = Modifier.fillMaxWidth()) {
        // User transcript bubble (right-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        msg.transcript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Assistant response bubble (left-aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    msg.response,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

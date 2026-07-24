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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.provider.Settings
import android.text.TextUtils
import com.example.iris.ui.theme.IrisTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The lifecycle of a single push-to-talk turn, as the screen understands it. */
private enum class Phase { READY, LISTENING, WORKING, DISCARDED, ERROR }

private data class UiStatus(val phase: Phase, val detail: String? = null)

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
                var status by remember { mutableStateOf(UiStatus(Phase.READY)) }

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
                                    status = UiStatus(Phase.READY)
                                }
                                KeySnifferService.ACTION_STATUS -> {
                                    val phase = when (intent.getStringExtra(KeySnifferService.EXTRA_STATUS)) {
                                        KeySnifferService.STATUS_RECORDING -> Phase.LISTENING
                                        KeySnifferService.STATUS_WORKING -> Phase.WORKING
                                        KeySnifferService.STATUS_DISCARDED -> Phase.DISCARDED
                                        KeySnifferService.STATUS_ERROR -> Phase.ERROR
                                        else -> Phase.READY
                                    }
                                    status = UiStatus(phase, intent.getStringExtra(KeySnifferService.EXTRA_DETAIL))
                                }
                            }
                        }
                    }
                    val filter = IntentFilter().apply {
                        addAction(KeySnifferService.ACTION_RESPONSE)
                        addAction(KeySnifferService.ACTION_STATUS)
                    }
                    ContextCompat.registerReceiver(
                        this@MainActivity,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                    onDispose { unregisterReceiver(receiver) }
                }

                // "Too short" is transient — settle back to ready on its own.
                LaunchedEffect(status) {
                    if (status.phase == Phase.DISCARDED) {
                        delay(1800)
                        status = UiStatus(Phase.READY)
                    }
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
                            title = {
                                Text(
                                    "iris",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            actions = {
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
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
                            SetupNeeded(
                                modifier = Modifier.weight(1f),
                                onOpenSettings = { openAccessibilitySettings() },
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                val msg = latest
                                if (msg == null) {
                                    EmptyState()
                                } else {
                                    Readout(msg)
                                }
                            }
                            StatusStrip(status)
                        }
                    }
                }
            }
        }
    }
}

/** The reply is the hero; the transcript is a quiet echo of the command above it. */
@Composable
private fun Readout(msg: PttMessage) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val time = remember(msg.timestamp) { fmt.format(Date(msg.timestamp)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // What you said — muted, accent-barred, secondary.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "YOU SAID",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    msg.transcript,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The reply — the loud, high-contrast answer.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "IRIS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                msg.response,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "NOTHING YET",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            "Hold the Essential Key and speak a command.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Persistent instrument readout of the device's live state. */
@Composable
private fun StatusStrip(status: UiStatus) {
    val live = Color(0xFFE5484D)
    val (label, dotColor) = when (status.phase) {
        Phase.READY -> "HOLD TO TALK" to MaterialTheme.colorScheme.onSurfaceVariant
        Phase.LISTENING -> "LISTENING" to live
        Phase.WORKING -> "WORKING" to MaterialTheme.colorScheme.primary
        Phase.DISCARDED -> "TOO SHORT — HOLD LONGER" to MaterialTheme.colorScheme.onSurfaceVariant
        Phase.ERROR -> (status.detail ?: "SOMETHING WENT WRONG").uppercase(Locale.getDefault()) to
            MaterialTheme.colorScheme.error
    }

    // Breathe the dot only while something is actively happening.
    val pulsing = status.phase == Phase.LISTENING || status.phase == Phase.WORKING
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val dotAlpha = if (pulsing) pulse else 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (status.phase == Phase.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SetupNeeded(modifier: Modifier = Modifier, onOpenSettings: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "SETUP REQUIRED",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Iris needs the accessibility service to hear the Essential Key.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenSettings) {
            Text("Enable in Settings")
        }
    }
}

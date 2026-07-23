package com.example.iris

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.iris.ui.theme.IrisTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IrisTheme {
                var keyState by remember { mutableStateOf("waiting…") }

                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            keyState = intent.getStringExtra("action") ?: "?"
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

                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Essential Key", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(androidx.compose.ui.unit.Dp(16f)))
                            Text(keyState, fontSize = 48.sp)
                        }
                    }
                }
            }
        }
    }
}

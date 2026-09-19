package com.enjirad.qrqueue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.enjirad.qrqueue.ui.QueueRoute
import com.enjirad.qrqueue.ui.theme.QrQueueTheme

/**
 * Single-activity entry point for QR Payment Queue.
 *
 * The whole UI is Jetpack Compose; this class only installs the theme and the
 * queue screen. Everything stays on-device — the app never automates banking
 * screens or performs a payment by itself (see AI_RULES.md).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QrQueueTheme {
                QueueRoute()
            }
        }
    }
}

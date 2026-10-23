package com.rez.notif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.rez.notif.ui.AppRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        // Catch SMS that arrived while the app was not running (for example right after a reboot).
        val repo = NotifApp.repo(this)
        if (repo.prefs.current.firstScanDone && hasSmsPermission()) {
            lifecycleScope.launch { runCatching { repo.syncSms() } }
        }
    }

    private fun hasSmsPermission() =
        checkSelfPermission(android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

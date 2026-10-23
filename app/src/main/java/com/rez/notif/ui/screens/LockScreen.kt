package com.rez.notif.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rez.notif.security.AppLock
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.theme.LocalGlass

/** Shown instead of the app while it is locked. Asks for the fingerprint right away. */
@Composable
fun LockScreen() {
    val g = LocalGlass.current
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) { activity?.let { AppLock.authenticate(it) } }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeonIcon(Icons.Rounded.Fingerprint, g.accent, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text("نوتیف قفله", style = MaterialTheme.typography.headlineSmall, color = g.text)
        Spacer(Modifier.height(24.dp))
        GlassButton("باز کردن", icon = Icons.Rounded.Fingerprint, onClick = { activity?.let { AppLock.authenticate(it) } })
    }
}

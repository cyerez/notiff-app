package com.rez.notif.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rez.notif.ui.components.GlassButton
import com.rez.notif.ui.components.GlassCard
import com.rez.notif.ui.components.NeonIcon
import com.rez.notif.ui.theme.LocalGlass
import kotlinx.coroutines.delay

object Permissions {
    fun sms(c: Context) = granted(c, Manifest.permission.READ_SMS) && granted(c, Manifest.permission.RECEIVE_SMS)
    fun notificationAccess(c: Context): Boolean {
        val enabled = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(c.packageName)
    }
    fun battery(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(c.packageName) == true

    private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun openNotificationAccess(c: Context) =
        c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** App info page, where Samsung/Android 13+ shows "Allow restricted settings" in the ⋮ menu. */
    fun openAppInfo(c: Context) = c.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    @android.annotation.SuppressLint("BatteryLife")
    fun askBattery(c: Context) = c.startActivity(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** First run: explains and asks for each permission. Status refreshes every second. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val g = LocalGlass.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }

    val smsOk = remember(tick) { Permissions.sms(context) }
    val notifOk = remember(tick) { Permissions.notificationAccess(context) }
    val batteryOk = remember(tick) { Permissions.battery(context) }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        NeonIcon(Icons.Rounded.NotificationsActive, g.accent, size = 84.dp)
        Spacer(Modifier.height(18.dp))
        Text("سلام rez", style = MaterialTheme.typography.headlineLarge, color = g.text)
        Text(
            "نوتیف همه پیامک‌ها و نوتیف‌هات رو مرتب می‌کنه. همه چیز فقط روی همین گوشی می‌مونه و هیچ چیزی به اینترنت فرستاده نمیشه.",
            style = MaterialTheme.typography.bodyLarge,
            color = g.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )

        StepCard(
            icon = Icons.Rounded.Sms,
            color = Color(0xFF34D399),
            title = "۱. دسترسی پیامک",
            text = "برای خوندن پیامک‌های قدیمی و جدید، و فهمیدن اینکه از کدوم سیم‌کارت اومدن.",
            done = smsOk,
            action = "اجازه بده",
            onAction = {
                smsLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ),
                )
            },
        )
        StepCard(
            icon = Icons.Rounded.NotificationsActive,
            color = g.accent,
            title = "۲. دسترسی نوتیف‌ها",
            text = "در صفحه‌ای که باز میشه «نوتیف» رو روشن کن. اگه خاکستری بود و روشن نشد: برو به اطلاعات اپ، منوی ⋮ بالا، و «Allow restricted settings» رو بزن، بعد دوباره امتحان کن.",
            done = notifOk,
            action = "باز کردن تنظیمات",
            onAction = { Permissions.openNotificationAccess(context) },
            secondary = "اطلاعات اپ",
            onSecondary = { Permissions.openAppInfo(context) },
        )
        StepCard(
            icon = Icons.Rounded.BatteryChargingFull,
            color = Color(0xFFFF8A3D),
            title = "۳. باتری (پیشنهادی)",
            text = "سامسونگ اپ‌ها رو در پس‌زمینه می‌خوابونه. با این گزینه نوتیفی از دست نمیره.",
            done = batteryOk,
            action = "اجازه بده",
            onAction = { Permissions.askBattery(context) },
        )

        Spacer(Modifier.height(12.dp))
        GlassButton(
            text = if (smsOk) "شروع اسکن" else "اول دسترسی پیامک لازمه",
            onClick = onDone,
            enabled = smsOk,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StepCard(
    icon: ImageVector,
    color: Color,
    title: String,
    text: String,
    done: Boolean,
    action: String,
    onAction: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    val g = LocalGlass.current
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 7.dp), glow = if (done) color else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(icon, color, size = 40.dp)
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = g.text, modifier = Modifier.weight(1f))
            if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF34D399), modifier = Modifier.size(28.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = g.textDim, modifier = Modifier.padding(vertical = 10.dp))
        if (!done) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(action, onAction)
                if (secondary != null) GlassButton(secondary, onSecondary, primary = false)
            }
        }
    }
}

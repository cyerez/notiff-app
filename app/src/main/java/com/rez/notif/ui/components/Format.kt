package com.rez.notif.ui.components

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rez.notif.classify.Category
import java.util.Date

/** ASCII digits -> Persian digits. */
fun fa(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) sb.append(if (c in '0'..'9') '۰' + (c - '0') else c)
    return sb.toString()
}

fun fa(n: Int): String = fa(n.toString())

private val persianLocale = ULocale("fa_IR@calendar=persian")
private val dayFormat = SimpleDateFormat("d MMMM", persianLocale)
private val fullFormat = SimpleDateFormat("yyyy/MM/dd  HH:mm", persianLocale)
private val timeFormat = SimpleDateFormat("HH:mm", persianLocale)
private val weekdayFormat = SimpleDateFormat("EEEE", persianLocale)

/** Short time for lists: "۵ دقیقه پیش", "۱۴:۳۰", "دیروز", "۳ آبان". */
fun shortTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ts
    return when {
        diff < 60_000 -> "همین الان"
        diff < 3_600_000 -> fa((diff / 60_000).toInt()) + " دقیقه پیش"
        diff < 86_400_000 && sameDay(ts, now) -> fa(timeFormat.format(Date(ts)))
        sameDay(ts, now - 86_400_000) -> "دیروز"
        else -> fa(dayFormat.format(Date(ts)))
    }
}

fun fullTime(ts: Long): String = fa(fullFormat.format(Date(ts)))
fun weekday(ts: Long): String = weekdayFormat.format(Date(ts))
fun today(): String = weekday(System.currentTimeMillis()) + "، " + fa(dayFormat.format(Date()))

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

val Category.icon: ImageVector
    get() = when (this) {
        Category.OTP -> Icons.Rounded.Key
        Category.BANK -> Icons.Rounded.AccountBalance
        Category.PERSONAL -> Icons.Rounded.Person
        Category.SOCIAL -> Icons.Rounded.Forum
        Category.EMAIL -> Icons.Rounded.Email
        Category.SHOPPING -> Icons.Rounded.ShoppingBag
        Category.OPERATOR -> Icons.Rounded.SimCard
        Category.SERVICES -> Icons.AutoMirrored.Rounded.ReceiptLong
        Category.APPS -> Icons.Rounded.Apps
        Category.SYSTEM -> Icons.Rounded.PhoneAndroid
        Category.ADS -> Icons.Rounded.Campaign
        Category.OTHER -> Icons.Rounded.MoreHoriz
    }

val Category.tint: Color get() = Color(color)

/** Gold used for the "valuable" flag. */
val ValuableColor = Color(0xFFFFC23D)

package com.rez.notif.ui.components

import com.rez.notif.i18n.t
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
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rez.notif.classify.Category
import com.rez.notif.i18n.I18n
import java.util.Date

/** ASCII digits -> Persian digits (only when the app is in Persian). */
fun fa(s: String): String {
    if (!I18n.isFa) return s
    val sb = StringBuilder(s.length)
    for (c in s) sb.append(if (c in '0'..'9') '۰' + (c - '0') else c)
    return sb.toString()
}

fun fa(n: Int): String = fa(n.toString())

private val persianLocale = ULocale("fa_IR@calendar=persian")
private val englishLocale = ULocale("en_US")
private val loc: ULocale get() = if (I18n.isFa) persianLocale else englishLocale
private val dayFormat get() = SimpleDateFormat(if (I18n.isFa) "d MMMM" else "MMM d", loc)
private val fullFormat get() = SimpleDateFormat(if (I18n.isFa) "yyyy/MM/dd  HH:mm" else "MMM d, yyyy  HH:mm", loc)
private val timeFormat get() = SimpleDateFormat("HH:mm", loc)
private val weekdayFormat get() = SimpleDateFormat("EEEE", loc)

/** Short time for lists: "۵ دقیقه پیش", "۱۴:۳۰", "دیروز", "۳ آبان". */
fun shortTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ts
    return when {
        diff < 60_000 -> t("همین الان")
        diff < 3_600_000 -> fa((diff / 60_000).toInt()) + t(" دقیقه پیش")
        diff < 86_400_000 && sameDay(ts, now) -> fa(timeFormat.format(Date(ts)))
        sameDay(ts, now - 86_400_000) -> t("دیروز")
        else -> fa(dayFormat.format(Date(ts)))
    }
}

fun fullTime(ts: Long): String = fa(fullFormat.format(Date(ts)))
fun weekday(ts: Long): String = weekdayFormat.format(Date(ts))
fun today(): String = weekday(System.currentTimeMillis()) + t("، ") + fa(dayFormat.format(Date()))

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

val Category.icon: ImageVector
    get() = when (this) {
        Category.OTP -> Icons.Rounded.Key
        Category.MISSED -> Icons.Rounded.PhoneMissed
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

/** Pink-red used for chosen people. */
val VipColor = Color(0xFFFF4D8D)

/** Gold used for the "valuable" flag. */
val ValuableColor = Color(0xFFFFC23D)

/** Rial → "۱۲۵٬۰۰۰ تومان". */
fun toman(rial: Long): String = fa(String.format(java.util.Locale.US, "%,d", rial / 10)) + t(" تومان")

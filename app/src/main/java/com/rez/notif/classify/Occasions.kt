package com.rez.notif.classify

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Occasions of the day for the calendar, the widget and the home screen, each with a cheeky one-liner ("تیکه")
 * about money, in the spirit of the app. Mourning days get no quip (and the home shows no joke on them).
 * Pure Kotlin, tested.
 *
 * Islamic dates use java.time's Hijri calendar (Umm al-Qura); Iran's official date can be a day off.
 */
object Occasions {
    data class Occasion(
        val name: String,
        val en: String,
        /** Official day off in Iran. */
        val holiday: Boolean = false,
        /** A sad religious day: no jokes. */
        val mourning: Boolean = false,
        val quip: String? = null,
    )

    private fun o(name: String, en: String, quip: String? = null, holiday: Boolean = false, mourning: Boolean = false) =
        Occasion(name, en, holiday, mourning, if (mourning) null else quip)

    /** Persian (solar) calendar: month to day to occasion. */
    private val SOLAR: Map<Pair<Int, Int>, Occasion> = mapOf(
        (1 to 1) to o("نوروز", "Nowruz", "عیدی گرفتی؟ قبل از خرج کردن یه بار بشمارش.", holiday = true),
        (1 to 2) to o("عید نوروز", "Nowruz", "عید دیدنی رایگانه؛ شیرینی‌ش نه.", holiday = true),
        (1 to 3) to o("عید نوروز", "Nowruz", "آجیل رو مشتی برندار، شمرده بردار!", holiday = true),
        (1 to 4) to o("عید نوروز", "Nowruz", "تعطیلات تموم میشه، قسط‌ها نه.", holiday = true),
        (1 to 12) to o("روز جمهوری اسلامی", "Islamic Republic Day", holiday = true),
        (1 to 13) to o("سیزده‌بدر", "Nature Day", "سبزه رو گره بزن، در کیف پولت رو هم.", holiday = true),
        (2 to 25) to o("روز بزرگداشت فردوسی", "Ferdowsi Day", "فردوسی سی سال رنج برد؛ تو هم سی روز برای این قسط."),
        (3 to 14) to o("رحلت امام خمینی", "Death of Khomeini", holiday = true, mourning = true),
        (3 to 15) to o("قیام ۱۵ خرداد", "15 Khordad uprising", holiday = true),
        (4 to 13) to o("جشن تیرگان", "Tirgan", "تیرگان یعنی آب‌بازی؛ قبض آب رو هم یادت باشه!"),
        (7 to 10) to o("جشن مهرگان", "Mehregan", "مهرگان مبارک؛ مهربونی رایگانه، از اون زیاد خرج کن."),
        (8 to 1) to o("روز آمار و برنامه‌ریزی", "Statistics Day", "روز آماره؛ گاوه همه‌ی خرج‌هات رو شمرده!"),
        (9 to 30) to o("شب یلدا", "Yalda Night", "یلدا بلندترین شب ساله؛ ولی تا آخر ماه بلندتره. انار رو دونه‌دونه بخور!"),
        (10 to 1) to o("روز اول زمستان", "First day of winter", "زمستون اومد؛ قبض گاز داره میاد. بخاری رو یه درجه کم کن."),
        (11 to 10) to o("جشن سده", "Sadeh", "جشن سده؛ آتیش روشن کن، نه آتیش به مالت."),
        (11 to 22) to o("پیروزی انقلاب اسلامی", "Revolution Day", holiday = true),
        (12 to 15) to o("روز درختکاری", "Arbor Day", "یه درخت بکار؛ پول که روی درخت نمی‌رویه، ولی سایه‌ش مجانیه."),
        (12 to 29) to o("ملی شدن صنعت نفت", "Oil Nationalization Day", "نفت ملی شد؛ بنزین موتور هنوز با کارت سوخته.", holiday = true),
    )

    /** Hijri (lunar) calendar: month to day to occasion. Month 2 day 30 is handled as "last day of Safar". */
    private val HIJRI: Map<Pair<Int, Int>, Occasion> = mapOf(
        (1 to 9) to o("تاسوعای حسینی", "Tasua", holiday = true, mourning = true),
        (1 to 10) to o("عاشورای حسینی", "Ashura", holiday = true, mourning = true),
        (2 to 20) to o("اربعین حسینی", "Arbaeen", holiday = true, mourning = true),
        (2 to 28) to o("رحلت پیامبر و شهادت امام حسن", "Death of the Prophet", holiday = true, mourning = true),
        (3 to 8) to o("شهادت امام حسن عسکری", "Martyrdom of Imam Askari", holiday = true, mourning = true),
        (3 to 17) to o("میلاد پیامبر و امام صادق", "Birth of the Prophet", "عید مبارک؛ شیرینی بخر، ولی یه جعبه نه سه تا!", holiday = true),
        (6 to 3) to o("شهادت حضرت فاطمه", "Martyrdom of Fatima", holiday = true, mourning = true),
        (6 to 20) to o("ولادت حضرت فاطمه، روز مادر", "Mother's Day", "روز مادر مبارک؛ یه زنگ بزن، رایگانه. یه گل هم بخر، می‌ارزه."),
        (7 to 13) to o("ولادت امام علی، روز پدر", "Father's Day", "روز پدر مبارک؛ هدیه از دل بخر، نه از کارت اعتباری.", holiday = true),
        (7 to 27) to o("مبعث", "Mab'ath", "مبعث مبارک؛ یه روز تعطیل، یه روز کم‌خرج.", holiday = true),
        (8 to 15) to o("نیمه شعبان", "Mid-Sha'ban", "نیمه شعبان مبارک؛ شربت نذری رایگانه، صف‌ش نه!", holiday = true),
        (9 to 21) to o("شهادت امام علی", "Martyrdom of Imam Ali", holiday = true, mourning = true),
        (10 to 1) to o("عید فطر", "Eid al-Fitr", "عید فطر مبارک؛ بعد یه ماه، شکم هم حساب و کتاب داره!", holiday = true),
        (10 to 2) to o("تعطیل به مناسبت عید فطر", "Eid al-Fitr", "دومین روز عید؛ رستوران‌ها پرن، آشپزخونه‌ی خودت خالی.", holiday = true),
        (10 to 25) to o("شهادت امام صادق", "Martyrdom of Imam Sadiq", holiday = true, mourning = true),
        (12 to 10) to o("عید قربان", "Eid al-Adha", "عید قربان مبارک؛ فقط بودجه‌ی ماه رو قربونی نکن.", holiday = true),
        (12 to 18) to o("عید غدیر", "Eid al-Ghadir", "غدیر مبارک؛ عیدی سیدها یادت نره!", holiday = true),
    )

    private val SAFAR_END = o("شهادت امام رضا", "Martyrdom of Imam Reza", holiday = true, mourning = true)

    /** Gregorian calendar: month to day. */
    private val GREGORIAN: Map<Pair<Int, Int>, Occasion> = mapOf(
        (1 to 1) to o("سال نو میلادی", "New Year", "سال نو میلادی مبارک؛ قسط‌ها ولی هنوز شمسی‌ان."),
        (2 to 14) to o("ولنتاین", "Valentine's Day", "عشق رایگانه؛ گل رز نه. یه شاخه کافیه!"),
        (12 to 25) to o("کریسمس", "Christmas", "کریسمس مبارک؛ بابانوئل قسط‌هات رو نمیده."),
    )

    /** Every occasion on [day] (epoch day). */
    fun on(day: Long): List<Occasion> {
        val out = ArrayList<Occasion>(2)
        val j = Jalali.ofEpochDay(day)
        SOLAR[j.m to j.d]?.let { out += it }
        // Chaharshanbe Suri: the evening before the last Wednesday of the year.
        if (j.m == 12 && Jalali.weekday(day) == 3 && Jalali.ofEpochDay(day + 8).m == 1) {
            out += o("چهارشنبه‌سوری", "Chaharshanbe Suri", "از روی آتیش بپر، نه از روی بودجه!")
        }
        runCatching {
            val h = HijrahDate.from(LocalDate.ofEpochDay(day))
            val hm = h.get(ChronoField.MONTH_OF_YEAR)
            val hd = h.get(ChronoField.DAY_OF_MONTH)
            HIJRI[hm to hd]?.let { out += it }
            if (hm == 2 && hd == h.lengthOfMonth()) out += SAFAR_END
        }
        val g = LocalDate.ofEpochDay(day)
        GREGORIAN[g.monthValue to g.dayOfMonth]?.let { out += it }
        // Black Friday: the day after the 4th Thursday of November.
        if (g.monthValue == 11 && g.dayOfWeek.value == 5 && g.dayOfMonth in 23..29) {
            out += o("بلک فرایدی", "Black Friday", "بلک فرایدی: تخفیف یعنی کمتر خرج کنی، نه بیشتر بخری!")
        }
        return out
    }

    fun isHoliday(day: Long): Boolean = on(day).any { it.holiday }
    fun isMourning(day: Long): Boolean = on(day).any { it.mourning }

    /** The quip for today's occasion, if any (none on mourning days). */
    fun quip(day: Long): String? = if (isMourning(day)) null else on(day).firstNotNullOfOrNull { it.quip }
}

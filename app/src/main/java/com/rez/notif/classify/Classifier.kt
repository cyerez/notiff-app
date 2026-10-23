package com.rez.notif.classify

/**
 * The rule-based "first brain" of the app. Pure Kotlin, no Android code, so it is easy to test
 * and could move to a shared module later.
 *
 * Order matters: the first rule that matches wins. User overrides (sender -> category) always win.
 */
object Classifier {

    data class Input(
        val isSms: Boolean,
        val sender: String,
        val title: String,
        val body: String,
        val packageName: String = "",
    )

    data class Result(val category: Category, val isValuable: Boolean)

    fun classify(input: Input, senderOverrides: Map<String, String> = emptyMap()): Result {
        val text = normalize("${input.title} ${input.body}")
        val sender = normalize(input.sender)
        val valuable = looksValuable(text, input)

        senderOverrides[senderKey(input)]?.let { return Result(Category.of(it), valuable) }

        val category = if (input.isSms) classifySms(sender, text) else classifyNotification(input.packageName, text)
        return Result(category, valuable && category != Category.ADS)
    }

    /** Key used for per-sender learning: SMS sender number, or notification app package. */
    fun senderKey(input: Input): String =
        if (input.isSms) "sms:" + normalize(input.sender).replace(" ", "") else "app:" + input.packageName

    // ---------- SMS ----------

    private fun classifySms(sender: String, text: String): Category {
        if (isMissedCall(text)) return Category.MISSED
        if (isOtp(text)) return Category.OTP
        if (isBank(sender, text)) return Category.BANK
        if (isAd(sender, text)) return Category.ADS
        if (OPERATOR_WORDS.any { it in sender } || OPERATOR_WORDS.any { it in text } && OPERATOR_TEXT_HINTS.any { it in text }) {
            return Category.OPERATOR
        }
        if (SHOPPING_WORDS.any { it in text || it in sender }) return Category.SHOPPING
        if (SERVICE_WORDS.any { it in text || it in sender }) return Category.SERVICES
        if (isPhoneNumber(sender)) return Category.PERSONAL
        return Category.OTHER
    }

    // ---------- Notifications ----------

    private fun classifyNotification(pkg: String, text: String): Category {
        val p = pkg.lowercase()
        if (DIALER_PACKAGES.any { p.startsWith(it) }) return if (isMissedCall(text)) Category.MISSED else Category.SYSTEM
        if (isOtp(text)) return Category.OTP
        if (SOCIAL_PACKAGES.any { p.startsWith(it) }) return Category.SOCIAL
        if (EMAIL_PACKAGES.any { p.startsWith(it) }) return Category.EMAIL
        if (BANK_PACKAGES.any { p.contains(it) } || isBank("", text)) return Category.BANK
        if (SHOPPING_PACKAGES.any { p.contains(it) }) return Category.SHOPPING
        if (OPERATOR_PACKAGES.any { p.contains(it) }) return Category.OPERATOR
        if (SYSTEM_PACKAGES.any { p == it || p.startsWith("$it.") }) return Category.SYSTEM
        if (AD_WORDS.count { it in text } >= 2) return Category.ADS
        return Category.APPS
    }

    // ---------- Rules ----------

    private val MISSED_WORDS = listOf(
        "تماس از دست رفته", "تماس بی پاسخ", "تماس بیپاسخ", "تماس پاسخ داده نشده", "با شما تماس گرفت",
        "تماس ناموفق", "missed call", "missed calls", "you missed", "tried to call", "called you",
    )

    fun isMissedCall(text: String): Boolean {
        val t = normalize(text)
        return MISSED_WORDS.any { it in t }
    }

    private val DIALER_PACKAGES = listOf(
        "com.samsung.android.dialer", "com.samsung.android.incallui", "com.google.android.dialer",
        "com.android.dialer", "com.android.server.telecom", "com.android.phone",
    )

    private val OTP_WORDS = listOf(
        "کد", "رمز", "code", "otp", "password", "verification",
    )
    private val OTP_NUMBER = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")

    fun isOtp(text: String): Boolean {
        val t = normalize(text)
        return OTP_WORDS.any { it in t } && OTP_NUMBER.containsMatchIn(t) &&
            !t.contains("تخفیف") && !t.contains("لغو11") && !t.contains("promo")
    }

    private val BANK_SENDERS = listOf(
        "bank", "بانک", "melli", "mellat", "saman", "pasargad", "tejarat", "sepah", "refah", "keshavarzi",
        "ayandeh", "parsian", "resalat", "maskan", "sina", "eghtesad", "blu", "saderat", "post bank", "shahr",
        "dey", "karafarin", "middle east", "mehr",
    )
    private val BANK_WORDS = listOf("مانده", "برداشت", "واریز", "انتقال وجه", "حساب", "کارت", "موجودی", "تراکنش")

    private fun isBank(sender: String, text: String): Boolean {
        if (BANK_SENDERS.any { it in sender }) return true
        val hits = BANK_WORDS.count { it in text }
        return hits >= 2 || ("بانک" in text && hits >= 1)
    }

    /** Iranian bulk-SMS lines usually start with these prefixes. */
    private val AD_SENDER_PREFIXES = listOf("1000", "2000", "3000", "5000", "50001", "50002", "9821", "+9810", "+9820", "+9830", "+9850")
    private val AD_WORDS = listOf(
        "لغو11", "لغو 11", "لغو", "تخفیف", "جشنواره", "حراج", "فرصت", "ویژه", "قرعه", "ارسال عدد", "عدد 1",
        "ثبت نام", "اشتراک", "کلیک", "http", "www.", ".ir", "بزرگترین", "فقط تا", "پیشنهاد",
    )

    private fun isAd(sender: String, text: String): Boolean {
        val s = sender.replace(" ", "")
        if (AD_SENDER_PREFIXES.any { s.startsWith(it) } && s.length >= 8) return true
        if ("لغو11" in text.replace(" ", "")) return true
        return AD_WORDS.count { it in text } >= 3
    }

    private val OPERATOR_WORDS = listOf("mci", "irancell", "rightel", "همراه اول", "ایرانسل", "رایتل", "همراه من")
    private val OPERATOR_TEXT_HINTS = listOf("بسته", "شارژ", "اعتبار", "مگابایت", "گیگابایت", "سیم کارت", "دقیقه")

    private val SHOPPING_WORDS = listOf(
        "digikala", "دیجی کالا", "دیجیکالا", "snapp", "اسنپ", "tapsi", "تپسی", "سفارش", "مرسوله", "ارسال شد",
        "تحویل", "فاکتور", "پیک", "باسلام", "ترب", "علی بابا", "alibaba",
    )
    private val SERVICE_WORDS = listOf(
        "قبض", "یارانه", "ثنا", "عدالت", "دولت", "مالیات", "بیمه", "پلیس", "سامانه", "نوبت", "شهرداری", "تامین اجتماعی",
        "my.gov", "mygov", "سازمان",
    )

    private val PHONE = Regex("^(\\+98|0098|98|0)?9[0-9]{9}$")
    private fun isPhoneNumber(sender: String): Boolean = PHONE.matches(sender.replace(" ", "").replace("-", ""))

    private val VALUABLE_WORDS = listOf(
        "رایگان", "هدیه", "مجانی", "کارت هدیه", "کد تخفیف", "بن خرید", "جایزه", "free", "gift", "voucher",
        "giveaway", "coupon", "promo code", "reward", "claim", "% off", "off your",
    )

    private fun looksValuable(text: String, input: Input): Boolean {
        if (VALUABLE_WORDS.none { it in text }) return false
        // A "free gift" from an ad line is not valuable; from email or a known app it may be.
        return !input.isSms || !AD_SENDER_PREFIXES.any { input.sender.replace(" ", "").startsWith(it) }
    }

    private val SOCIAL_PACKAGES = listOf(
        "org.telegram", "org.thunderdog.challegram", "com.whatsapp", "com.instagram", "ir.eitaa", "ir.nasim",
        "ir.resaneh1.iptv", "mobi.mmdt", "com.discord", "com.twitter", "com.linkedin", "com.facebook",
        "com.snapchat", "com.zhiliaoapp.musically", "com.reddit", "com.viber", "com.skype",
    )
    private val EMAIL_PACKAGES = listOf(
        "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email", "ch.protonmail",
        "com.yahoo.mobile.client.android.mail", "me.bluemail", "com.fsck.k9", "net.thunderbird",
    )
    private val BANK_PACKAGES = listOf("bank", "ir.mservices", "com.bpm", "ir.bmi", "com.sadad", "ir.hafhashtad", "com.isc", "ir.asanpardakht", "com.farazpardazan", "ir.pec")
    private val SHOPPING_PACKAGES = listOf("digikala", "snapp", "tapsi", "basalam", "torob", "alibaba", "divar", "sheypoor", "amazon", "aliexpress", "temu")
    private val OPERATOR_PACKAGES = listOf("ir.mci", "com.irancell", "ir.rightel")
    private val SYSTEM_PACKAGES = listOf(
        "android", "com.android.systemui", "com.android.settings", "com.android.vending", "com.google.android.gms",
        "com.samsung.android.lool", "com.samsung.android.app.smartcapture", "com.sec.android.app.samsungapps",
        "com.samsung.android.scloud", "com.android.providers.downloads",
    )

    /** Persian/Arabic digits -> ASCII, Arabic letters -> Persian, lowercase, collapse spaces. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    'ة' -> 'ه'
                    'أ', 'إ' -> 'ا'
                    '‌' -> ' '
                    'ـ' -> null
                    else -> c
                } ?: continue,
            )
        }
        return sb.toString().lowercase().replace("ﺗ", "ت").replace("تأیید", "تایید").replace(Regex("\\s+"), " ").trim()
    }
}

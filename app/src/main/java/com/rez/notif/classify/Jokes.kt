package com.rez.notif.classify

/**
 * The jokes of "شاخ گاو". All of them live inside the app (it has no internet); new ones come with updates.
 * Pure Kotlin. Rule: never joke at serious moments (very low balance, bounced cheque); the UI asks [mood] first.
 */
object Jokes {
    /** One a day, at the top of the home screen. */
    val DAILY = listOf(
        "پولو زدی به شاخ گاو؟",
        "پول علف خرس نیست ها!",
        "دخلت با خرجت می‌خونه؟ امروز یه گوش بده.",
        "قطره قطره جمع گردد، وانگهی دریا شود. حتی تومن تومن.",
        "امروز روز خوبیه برای خرج نکردن.",
        "کیف پولت امروز یه نفس راحت کشید؟",
        "قسط‌ها خوابن؟ آروم راه برو بیدار نشن.",
        "گاوه امروز سرحاله. تو چطور؟",
        "پول خوش‌قدم کسیه که مانده‌ش رو نگاه می‌کنه.",
        "یه چای بخور، بعد خرید کن.",
        "سبد خرید پر، کیف پول خالی. مراقب باش.",
        "شاخ گاو سفته. تو هم سفت بگیر.",
        "آخر ماه نزدیک‌تر از اونیه که فکر می‌کنی.",
        "اول ماه همه شاهن، آخر ماه همه فیلسوف.",
        "هر تومنی که امروز نمیدی، فردا بهت سلام می‌کنه.",
        "تخفیف یعنی کمتر خرج کردن، نه بیشتر خریدن.",
        "گاوه نشخوار می‌کنه، تو حساب کتاب.",
        "پول درخت نیست، ولی میشه کاشتش.",
        "اسنپ یا پیاده؟ پاهات رایگانن.",
        "امروز یه خرج رو بنداز فردا. شاید دیگه لازم نشد.",
        "این ماه کم‌خرج باشی، ماه بعد خوش‌خرج.",
        "قلکت گرسنه‌ست. یه چیزی بده بخوره.",
        "خرید از روی دلتنگی ممنوع!",
        "کیف پول هم دل داره.",
        "کارت بانکی تو جیب، عقل تو سر.",
        "سالی که نکوست از بهارش پیداست، ماهی که نکوست از دهمش.",
        "حساب حسابه، کاکا برادر.",
        "دستت به خرید نره، دلت به پس‌انداز.",
        "صبر کن، ارزون‌تر میشه. شاید.",
        "مانده‌ت رو دیدی؟ ما دیدیم. هیچی نمی‌گیم.",
        "یه قهوه‌ی خونگی، یه قدم به پولدار شدن.",
        "پول که جمع شد، شاخ گاو رو هم می‌شکنه.",
        "امروز چیزی لازم نداری. باور کن.",
        "سه تا چیز رو پس بده: سبد خرید، سبد خرید، سبد خرید.",
        "ارزون‌ترین خرید اونیه که نکردی.",
        "حقوق اومد؟ آروم آروم بیا پایین.",
        "زیاد دیدی کم نیار، کم دیدی زیاد نخر.",
        "بیا امروز رو بی‌تراکنش تموم کنیم.",
        "گاوه از دیروز به تو افتخار می‌کنه.",
        "هر روز یه کم. آخر ماه یه عالمه.",
        "نخواه هرچی دیدی. چشمات مفتن، کارتت نه.",
        "پول خرد هم پوله ها.",
        "حراج دلیل نیست، بهونه‌ست.",
        "امروز یه دنگ عقب‌افتاده رو صاف کن.",
        "پول قرضی رو یادت نره، خودشون یادشون میره.",
        "خرج امروز، غصه‌ی فردا. انتخاب با خودته.",
        "دل به دریا بزن، ولی کارت به کارت نه.",
        "یه نگاه به قسط‌ها بنداز، بعد به ویترین‌ها.",
        "مغازه‌ها فرار نمی‌کنن. پولت ولی چرا.",
        "از قدیم گفتن: کم بخور، همیشه بخور.",
        "اگه شک داری بخری یا نه، نخر.",
        "این هفته یه چیز رو تعمیر کن به‌جای عوض کردن.",
        "گاوه یه سکه پیدا کرد. تو هم بگرد.",
        "ماه پیش یادته؟ این ماه بهتر میشه.",
        "خرج دسته‌بندی‌نشده مثل جوراب لنگه‌به‌لنگه‌ست.",
        "پولت رو بشمار، قبل از اینکه دیگران بشمارن.",
        "هوس رو یه روز معطل کن. معمولاً میره.",
        "قبض‌ها مهمون ناخونده‌ان. جاشون رو آماده کن.",
        "یه روز بی‌خرید، یه ستاره برای تو.",
        "بیست تومن اینجا، بیست تومن اونجا. یهو یه میلیون.",
        "چک بی‌محل نکش، چایی بی‌قند بخور.",
        "تو برنامه بریز، پول خودش میاد. خب، شاید.",
        "دخل بیشتر یا خرج کمتر؟ دومی دست خودته.",
        "امروز شاخ گاو رو نگه داشتی. آفرین!",
        "پس‌انداز یعنی یه هدیه به خودِ آینده‌ت.",
        "کارتت رو بذار تو کیف، کیف رو بذار تو کشو.",
        "آدم عاقل اول حساب می‌کنه، بعد حساب می‌کنه.",
        "دیگه هیچی نمی‌گیم. خودت می‌دونی.",
    )

    /** After one big spending. */
    val BIG_SPEND = listOf(
        "پول علف خرس نیست ها!",
        "این یکی گنده بود. حداقل خوش بگذره!",
        "گاوه یه لحظه سکسکه کرد.",
        "اوه! کیف پولت یه آه کشید.",
    )

    /** Balance getting low (but not alarming). */
    val LOW = listOf(
        "دخلت با خرجت نمی‌خونه.",
        "ته‌ش داره پیدا میشه. یواش‌تر!",
        "گاوه نگرانه. یه کم مراقب باش.",
    )

    /** Spent more than this month's plan: a gentle tease, not a scolding. */
    val OVER = listOf(
        "دخلت با خرجت نمی‌خونه؛ یه کم یواش‌تر!",
        "این ماه از برنامه جلو زدی. دنده رو سبک کن.",
        "گاوه یه نگاه به برنامه‌ت انداخت و یه آه کشید.",
        "بودجه‌ی ماه تموم شد، ماه هنوز نه!",
        "از اینجا به بعد هر خرید رو دو بار فکر کن.",
        "پول علف خرس نیست ها! بقیه‌ی ماه رو مراقب باش.",
        "قلک پسته‌ای داره نگاهت می‌کنه...",
    )

    /** Empty lists. */
    val EMPTY_SPENDING = listOf(
        "هنوز یه قرون هم خرج نکردی، آفرین!",
        "اینجا خلوته. گاوه داره نشخوار می‌کنه.",
    )
    val EMPTY_SORTED = listOf(
        "همه‌چی دسته داره. گاوه راضیه.",
        "چیزی نمونده که بپرسم!",
    )

    enum class Mood { JOKE, SERIOUS }

    /** Below this real balance (toman) the app stops joking: money is really tight. */
    const val TIGHT_TOMAN = 300_000L

    /**
     * Serious only when real money is near zero ([totalToman], the fresh balance of all accounts) or on a mourning day.
     * Being over the month's plan is not serious: then the jokes tease gently ([OVER]).
     */
    fun mood(totalToman: Long?, mourning: Boolean = false): Mood =
        if (mourning || (totalToman != null && totalToman in 0 until TIGHT_TOMAN)) Mood.SERIOUS else Mood.JOKE

    /**
     * The line under the total on the home screen, or null for none.
     * Order: today's occasion quip, a big spending today, over the plan, a low daily allowance, the joke of the day.
     */
    fun forHome(totalToman: Long?, perDayToman: Long?, leftToman: Long?, bigTodayRial: Long, epochDay: Long, occasionQuip: String?, mourning: Boolean): String? {
        if (mood(totalToman, mourning) == Mood.SERIOUS) return null
        return when {
            occasionQuip != null -> occasionQuip
            bigTodayRial >= 10_000_000 -> pick(BIG_SPEND, epochDay)
            leftToman != null && leftToman < 0 -> pick(OVER, epochDay)
            isLow(perDayToman) || (perDayToman != null && perDayToman in 0 until 50_000) -> pick(LOW, epochDay)
            else -> ofDay(epochDay)
        }
    }

    /** Low but still fine for a joke. */
    fun isLow(perDayToman: Long?): Boolean = perDayToman != null && perDayToman in 50_000 until 150_000

    /** The joke of a day. [epochDay] = days since 1970 (local). Goes through the whole list before repeating. */
    fun ofDay(epochDay: Long): String = DAILY[Math.floorMod(epochDay * 37, DAILY.size.toLong()).toInt()]

    fun pick(list: List<String>, seed: Long): String = list[Math.floorMod(seed, list.size.toLong()).toInt()]
}

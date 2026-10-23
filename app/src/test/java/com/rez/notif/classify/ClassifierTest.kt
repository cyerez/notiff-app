package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {

    private fun sms(sender: String, body: String) =
        Classifier.classify(Classifier.Input(isSms = true, sender = sender, title = "", body = body))

    private fun notif(pkg: String, title: String, body: String) =
        Classifier.classify(Classifier.Input(isSms = false, sender = title, title = title, body = body, packageName = pkg))

    @Test fun otpPersian() = assertEquals(Category.OTP, sms("Digikala", "کد تایید شما: ۴۸۲۹۱۵").category)

    @Test fun otpBankDynamicPassword() = assertEquals(Category.OTP, sms("+9820001234", "رمز پویا: 739201 مبلغ 250,000 ریال").category)

    @Test fun bankTransaction() =
        assertEquals(Category.BANK, sms("Bank Mellat", "برداشت از حساب 1234\nمبلغ: 120,000\nمانده: 3,400,000").category)

    @Test fun bankByWords() =
        assertEquals(Category.BANK, sms("98700", "واریز به حساب شما\nمانده: 1,000,000").category)

    @Test fun adByLine() = assertEquals(Category.ADS, sms("30001234567", "سلام دوست عزیز").category)

    @Test fun adByWords() =
        assertEquals(Category.ADS, sms("Shop", "جشنواره بزرگ تخفیف! فقط تا جمعه. لغو11").category)

    @Test fun personal() = assertEquals(Category.PERSONAL, sms("+989121234567", "سلام کجایی؟").category)

    @Test fun personalLocal() = assertEquals(Category.PERSONAL, sms("09121234567", "رسیدم").category)

    @Test fun operator() = assertEquals(Category.OPERATOR, sms("MCI", "بسته اینترنت شما فعال شد").category)

    @Test fun shopping() = assertEquals(Category.SHOPPING, sms("Digikala", "سفارش شما ارسال شد").category)

    @Test fun services() = assertEquals(Category.SERVICES, sms("Sana", "قبض برق شما صادر شد").category)

    @Test fun telegram() = assertEquals(Category.SOCIAL, notif("org.telegram.messenger", "Ali", "hi").category)

    @Test fun gmailValuable() {
        val r = notif("com.google.android.gm", "Steam", "Free game: claim your gift today")
        assertEquals(Category.EMAIL, r.category)
        assertTrue(r.isValuable)
    }

    @Test fun adNotValuable() = assertFalse(sms("30001234567", "هدیه رایگان برای شما").isValuable)

    @Test fun bluetoothIsNotBank() = assertEquals(Category.SYSTEM, notif("android", "Bluetooth", "Connected").category)

    @Test fun overrideWins() {
        val input = Classifier.Input(true, "30001234567", "", "سلام")
        val r = Classifier.classify(input, mapOf(Classifier.senderKey(input) to Category.PERSONAL.id))
        assertEquals(Category.PERSONAL, r.category)
    }

    @Test fun normalizeDigits() = assertEquals("کد 123", Classifier.normalize("كد ۱۲۳"))
}

class AnalyzerTest {
    @Test fun senderMajorityFillsGaps() {
        val msgs = listOf(
            Analyzer.Msg("1", "Shop", "a", Category.ADS),
            Analyzer.Msg("2", "Shop", "b", Category.ADS),
            Analyzer.Msg("3", "Shop", "c", Category.ADS),
            Analyzer.Msg("4", "Shop", "d", Category.OTHER),
        )
        assertEquals(1, Analyzer.smoothBySender(msgs))
        assertEquals(Category.ADS, msgs[3].category)
    }

    @Test fun suggestionsSortedAndRecommended() {
        val msgs = (1..10).map { Analyzer.Msg("b$it", "Bank", "x$it", Category.BANK) } +
            (1..5).map { Analyzer.Msg("a$it", "Ad", "y$it", Category.ADS) }
        val s = Analyzer.suggest(msgs)
        assertEquals(Category.BANK, s.first().category)
        assertTrue(s.first().recommended)
        assertFalse(s.first { it.category == Category.ADS }.recommended)
    }
}

class MissedCallTest {
    @Test fun operatorMissedCallSms() {
        val r = Classifier.classify(Classifier.Input(true, "MCI", "", "شما یک تماس از دست رفته از 09121234567 دارید"))
        assertEquals(Category.MISSED, r.category)
    }

    @Test fun dialerMissedCallNotification() {
        val r = Classifier.classify(Classifier.Input(false, "", "Missed call", "Ali", "com.samsung.android.dialer"))
        assertEquals(Category.MISSED, r.category)
    }
}

class VipMatcherTest {
    private val people = listOf(
        VipPerson(1, "مامان", listOf("9121234567")),
        VipPerson(2, "Sara", emptyList()),
    )

    @Test fun keyNormalizesFormats() {
        assertEquals("9121234567", VipMatcher.key("+98 912 123 4567"))
        assertEquals("9121234567", VipMatcher.key("۰۹۱۲۱۲۳۴۵۶۷"))
        assertEquals(null, VipMatcher.key("MCI"))
    }

    @Test fun matchesSmsSender() = assertEquals(1L, VipMatcher.match(people, true, "+989121234567", "", "سلام")?.id)

    @Test fun matchesNumberInsideText() =
        assertEquals(1L, VipMatcher.match(people, true, "MCI", "", "تماس از دست رفته از ۰۹۱۲ ۱۲۳ ۴۵۶۷")?.id)

    @Test fun matchesNotificationTitleName() = assertEquals(2L, VipMatcher.match(people, false, "", "Sara", "hi")?.id)

    @Test fun noFalseMatch() = assertEquals(null, VipMatcher.match(people, false, "", "Reza", "hello Sarah?")?.id)
}

class CodeExtractTest {
    @Test fun bankDynamicPassword() =
        assertEquals("482913", Classifier.extractCode("بانک ملت\nرمز پویا: ۴۸۲۹۱۳\nمبلغ: 1,250,000 ریال\nزمان اعتبار: 2 دقیقه"))

    @Test fun amountBeforeCodeIsSkipped() =
        assertEquals("5531", Classifier.extractCode("خرید 3,400,000 ریال. کد تایید شما 5531 است"))

    @Test fun englishCode() = assertEquals("774120", Classifier.extractCode("Your verification code is 774120"))

    @Test fun codeBeforeWord() = assertEquals("90817", Classifier.extractCode("90817 is your Telegram code"))

    @Test fun notOtp() = assertEquals(null, Classifier.extractCode("سلام فردا ساعت 1830 میام"))
}

class PriorityTest {
    private fun p(c: Category, vip: Boolean = false, read: Boolean = false, hours: Long = 0) =
        Priority.score(Priority.Input(c, vip, false, false, read, hours * 3_600_000L))

    @Test fun vipBeatsEverything() = assertTrue(p(Category.APPS, vip = true) > p(Category.BANK))
    @Test fun missedCallIsImportant() = assertTrue(p(Category.MISSED) >= Priority.IMPORTANT)

    @Test fun questionInChatIsImportant() {
        assertTrue(Priority.isQuestion("فردا میای؟"))
        assertTrue(!Priority.isQuestion("باشه ممنون"))
        val chat = Priority.score(Priority.Input(Category.SOCIAL, false, false, false, false, 3_600_000L, isQuestion = true))
        assertTrue(chat >= Priority.IMPORTANT)
    }
    @Test fun adsAreNever() = assertEquals(0, p(Category.ADS))
    @Test fun oldThingsFade() = assertTrue(p(Category.PERSONAL, hours = 30) < p(Category.PERSONAL))
}

class LearnerTest {
    private val train = (1..12).map { "سفارش شما ارسال شد پیگیری مرسوله پست $it" to Category.SHOPPING } +
        (1..12).map { "جلسه فردا با تیم ساعت $it در دفتر" to Category.PERSONAL } +
        (1..12).map { "قبض گاز شما صادر شد مبلغ $it" to Category.SERVICES }

    @Test fun learnsWords() {
        val m = Learner.train(train)
        assertEquals(Category.SHOPPING, m.guess("مرسوله شما توسط پست ارسال شد")?.category)
        assertEquals(Category.PERSONAL, m.guess("فردا جلسه داریم با تیم")?.category)
    }

    @Test fun unsureReturnsNull() {
        val m = Learner.train(train)
        assertEquals(null, m.guess("هوا امروز خیلی خوبه"))
    }

    @Test fun tooFewExamplesAreIgnored() {
        val m = Learner.train(listOf("یک دو سه" to Category.BANK))
        assertEquals(null, m.guess("یک دو سه"))
    }
}

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

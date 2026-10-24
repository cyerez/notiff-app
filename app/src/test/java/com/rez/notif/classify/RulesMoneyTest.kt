package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesMoneyTest {
    private fun input(text: String, sender: String = "Ali", pkg: String = "org.telegram.messenger", cat: String = "social", hour: Int = 12, sms: Boolean = false) =
        Rules.Input(sms, pkg, sender, text, cat, hour)

    @Test fun emptyRuleMatchesAll() = assertTrue(Rules.matches(Rule(), input("hi")))

    @Test fun disabledNeverMatches() = assertFalse(Rules.matches(Rule(enabled = false), input("hi")))

    @Test fun wordsMatchPersianVariants() {
        val r = Rule(words = listOf("فوری"))
        assertTrue(Rules.matches(r, input("لطفا فوري جواب بده")))
        assertFalse(Rules.matches(r, input("سلام")))
    }

    @Test fun regexRule() {
        val r = Rule(words = listOf("order\\s+#\\d+"), regex = true)
        assertTrue(Rules.matches(r, input("Your Order #123 shipped")))
        assertFalse(Rules.matches(Rule(words = listOf("(("), regex = true), input("((")))
    }

    @Test fun appsAndSms() {
        assertTrue(Rules.matches(Rule(apps = setOf("sms")), input("x", sms = true, pkg = "")))
        assertFalse(Rules.matches(Rule(apps = setOf("sms")), input("x")))
        assertTrue(Rules.matches(Rule(apps = setOf("org.telegram.messenger")), input("x")))
    }

    @Test fun hoursWrapMidnight() {
        assertTrue(Rules.inHours(23, 7, 2))
        assertTrue(Rules.inHours(23, 7, 23))
        assertFalse(Rules.inHours(23, 7, 7))
        assertFalse(Rules.inHours(9, 17, 18))
        assertTrue(Rules.inHours(-1, -1, 5))
    }

    @Test fun senderAndCategory() {
        val r = Rule(sender = "گروه خانواده", categories = setOf("social"))
        assertTrue(Rules.matches(r, input("x", sender = "گروه خانواده: مامان")))
        assertFalse(Rules.matches(r, input("x", sender = "گروه خانواده", cat = "bank")))
    }

    @Test fun firstWinsInOrder() {
        val a = Rule(id = 1, words = listOf("x"), action = RuleAction.ALERT)
        val b = Rule(id = 2, action = RuleAction.QUIET)
        assertEquals(1L, Rules.first(listOf(a, b), input("x"))?.id)
        assertEquals(2L, Rules.first(listOf(a, b), input("y"))?.id)
    }

    @Test fun codecRoundTrip() {
        Rules.templates.forEach { t ->
            val r = t.copy(id = 7, apps = setOf("sms", "a.b"), target = "bank", action = RuleAction.MOVE)
            assertEquals(r, Rules.decode(7, Rules.encode(r)))
        }
        assertNull(Rules.decode(1, "garbage"))
    }

    @Test fun moneyWithdrawalAndBalance() {
        val t = Money.parse("بانک ملت\nبرداشت: ۱,۲۵۰,۰۰۰\nحساب ۱۲۳۴۵۶۷۸۹۰\nمانده: ۱۲,۳۴۵,۶۷۸\n۰۸۰۱-۱۲:۳۰")!!
        assertEquals(1_250_000L, t.out)
        assertEquals(0L, t.inn)
        assertEquals(12_345_678L, t.balance)
    }

    @Test fun moneyDeposit() {
        val t = Money.parse("واریز به حساب 0101234567 مبلغ 5,000,000 ریال مانده 7,000,000")!!
        assertEquals(5_000_000L, t.inn)
        assertEquals(7_000_000L, t.balance)
    }

    @Test fun moneySigned() {
        val t = Money.parse("کارت 6037-99**-1234\n-320,000\n1402/08/01-12:30\nمانده:900,000")!!
        assertEquals(320_000L, t.out)
        assertEquals(900_000L, t.balance)
    }

    @Test fun notMoney() = assertNull(Money.parse("کد تایید شما 12345 است"))

    @Test fun dateIsNotACode() {
        assertEquals("58213", Classifier.extractCode("رمز یکبار مصرف\n58213\nتاریخ 1405-08-02 09:00"))
        assertNull(Classifier.extractCode("کد تایید ورود شما\n۱۴۰۵ آبان"))
    }
}

class MoneySanityTest {
    @Test fun accountNumberIsNotMoney() {
        val t = Money.parse("برداشت از حساب 0101234567890\nمانده: 5,000,000")!!
        assertEquals(0L, t.out)
        assertEquals(5_000_000L, t.balance)
    }

    @Test fun plainNumberWithRial() = assertEquals(250_000L, Money.parse("خرید 250000 ریال")!!.out)
}

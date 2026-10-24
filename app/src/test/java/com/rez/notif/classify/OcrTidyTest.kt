package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTidyTest {
    private fun l(t: String, c: Float, top: Int, h: Int = 30) = OcrTidy.Line(t, c, top, top + h)

    @Test fun dropsStatusBarNoiseAndJoinsWrappedLines() {
        val lines = listOf(
            l("0142 مه 1! 2", 38f, 10),
            l("9", 60f, 60),
            l("App was denied access to", 92f, 200, 40),
            l("SMS", 90f, 245, 40),
            l("The app requested access to a sensitive", 91f, 400),
            l("permission which can put your personal", 90f, 434),
            l("and financial info at risk.", 89f, 468),
            l("Close", 93f, 700),
        )
        assertEquals(
            "App was denied access to SMS\nThe app requested access to a sensitive permission which can put your personal and financial info at risk.\nClose",
            OcrTidy.tidy(lines),
        )
    }

    @Test fun keepsShortConfidentWords() {
        assertEquals("OK", OcrTidy.tidy(listOf(l("OK", 90f, 0))))
        assertEquals("کد: 4821", OcrTidy.tidy(listOf(l("کد: 4821", 70f, 0))))
    }
}

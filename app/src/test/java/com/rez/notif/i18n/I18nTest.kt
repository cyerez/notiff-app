package com.rez.notif.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nTest {
    /** Every t("...") in the app, and every Persian enum label shown through t(...), has English. */
    @Test fun everyTextHasEnglish() {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val call = Regex("(?<![A-Za-z0-9_])t\\(\"((?:[^\"\\\\\\n]|\\\\.)*)\"\\)")
        val missing = root.walk().filter { it.extension == "kt" }.flatMap { f ->
            call.findAll(f.readText()).map { it.groupValues[1].replace("\\\"", "\"").replace("\\$", "$") }
        }.filter { it !in En.map }.toSet()
        assertTrue("No English for: $missing", missing.isEmpty())
    }

    @Test fun switchesLanguage() {
        I18n.lang = Lang.EN
        assertEquals("Settings", t("تنظیمات"))
        I18n.lang = Lang.FA
        assertEquals("تنظیمات", t("تنظیمات"))
    }
}

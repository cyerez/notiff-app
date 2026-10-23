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

    @Test fun walletLabelsHaveEnglish() {
        val labels = com.rez.notif.wallet.WalletKind.entries.flatMap { listOf(it.label, it.userLabel, it.secretLabel) }
        assertTrue(labels.filter { it !in En.map }.toString(), labels.all { it in En.map })
    }

    @Test fun enumLabelsHaveEnglish() {
        // Persian labels of enums shown through t(x.label), read from the source (some enums use Android classes).
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val enumEntry = Regex("^\\s*[A-Z_]+\\(\"([^\"]*[\\u0600-\\u06FF][^\"]*)\"", RegexOption.MULTILINE)
        val files = listOf("ocr/Ocr.kt", "data/Prefs.kt").map { File(root, "com/rez/notif/$it") }
        val missing = files.flatMap { f -> enumEntry.findAll(f.readText()).map { it.groupValues[1] }.toList() }.filter { it !in En.map }
        assertTrue("No English for: $missing", missing.isEmpty())
    }

    @Test fun switchesLanguage() {
        I18n.lang = Lang.EN
        assertEquals("Settings", t("تنظیمات"))
        I18n.lang = Lang.FA
        assertEquals("تنظیمات", t("تنظیمات"))
    }
}

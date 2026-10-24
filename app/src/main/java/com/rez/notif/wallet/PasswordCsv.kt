package com.rez.notif.wallet

/**
 * Reads password exports (CSV) from Google Password Manager / Chrome, Microsoft Edge, Firefox,
 * Bitwarden, 1Password and most others. Columns are found by their header names. Pure Kotlin, tested.
 */
object PasswordCsv {
    private val TITLE = listOf("name", "title", "account", "item name")
    private val URL = listOf("url", "website", "login_uri", "origin", "web site", "uri", "hostname")
    private val USER = listOf("username", "login_username", "login", "user", "user name", "email")
    private val PASS = listOf("password", "login_password", "pass")
    private val NOTE = listOf("note", "notes", "extra", "comment")

    /** Splits CSV text into rows, handling quotes, doubled quotes and line breaks inside quotes. */
    fun rows(text: String): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row += cell.toString(); cell.clear() }
                '\r' -> {}
                '\n' -> { row += cell.toString(); cell.clear(); out += row; row = ArrayList() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); out += row }
        return out.filter { r -> r.any { it.isNotBlank() } }
    }

    /** Entries found in [text]; empty when the file has no password column. */
    fun parse(text: String): List<WalletEntry> {
        val all = rows(text)
        if (all.isEmpty()) return emptyList()
        val head = all.first().map { it.trim().lowercase() }
        fun col(names: List<String>) = names.firstNotNullOfOrNull { n -> head.indexOf(n).takeIf { it >= 0 } }
        val p = col(PASS) ?: return emptyList()
        val t = col(TITLE); val u = col(URL); val n = col(USER); val no = col(NOTE)
        return all.drop(1).mapNotNull { r ->
            fun get(i: Int?) = i?.let { r.getOrNull(it) }?.trim().orEmpty()
            val secret = get(p)
            if (secret.isEmpty()) return@mapNotNull null
            val url = get(u)
            val title = get(t).ifBlank { hostOf(url) }.ifBlank { get(n) }.ifBlank { "?" }
            WalletEntry(title = title, kind = WalletKind.LOGIN, username = get(n), secret = secret, url = url, note = get(no))
        }
    }

    /** "https://accounts.google.com/signin" -> "accounts.google.com"; android://...@com.app/ -> "com.app". */
    fun hostOf(url: String): String {
        if (url.isBlank()) return ""
        if (url.startsWith("android://")) return url.substringAfterLast('@').trimEnd('/')
        return url.substringAfter("://").substringBefore('/').substringBefore('?').removePrefix("www.")
    }
}

package com.rez.notif.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCsvTest {
    @Test fun googleExport() {
        val csv = "name,url,username,password,note\r\n" +
            "accounts.google.com,https://accounts.google.com/,rez@gmail.com,\"p,a\"\"ss\",\r\n" +
            "Digikala,https://www.digikala.com/login,0912,123456,\"two\nlines\"\r\n"
        val l = PasswordCsv.parse(csv)
        assertEquals(2, l.size)
        assertEquals("p,a\"ss", l[0].secret)
        assertEquals("rez@gmail.com", l[0].username)
        assertEquals("two\nlines", l[1].note)
        assertEquals("Digikala", l[1].title)
    }

    @Test fun firefoxHasNoNameColumn() {
        val l = PasswordCsv.parse("\"url\",\"username\",\"password\",\"httpRealm\"\n\"https://www.github.com/login\",\"rez\",\"x1\",\"\"\n")
        assertEquals("github.com", l.single().title)
    }

    @Test fun bitwarden() {
        val l = PasswordCsv.parse("folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n,,login,Bank,,,0,https://bank.ir,me,secret,\n")
        assertEquals("secret", l.single().secret)
        assertEquals("Bank", l.single().title)
    }

    @Test fun androidAppUrl() = assertEquals("com.instagram.android", PasswordCsv.hostOf("android://abc==@com.instagram.android/"))

    @Test fun notAPasswordFile() = assertTrue(PasswordCsv.parse("a,b,c\n1,2,3\n").isEmpty())
}

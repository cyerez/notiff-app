package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JokesTest {
    @Test fun everyDayJokeShowsBeforeRepeating() {
        val n = Jokes.DAILY.size
        assertEquals(n, (0L until n).map { Jokes.ofDay(it + 20_000) }.toSet().size)
    }

    @Test fun seriousOnlyWhenMoneyIsReallyTightOrMourning() {
        assertEquals(Jokes.Mood.SERIOUS, Jokes.mood(totalToman = 100_000))
        assertEquals(Jokes.Mood.SERIOUS, Jokes.mood(totalToman = 9_000_000, mourning = true))
        assertEquals(Jokes.Mood.JOKE, Jokes.mood(totalToman = 5_570_000))
        assertEquals(Jokes.Mood.JOKE, Jokes.mood(null))
    }

    @Test fun overThePlanTeasesInsteadOfHiding() {
        val j = Jokes.forHome(totalToman = 5_570_000, perDayToman = 0, leftToman = -2_000_000, bigTodayRial = 0, epochDay = 20_000, occasionQuip = null, mourning = false)
        assertTrue(j in Jokes.OVER)
    }

    @Test fun occasionFirstNothingWhenTightOrMourning() {
        assertEquals("یلدا!", Jokes.forHome(5_000_000, 200_000, 3_000_000, 0, 20_000, "یلدا!", false))
        assertNull(Jokes.forHome(100_000, 200_000, 3_000_000, 0, 20_000, "یلدا!", false))
        assertNull(Jokes.forHome(5_000_000, 200_000, 3_000_000, 0, 20_000, null, true))
    }
}

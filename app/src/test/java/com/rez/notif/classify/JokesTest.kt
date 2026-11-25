package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Test

class JokesTest {
    @Test fun everyDayJokeShowsBeforeRepeating() {
        val n = Jokes.DAILY.size
        assertEquals(n, (0L until n).map { Jokes.ofDay(it + 20_000) }.toSet().size)
    }

    @Test fun noJokesWhenMoneyIsVeryLow() {
        assertEquals(Jokes.Mood.SERIOUS, Jokes.mood(perDayToman = 10_000, leftToman = 100_000))
        assertEquals(Jokes.Mood.SERIOUS, Jokes.mood(perDayToman = 0, leftToman = -5))
        assertEquals(Jokes.Mood.JOKE, Jokes.mood(perDayToman = 320_000, leftToman = 9_000_000))
        assertEquals(Jokes.Mood.JOKE, Jokes.mood(null, null))
    }
}

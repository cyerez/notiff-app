package com.rez.notif.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.chrono.HijrahDate

class OccasionsTest {
    @Test fun solarDays() {
        val nowruz = Jalali.toEpochDay(1405, 1, 1)
        assertEquals("نوروز", Occasions.on(nowruz).first().name)
        assertTrue(Occasions.isHoliday(nowruz))
        assertNotNull(Occasions.quip(Jalali.toEpochDay(1405, 9, 30))) // Yalda
        assertFalse(Occasions.isHoliday(Jalali.toEpochDay(1405, 9, 30)))
    }

    @Test fun mourningHasNoJoke() {
        val ashura = LocalDate.from(HijrahDate.of(1448, 1, 10)).toEpochDay()
        assertTrue(Occasions.on(ashura).any { it.name == "عاشورای حسینی" })
        assertTrue(Occasions.isMourning(ashura))
        assertNull(Occasions.quip(ashura))
    }

    @Test fun eidAndBlackFriday() {
        val fitr = LocalDate.from(HijrahDate.of(1448, 10, 1)).toEpochDay()
        assertTrue(Occasions.isHoliday(fitr))
        assertNotNull(Occasions.quip(fitr))
        assertTrue(Occasions.on(LocalDate.of(2026, 11, 27).toEpochDay()).any { it.en == "Black Friday" })
    }

    @Test fun chaharshanbeSuriIsTheLastTuesdayOfTheYear() {
        // 1404: Nowruz 1405 is Saturday 21 March 2026, so the last Wednesday is 18 March and the fire night is Tuesday 17 March.
        val d = LocalDate.of(2026, 3, 17).toEpochDay()
        assertTrue(Occasions.on(d).any { it.en == "Chaharshanbe Suri" })
        assertFalse(Occasions.on(d - 7).any { it.en == "Chaharshanbe Suri" })
    }

    @Test fun mostDaysHaveNothing() {
        assertTrue(Occasions.on(Jalali.toEpochDay(1405, 9, 7)).isEmpty())
    }
}

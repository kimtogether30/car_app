package com.vemoring.companion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FuelRefillTest {
    private val t = 20

    @Test fun lowEpisodeIsStickyWhileFuelSloshesAroundThreshold() {
        var s = FuelRefillPolicy.observe(FuelEpisode(), 25, t)
        assertEquals(FuelPhase.ARMED, s.phase)
        s = FuelRefillPolicy.observe(s, 19, t)
        assertEquals(FuelPhase.LOW, s.phase); assertEquals(1L, s.episode)
        // 21 -> 19 -> 22 -> 18: same episode, so the phone sends no new low-fuel alert.
        for (p in listOf(21, 19, 22, 18, 29)) s = FuelRefillPolicy.observe(s, p, t)
        assertEquals(FuelPhase.LOW, s.phase); assertEquals(1L, s.episode)
        assertFalse(FuelRefillPolicy.isCandidate(s, 29, t))
        assertTrue(FuelRefillPolicy.isCandidate(s, 30, t))
    }

    @Test fun firstConnectionAlreadyLowStartsOneEpisode() {
        val s = FuelRefillPolicy.observe(FuelEpisode(), 20, t)
        assertEquals(FuelPhase.LOW, s.phase); assertEquals(1L, s.episode)
        assertEquals(s, FuelRefillPolicy.observe(s, 20, t))
    }

    @Test fun threeSeparateReadsThirtySecondsApartConfirmEvenWithSameValue() {
        val c = FuelRefillConfirmation(t)
        assertNull(c.nextRequestAt())
        assertEquals(FuelRefillConfirmation.Outcome.CONTINUE, c.accept(30, 0))
        assertEquals(30_000L, c.nextRequestAt())
        assertEquals(FuelRefillConfirmation.Outcome.CONTINUE, c.accept(31, 30_000))
        assertEquals(FuelRefillConfirmation.Outcome.CONFIRMED, c.accept(30, 60_500))
    }

    @Test fun responsesInsideThirtySecondsAreNotCounted() {
        val c = FuelRefillConfirmation(t)
        c.accept(40, 0)
        assertEquals(FuelRefillConfirmation.Outcome.CONTINUE, c.accept(40, 5_000))
        assertEquals(FuelRefillConfirmation.Outcome.CONTINUE, c.accept(40, 10_000))
        assertEquals(1, c.accepted)
        assertEquals(FuelRefillConfirmation.Outcome.CONTINUE, c.accept(40, 30_000))
        assertEquals(FuelRefillConfirmation.Outcome.CONFIRMED, c.accept(40, 60_000))
    }

    @Test fun lowerValueErrorOrInvalidCancels() {
        val low = FuelRefillConfirmation(t); low.accept(30, 0)
        assertEquals(FuelRefillConfirmation.Outcome.CANCELLED, low.accept(29, 30_000))
        val error = FuelRefillConfirmation(t); error.accept(30, 0)
        assertEquals(FuelRefillConfirmation.Outcome.CANCELLED, error.accept(null, 30_000))
        assertEquals(FuelRefillConfirmation.Outcome.CANCELLED, FuelRefillConfirmation(t).accept(101, 0))
    }

    @Test fun confirmedRefuelReArmsAndKeepsBoundedEventsThroughJson() {
        var s = FuelRefillPolicy.observe(FuelEpisode(), 10, t)
        s = FuelRefillPolicy.confirmed(s, 45, t, 1_000, "r1")
        assertEquals(FuelPhase.ARMED, s.phase)
        assertEquals(listOf("r1"), s.refills.map { it.id })
        // Next low episode alerts again once.
        s = FuelRefillPolicy.observe(s, 20, t)
        assertEquals(FuelPhase.LOW, s.phase); assertEquals(2L, s.episode)
        repeat(7) { n -> s = FuelRefillPolicy.observe(FuelRefillPolicy.confirmed(s, 50, t, n.toLong(), "x$n"), 5, t) }
        assertEquals(FuelRefillPolicy.KEEP_REFILLS, s.refills.size)
        assertEquals(s, FuelEpisode.from(JSONObject(s.json().toString())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotConfirmWithoutLowEpisode() { FuelRefillPolicy.confirmed(FuelEpisode(phase = FuelPhase.ARMED), 50, t, 0, "x") }
}

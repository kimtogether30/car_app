package com.vemoring.companion

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class WeatherRetryTest {
    private var now = 0L
    private val waits = mutableListOf<Long>()
    private fun <T> run(block: () -> T) = retryTransient(clock = { now }, sleep = { waits += it; now += it }, block = block)

    @Test fun lostHandshakeIsRetriedThenSucceeds() {
        var calls = 0
        val result = run { calls++; if (calls < 3) throw SocketTimeoutException("Read timed out") else "ok" }
        assertEquals("ok", result)
        assertEquals(3, calls)
        assertEquals(listOf(1_000L, 3_000L), waits)
    }

    @Test fun serverErrorIsRetriedButBadRequestIsNot() {
        var calls = 0
        assertEquals("ok", run { calls++; if (calls == 1) throw WeatherHttpException(503) else "ok" })
        assertEquals(2, calls)
        calls = 0
        assertThrows(WeatherHttpException::class.java) { run { calls++; throw WeatherHttpException(400) } }
        assertEquals(1, calls)
    }

    @Test fun givesUpAfterThreeAttemptsWithLastError() {
        var calls = 0
        val e = assertThrows(IOException::class.java) { run<String> { calls++; throw IOException("try $calls") } }
        assertEquals(3, calls)
        assertEquals("try 3", e.message)
    }

    @Test fun noNewAttemptAfterTimeBudget() {
        var calls = 0
        assertThrows(IOException::class.java) { run<String> { calls++; now += 50_000; throw SocketTimeoutException() } }
        assertEquals(1, calls)
    }

    @Test fun invalidDataIsNotRetried() {
        var calls = 0
        assertThrows(IllegalArgumentException::class.java) { run<String> { calls++; throw IllegalArgumentException("too large") } }
        assertEquals(1, calls)
    }
}

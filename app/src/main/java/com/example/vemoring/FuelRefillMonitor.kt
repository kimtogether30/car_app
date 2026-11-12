package com.example.vemoring

import android.content.Context
import com.example.vemoring.vehicle.*
import com.vemoring.companion.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/**
 * Vehicle side of the agreed refuel rule (FuelRefill.kt). Fed from the care payload on every relevant snapshot.
 * No timers when nothing is pending: a confirmation is at most three separate fuel reads per candidate and then ends.
 */
object FuelRefillMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var jobKey: String? = null
    /** After a cancelled check, wait one gap before re-checking the same car (sloshing cannot spin up reads). */
    private val cooldownUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val stopStates = setOf(ReadingState.Disconnected, ReadingState.Error, ReadingState.PermissionDenied,
        ReadingState.Unsupported, ReadingState.NoData)

    /** Caller is on IO (care payload). Returns the state to send to the phone. */
    @Synchronized
    fun observe(context: Context, journal: CareJournal, threshold: Int, reading: Reading,
        repository: VehicleRepository = SharedVehicleConnection.repository(context),
        now: Long = System.currentTimeMillis()): FuelEpisode {
        val percent = reading.takeIf { it.state == ReadingState.Available && it.receivedAtMillis?.let { at -> now - at in 0..60_000 } == true }
            ?.realPercent()
        val state = if (percent == null) journal.fuelEpisode()
            else journal.updateFuelEpisode { FuelRefillPolicy.observe(it, percent, threshold) }
        val key = "${journal.vehicleId}:$threshold"
        val running = job?.isActive == true
        // Lower value, explicit failure/disconnect, other car or changed threshold cancels an unfinished check.
        if (running && (jobKey != key || reading.state in stopStates ||
                percent != null && percent < threshold + FuelRefillPolicy.MARGIN_PERCENT)) cancel()
        if (percent != null && job?.isActive != true && now >= (cooldownUntil[journal.vehicleId] ?: 0L) &&
            FuelRefillPolicy.isCandidate(state, percent, threshold)) {
            jobKey = key
            job = scope.launch { confirm(journal, threshold, repository) }
        }
        return state
    }

    @Synchronized fun cancel() { job?.cancel(); job = null; jobKey = null }

    private suspend fun readFuel(repository: VehicleRepository): Reading =
        withTimeoutOrNull(20_000) { suspendCancellableCoroutine { c -> repository.readFuel { if (c.isActive) c.resume(it) } } }
            ?: Reading(ReadingState.Error, reason = "응답 시간 초과")

    private suspend fun confirm(journal: CareJournal, threshold: Int, repository: VehicleRepository) {
        val check = FuelRefillConfirmation(threshold)
        while (currentCoroutineContext().isActive) {
            check.nextRequestAt()?.let { delay((it - System.currentTimeMillis()).coerceAtLeast(0)) }
            val reading = readFuel(repository)
            val respondedAt = System.currentTimeMillis()
            val percent = reading.takeIf { it.state == ReadingState.Available }?.realPercent()
            when (check.accept(percent, respondedAt)) {
                FuelRefillConfirmation.Outcome.CANCELLED -> {
                    cooldownUntil[journal.vehicleId] = respondedAt + FuelRefillPolicy.GAP_MILLIS
                    if (reading.state == ReadingState.Available) repository.publish(VehicleField.FUEL, reading)
                    return
                }
                FuelRefillConfirmation.Outcome.CONTINUE -> repository.publish(VehicleField.FUEL, reading)
                FuelRefillConfirmation.Outcome.CONFIRMED -> {
                    // Persist first, then publish so the next payload already carries the refuel event.
                    withContext(Dispatchers.IO) {
                        journal.updateFuelEpisode { state ->
                            if (state.phase == FuelPhase.LOW) FuelRefillPolicy.confirmed(state, percent!!, threshold, respondedAt,
                                java.util.UUID.randomUUID().toString()) else state
                        }
                    }
                    repository.publish(VehicleField.FUEL, reading)
                    return
                }
            }
        }
    }
}

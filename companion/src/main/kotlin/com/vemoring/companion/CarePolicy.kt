package com.vemoring.companion

import java.time.*
import kotlin.math.roundToInt

val CARE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
const val CARE_POLICY_VERSION = 1

/** Long-lived, non-location journal. Never replace this with a final daily score. */
data class TravelInterval(val id: String, val start: Instant, val end: Instant, val km: Double,
    val stationary: Boolean = false, val located: Boolean = false, val weather: HourWeather? = null,
    val source: String = "Real", val policy: Int = CARE_POLICY_VERSION) {
    init {
        require(id.isNotBlank() && end > start && km.isFinite() && km >= 0)
        require(!stationary || km == 0.0)
    }
}

/** Matching is explicit: hourly bins and actual travel only, never today's forecast on old mileage. */
data class HourWeather(val hourStart: Instant, val rainMm: Double, val showersMm: Double,
    val snowCm: Double, val pm10: Double?, val pm25: Double?, val obtainedAt: Instant,
    val version: String) {
    init { require(listOfNotNull(rainMm, showersMm, snowCm, pm10, pm25).all { it.isFinite() && it >= 0 }) }
}

data class WashBaseline(val id: String, val date: LocalDate, val completedAt: Instant? = null,
    val visitId: String? = null, val cancelled: Boolean = false, val source: String = "Manual", val appliedAt: Instant? = null) {
    val start: Instant get() = completedAt ?: date.plusDays(1).atStartOfDay(CARE_ZONE).toInstant()
}

data class WashScore(val value: Double?, val cycleId: String?, val actualKm: Double = 0.0,
    val estimatedKm: Double = 0.0, val dustLoss: Double = 0.0) {
    val displayed: Int? get() = value?.roundToInt()?.coerceIn(0, 100)
}

object WashPolicy {
    /** Only zero-distance contiguous evidence is lossless across every possible wash boundary. */
    fun coalesceParking(intervals: List<TravelInterval>): List<TravelInterval> {
        val result = mutableListOf<TravelInterval>()
        for (next in intervals.sortedBy { it.start }) {
            val last = result.lastOrNull()
            if (last != null && last.stationary && next.stationary && last.end == next.start &&
                last.source == next.source && last.policy == next.policy) {
                result[result.lastIndex] = last.copy(end = next.end)
            } else result += next
        }
        return result
    }
    /** Inputs are canonical non-overlapping actual intervals; unknown gaps alone get 20km/day. */
    fun score(records: List<WashBaseline>, intervals: List<TravelInterval>, now: Instant): WashScore {
        val today = now.atZone(CARE_ZONE).toLocalDate()
        val baseline = records.filter { !it.cancelled && it.date <= today &&
            (it.completedAt == null || it.completedAt <= now) }.maxWithOrNull(
            compareBy<WashBaseline> { it.start }.thenBy { it.id }) ?: return WashScore(null, null)
        if (baseline.start >= now) return WashScore(100.0, baseline.id)
        val actual = intervals.filter { it.source == "Real" && it.end > baseline.start && it.start < now }
            .sortedBy { it.start }
        require(actual.map { it.id }.distinct().size == actual.size)
        require(actual.zipWithNext().all { (a, b) -> a.end <= b.start }) { "Overlapping travel evidence" }
        var cursor = baseline.start
        var actualKm = 0.0
        var estimated = 0.0
        var extra = 0.0
        val dailyDust = mutableMapOf<LocalDate, Double>()
        fun estimate(until: Instant) {
            if (until > cursor) estimated += Duration.between(cursor, until).toMillis() / 86400000.0 * 20.0
        }
        for (part in actual) {
            estimate(part.start)
            // No invented allocation across a wash boundary or the current observation boundary.
            if (part.start >= baseline.start && part.end <= now) {
                actualKm += part.km
                val w = part.weather
                if (part.located && w != null && part.start >= w.hourStart &&
                    part.end <= w.hourStart.plusSeconds(3600) && w.obtainedAt >= part.end && w.obtainedAt <= now &&
                    w.obtainedAt <= part.end.plusSeconds(72 * 3600L)) {
                    when {
                        w.snowCm >= .1 -> extra += part.km * .40
                        w.rainMm + w.showersMm >= .1 -> extra += part.km * .20
                        else -> {
                            val factor = when {
                                (w.pm10 ?: 0.0) > 150 || (w.pm25 ?: 0.0) > 75 -> .06
                                (w.pm10 ?: 0.0) > 80 || (w.pm25 ?: 0.0) > 35 -> .03
                                else -> 0.0
                            }
                            // An hourly interval crossing Seoul midnight has no justified distance split.
                            val day = part.start.atZone(CARE_ZONE).toLocalDate()
                            if (part.end.minusNanos(1).atZone(CARE_ZONE).toLocalDate() == day)
                                dailyDust[day] = (dailyDust[day] ?: 0.0) + part.km * factor
                        }
                    }
                }
            }
            cursor = maxOf(cursor, minOf(part.end, now))
        }
        estimate(now)
        val dust = dailyDust.values.sumOf { it.coerceAtMost(2.0) }
        return WashScore((100 - (actualKm + estimated) * .1 - extra - dust).coerceIn(0.0, 100.0),
            baseline.id, actualKm, estimated, dust)
    }
}

enum class WashFacility { Conveyor, Stationary, Unknown }
/** Supplied by verified facility/map information, never inferred from a nearby POI point. */
enum class WashLocationEvidence { SharedOrUncertain, DedicatedSelfWash, SeparatedWashArea }
/** Inside is the distinguishable wash section, never the whole gas station or an OSM POI radius. */
enum class ZoneEvidence { Inside, Outside, Uncertain }
enum class EvidenceSource { Vehicle, Phone }
data class WashTrialProfile(val version: Int = 1, val requestSeconds: Long = 5,
    val freshSeconds: Long = 10, val maxAccuracyM: Double = 10.0, val entrySeconds: Long = 10,
    val maxGapSeconds: Long = 15, val exitSeconds: Long = 30, val dwellSeconds: Long = 180)

/** All predicates describe evidence supplied by adapters, not inferred from a POI radius or P/N. */
data class WashObservation(val at: Instant, val receivedAt: Instant, val accuracyM: Double?,
    val zone: ZoneEvidence, val source: EvidenceSource, val stationary: Boolean?, val gear: String?,
    val consistentMotion: Boolean = false, val vehicleReconnected: Boolean = false,
    val facilityExit: Boolean = false, val contradictsVehicle: Boolean = false)

/** A qualified estimate is applied immediately; actual washing need not be proven by a water sensor. */
data class DetectedWash(val visitId: String, val completedAt: Instant,
    val evidence: WashCompletionEvidence = WashCompletionEvidence.EstimatedVisit)

/** Facility-specific criteria qualify an estimate; the correction banner handles uncertain actual washing. */
class WashVisitDetector(private val visitId: String, private val facility: WashFacility,
    private val profile: WashTrialProfile = WashTrialProfile(),
    private val locationEvidence: WashLocationEvidence = WashLocationEvidence.SharedOrUncertain) {
    private var last: WashObservation? = null
    private var lastValidAt: Instant? = null
    private var entry: Instant? = null
    private var entered = false
    private var dwell = 0L
    private var neutralMotion = false
    private var driveAfterMotion = false
    private var exit: Instant? = null
    private var ended = false
    private var phoneUsed = false
    private var reconnected = false
    var holdReason: String = "구역·위치·차량 근거 대기"
        private set

    fun observe(o: WashObservation): DetectedWash? {
        if (ended || facility == WashFacility.Unknown) return null
        val previous = last
        if (previous != null && o.at <= previous.at) return null
        val fresh = Duration.between(o.at, o.receivedAt).seconds in 0..profile.freshSeconds
        val accurate = o.accuracyM?.let { it.isFinite() && it in 0.0..profile.maxAccuracyM } == true
        val gap = previous?.let { Duration.between(it.at, o.at).seconds }
        val validGap = lastValidAt?.let { Duration.between(it, o.at).seconds }
        if (validGap != null && validGap > profile.maxGapSeconds || o.contradictsVehicle) resetEvidence()
        if (!fresh || !accurate || o.zone == ZoneEvidence.Uncertain || o.contradictsVehicle) {
            last = o.copy(zone = ZoneEvidence.Uncertain); entry = null; exit = null
            holdReason = "위치·경계·차량 일치 근거 부족"
            return null
        }
        if (o.source == EvidenceSource.Phone && !entered) { holdReason = "차량 진입 근거 없음"; return null }
        if (o.source == EvidenceSource.Phone) {
            phoneUsed = true
            if (previous?.source != EvidenceSource.Phone) reconnected = false
        }
        if (o.source == EvidenceSource.Vehicle && o.vehicleReconnected) reconnected = true
        val conveyor = facility == WashFacility.Conveyor
        when (o.zone) {
            ZoneEvidence.Inside -> {
                exit = null
                if (!entered) {
                    if (entry == null) entry = o.at
                    if (Duration.between(entry, o.at).seconds >= profile.entrySeconds) entered = true
                }
                val qualifies = conveyor || o.stationary == true
                val previousQualifies = conveyor || previous?.stationary == true
                if (!conveyor && o.stationary != true) dwell = 0
                if (entered && qualifies && previousQualifies && previous?.zone == ZoneEvidence.Inside &&
                    gap != null && gap <= profile.requestSeconds) dwell += gap
                if (o.source == EvidenceSource.Vehicle && o.gear == "N" && o.consistentMotion) neutralMotion = true
                if (neutralMotion && o.source == EvidenceSource.Vehicle && o.gear == "D") driveAfterMotion = true
            }
            ZoneEvidence.Outside -> {
                if (!entered) { entry = null; last = o; lastValidAt = o.at; return null }
                if (exit == null) exit = o.at
                if (o.source == EvidenceSource.Vehicle && o.gear == "D" && neutralMotion) driveAfterMotion = true
                // Stationary facilities require actual stationary evidence, never a blanket N-only rule.
                val pattern = facility == WashFacility.Stationary || (neutralMotion && driveAfterMotion)
                if (dwell >= profile.dwellSeconds && Duration.between(exit, o.at).seconds >= profile.exitSeconds &&
                    o.source == EvidenceSource.Vehicle && o.consistentMotion && o.facilityExit &&
                    (!phoneUsed || reconnected) && pattern) {
                    ended = true; holdReason = "자동 추정"
                    val evidence=if(locationEvidence==WashLocationEvidence.SharedOrUncertain) WashCompletionEvidence.EstimatedVisit else WashCompletionEvidence.ClearFacilityPattern
                    return DetectedWash(visitId, exit!!,evidence)
                }
            }
            else -> Unit
        }
        last = o
        lastValidAt = o.at
        holdReason = "유효 체류 ${dwell}초 · 출차 근거 대기"
        return null
    }
    private fun resetEvidence() {
        entry = null; entered = false; dwell = 0; exit = null
        neutralMotion = false; driveAfterMotion = false
        phoneUsed = false; reconnected = false
    }
}

/** Idempotent records/tombstones survive cancellation, retries, edits and process death. */
class WashRecords(records: List<WashBaseline> = emptyList()) {
    val values = records.toMutableList()
    fun accept(wash: DetectedWash, source: String = "Real", appliedAt: Instant = wash.completedAt): Boolean {
        if (values.any { it.visitId == wash.visitId }) return false
        values += WashBaseline("wash:${wash.visitId}", wash.completedAt.atZone(CARE_ZONE).toLocalDate(),
            wash.completedAt, wash.visitId, source = source, appliedAt = appliedAt)
        return true
    }
    fun cancel(visitId: String, now: Instant): Boolean {
        val i = values.indexOfFirst { it.visitId == visitId && !it.cancelled }
        if (i < 0) return false
        val at = values[i].appliedAt ?: values[i].completedAt ?: return false
        if (now < at || now > at.plusSeconds(7 * 86400L)) return false
        values[i] = values[i].copy(cancelled = true)
        return true
    }
}

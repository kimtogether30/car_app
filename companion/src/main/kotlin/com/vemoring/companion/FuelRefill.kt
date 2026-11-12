package com.vemoring.companion

import org.json.JSONArray
import org.json.JSONObject

/**
 * Agreed refuel rule (docs/HEV_FUEL_ALERT_POLICY.md, user decision 2026-11-13):
 * fuel <= T starts a low episode and the low-fuel alert is sent once for it. Sloshing back above T does not
 * end the episode. Only a value >= T+10%p, confirmed by three separate fuel-only SDK reads whose requests are
 * each made at least 30 s after the previous accepted response, counts as a refuel. That sends one
 * "주유 완료" banner and re-arms the low alert. A refuel adds at least 10%p in practice.
 */
enum class FuelPhase { UNKNOWN, ARMED, LOW }

data class FuelRefill(val id: String, val at: Long, val percent: Int, val episode: Long) {
    fun json(): JSONObject = JSONObject().put("id", id).put("at", at).put("percent", percent).put("episode", episode)
    companion object {
        fun from(j: JSONObject) = FuelRefill(j.getString("id"), j.getLong("at"), j.getInt("percent"), j.getLong("episode"))
    }
}

data class FuelEpisode(val episode: Long = 0, val phase: FuelPhase = FuelPhase.UNKNOWN, val threshold: Int? = null,
    val refills: List<FuelRefill> = emptyList()) {
    fun json(): JSONObject = JSONObject().put("episode", episode).put("phase", phase.name)
        .put("threshold", threshold ?: JSONObject.NULL).put("refills", JSONArray().also { a -> refills.forEach { a.put(it.json()) } })
    companion object {
        fun from(j: JSONObject): FuelEpisode {
            val a = j.optJSONArray("refills") ?: JSONArray()
            require(a.length() <= FuelRefillPolicy.KEEP_REFILLS)
            return FuelEpisode(j.getLong("episode"), FuelPhase.valueOf(j.getString("phase")),
                if (j.isNull("threshold")) null else j.getInt("threshold"),
                (0 until a.length()).map { FuelRefill.from(a.getJSONObject(it)) })
        }
    }
}

object FuelRefillPolicy {
    const val MARGIN_PERCENT = 10
    const val GAP_MILLIS = 30_000L
    const val READS = 3
    const val KEEP_REFILLS = 5

    /** A valid integer reading (0..100) moves ARMED/UNKNOWN to LOW at or below T. LOW is sticky until a confirmed refuel. */
    fun observe(state: FuelEpisode, percent: Int, threshold: Int): FuelEpisode {
        require(percent in 0..100)
        return when (state.phase) {
            FuelPhase.LOW -> state.copy(threshold = threshold)
            FuelPhase.UNKNOWN, FuelPhase.ARMED ->
                if (percent <= threshold) state.copy(episode = state.episode + 1, phase = FuelPhase.LOW, threshold = threshold)
                else state.copy(phase = FuelPhase.ARMED, threshold = threshold)
        }
    }

    fun isCandidate(state: FuelEpisode, percent: Int, threshold: Int) = state.phase == FuelPhase.LOW && percent >= threshold + MARGIN_PERCENT

    /** Called only after a completed three-read confirmation. Re-arms the low alert and records one refuel event. */
    fun confirmed(state: FuelEpisode, percent: Int, threshold: Int, at: Long, id: String): FuelEpisode {
        require(state.phase == FuelPhase.LOW && percent >= threshold + MARGIN_PERCENT)
        return state.copy(phase = FuelPhase.ARMED, threshold = threshold,
            refills = (state.refills + FuelRefill(id, at, percent, state.episode)).takeLast(KEEP_REFILLS))
    }
}

/**
 * In-memory three-read check for one candidate. Counts restart with the app/session (not persisted).
 * Each accepted response must be at least [FuelRefillPolicy.GAP_MILLIS] after the previous accepted one.
 */
class FuelRefillConfirmation(val threshold: Int) {
    enum class Outcome { CONTINUE, CONFIRMED, CANCELLED }
    private val acceptedAt = mutableListOf<Long>()
    val accepted: Int get() = acceptedAt.size
    /** Earliest time the next separate read may be requested; null means immediately. */
    fun nextRequestAt(): Long? = acceptedAt.lastOrNull()?.plus(FuelRefillPolicy.GAP_MILLIS)

    /** [percent] is null for an error, timeout, disconnect or invalid value. */
    fun accept(percent: Int?, respondedAt: Long): Outcome {
        if (percent == null || percent !in 0..100 || percent < threshold + FuelRefillPolicy.MARGIN_PERCENT) return Outcome.CANCELLED
        val last = acceptedAt.lastOrNull()
        // A response that arrives too soon is not a separate 30-second confirmation; it is not counted.
        if (last != null && respondedAt - last < FuelRefillPolicy.GAP_MILLIS) return Outcome.CONTINUE
        acceptedAt += respondedAt
        return if (acceptedAt.size >= FuelRefillPolicy.READS) Outcome.CONFIRMED else Outcome.CONTINUE
    }
}

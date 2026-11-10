package com.vemoring.companion

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

/** An immutable, versioned view model, never a screenshot or a remote vehicle command. */
data class MirrorSnapshot(val vehicleId: String, val revision: Long, val capturedAt: Long, val content: String) {
    fun encode(): String = JSONObject().put("schema", 1).put("vehicleId", vehicleId).put("revision", revision)
        .put("capturedAt", capturedAt).put("content", JSONObject(content)).toString()

    companion object {
        const val MAX_BYTES = 1024 * 1024
        fun decode(raw: String): MirrorSnapshot {
            require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Snapshot too large" }
            val root = JSONObject(raw)
            require(root.getInt("schema") == 1) { "Unsupported schema" }
            val id = root.getString("vehicleId")
            val revision = root.getLong("revision")
            val captured = root.getLong("capturedAt")
            require(id.isNotBlank() && id.length <= 128 && revision > 0 && captured > 0)
            val content = root.getJSONObject("content")
            val p = content.getJSONObject("profile")
            listOf("nickname", "model", "plate", "fuel").forEach { require(p.getString(it).length <= 256) }
            require(p.getInt("washAlert") in 5..50 && p.getInt("fuelAlert") in 5..40)
            p.getBoolean("notifications")
            val records = content.getJSONArray("records")
            require(records.length() <= 5000)
            val ids = mutableSetOf<String>()
            repeat(records.length()) { index ->
                val r = records.getJSONObject(index)
                require(r.getString("id").isNotBlank() && ids.add(r.getString("id")))
                require(r.getString("kind") in listOf("wash", "fuel", "oil"))
                LocalDate.parse(r.getString("date"))
                require(r.getString("title").length <= 4096 && r.getString("detail").length <= 32768)
                r.optString("completedAt").takeUnless { it.isBlank() || it == "null" }?.let(java.time.Instant::parse)
                require(r.optString("visitId").length <= 256)
            }
            require(content.getString("mood") in listOf("NORMAL", "FUEL", "WASH", "OIL", "ANGRY", "EXHAUSTED"))
            require(content.getString("message").length <= 4096)
            content.getBoolean("demo")
            listOf("fuel", "battery", "cleanliness").forEach { key ->
                if (!content.isNull(key)) require(content.getDouble(key).let { it.isFinite() && it in 0.0..100.0 })
            }
            if (!content.isNull("oilRemainingKm")) require(content.getDouble("oilRemainingKm").isFinite())
            return MirrorSnapshot(id, revision, captured, content.toString())
        }
    }
}

/** Length-prefix framing rejects oversized and truncated frames before publishing any state. */
object MirrorFrames {
    fun write(output: OutputStream, snapshot: MirrorSnapshot) {
        val bytes = snapshot.encode().toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MirrorSnapshot.MAX_BYTES)
        DataOutputStream(output).apply { writeInt(bytes.size); write(bytes); flush() }
    }
    fun read(input: InputStream): MirrorSnapshot {
        val stream = DataInputStream(input)
        val length = stream.readInt()
        require(length in 1..MirrorSnapshot.MAX_BYTES)
        val bytes = ByteArray(length)
        stream.readFully(bytes)
        return MirrorSnapshot.decode(bytes.toString(Charsets.UTF_8))
    }
}

/** Link loss never clears the last complete view. A new source replaces rather than merges. */
class MirrorJournal(initial: MirrorSnapshot? = null) {
    var latest: MirrorSnapshot? = initial
        private set
    fun accept(next: MirrorSnapshot): Boolean {
        val previous = latest
        if (previous?.vehicleId == next.vehicleId && next.revision <= previous.revision) return false
        latest = next
        return true
    }
}

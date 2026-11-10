package com.vemoring.companion

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException

class MirrorSnapshotTest {
    private fun snapshot(revision: Long = 1, fuel: Any = 17, id: String = "vehicle-a"): MirrorSnapshot {
        val p = JSONObject().put("nickname", "베모링").put("model", "현대 아반떼").put("plate", "12가 3456")
            .put("fuel", "휘발유").put("notifications", true).put("washAlert", 30).put("fuelAlert", 20)
        val record = JSONObject().put("id", "wash-1").put("kind", "wash").put("date", "2026-11-08").put("title", "세차").put("detail", "장소·금액")
        val content = JSONObject().put("profile", p).put("records", JSONArray().put(record)).put("mood", "FUEL")
            .put("message", "배가 고파요…").put("demo", false).put("fuel", fuel).put("battery", JSONObject.NULL)
            .put("cleanliness", 90).put("oilRemainingKm", 1234).put("fuelStatus", "실제 차량 데이터")
        return MirrorSnapshot(id, revision, 1_799_000_000_000, content.toString())
    }

    @Test fun completeFramePreservesKoreanProfileRecordsMoodAndUnknownValues() {
        val original = snapshot(fuel = JSONObject.NULL)
        val output = ByteArrayOutputStream()
        MirrorFrames.write(output, original)
        val decoded = MirrorFrames.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(original.vehicleId, decoded.vehicleId)
        val data = JSONObject(decoded.content)
        assertEquals("베모링", data.getJSONObject("profile").getString("nickname"))
        assertEquals("장소·금액", data.getJSONArray("records").getJSONObject(0).getString("detail"))
        assertTrue(data.isNull("fuel"))
        assertEquals("FUEL", data.getString("mood"))
    }

    @Test fun disconnectOrPartialNextFrameKeepsLastCompleteSnapshot() {
        val first = snapshot()
        val journal = MirrorJournal(first)
        val bytes = ByteArrayOutputStream().also { MirrorFrames.write(it, snapshot(2, 50)) }.toByteArray()
        assertThrows(EOFException::class.java) { journal.accept(MirrorFrames.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 3)))) }
        assertEquals(first, journal.latest)
        // A process restart loads the persisted envelope, with no synthetic connected state.
        val restored = MirrorJournal(MirrorSnapshot.decode(journal.latest!!.encode()))
        assertEquals(17, JSONObject(restored.latest!!.content).getInt("fuel"))
    }

    @Test fun duplicateAndOlderSnapshotsNeverRollBackTheDisplay() {
        val journal = MirrorJournal(snapshot(8, 80))
        assertFalse(journal.accept(snapshot(7, 5)))
        assertFalse(journal.accept(snapshot(8, 3)))
        assertEquals(80, JSONObject(journal.latest!!.content).getInt("fuel"))
        assertTrue(journal.accept(snapshot(9, 42)))
        assertEquals(42, JSONObject(journal.latest!!.content).getInt("fuel"))
        assertTrue(journal.accept(snapshot(1, 60, "vehicle-b")))
        assertEquals("vehicle-b", journal.latest!!.vehicleId)
    }

    @Test fun malformedOrOversizedMessagesAreRejectedWithoutReplacingTheCache() {
        val journal = MirrorJournal(snapshot())
        assertThrows(IllegalArgumentException::class.java) { MirrorSnapshot.decode(snapshot(fuel = 101).encode()) }
        val badVersion = JSONObject(snapshot().encode()).put("schema", 9).toString()
        assertThrows(IllegalArgumentException::class.java) { MirrorSnapshot.decode(badVersion) }
        val huge = ByteArrayOutputStream().also { DataOutputStream(it).writeInt(MirrorSnapshot.MAX_BYTES + 1) }
        assertThrows(IllegalArgumentException::class.java) { MirrorFrames.read(ByteArrayInputStream(huge.toByteArray())) }
        assertEquals(1L, journal.latest!!.revision)
    }
}

package com.example.vemoring.phone

import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MirrorPersistenceTest {
    @Test fun lastSnapshotSurvivesNewStoreAndInvalidReplacementDoesNotEraseIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "companion_test_${UUID.randomUUID()}"
        val content = """{"profile":{"nickname":"테스트 차량","model":"현대 아반떼","plate":"12가 3456","fuel":"휘발유","notifications":true,"washAlert":30,"fuelAlert":20},"records":[],"mood":"FUEL","message":"배가 고파요","demo":false,"fuel":18,"battery":null,"cleanliness":65,"oilRemainingKm":3000}"""
        try {
            val received = ReceivedMirror(MirrorSnapshot("test-car", 1, 100, content), 1234567)
            MirrorCacheStore(context, name).save(received)
            val restored = MirrorCacheStore(context, name).load()!!
            assertEquals(1234567L, restored.receivedAt)
            assertEquals("test-car", restored.snapshot.vehicleId)
            assertEquals("FUEL", org.json.JSONObject(restored.snapshot.content).getString("mood"))
            assertThrows(IllegalArgumentException::class.java) {
                MirrorCacheStore(context, name).save(ReceivedMirror(received.snapshot.copy(content = content.replace("\"fuel\":18", "\"fuel\":180")), 9999999))
            }
            assertEquals(1L, MirrorCacheStore(context, name).load()!!.snapshot.revision)
            assertEquals(1234567L, MirrorCacheStore(context, name).load()!!.receivedAt)
        } finally { context.deleteSharedPreferences(name) }
    }
}

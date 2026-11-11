package com.example.vemoring

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.MaintenanceType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Real Pleos UI, in-memory records only. Never opens the user's garage or SDK stream. */
class MaintenanceSimulationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tirePressureAndEngineOilRemainSeparateInsideMaintenance() {
        var saved: CareRecord? = null
        val original = CareRecord(date = LocalDate.now(), kind = CareKind.MAINTENANCE, title = "시뮬레이션 정비")
        compose.setContent { MaterialTheme { RecordDialog(original, original.date, close = {}, save = { saved = it }) } }
        compose.onNodeWithText("타이어 공기압").performScrollTo().performClick()
        capture("maintenance-tire.png")
        compose.onNodeWithText("기록 저장").performClick()
        compose.runOnIdle {
            assertEquals(original.id, saved!!.id)
            assertEquals(MaintenanceType.TIRE_PRESSURE, saved!!.maintenanceType)
            assertEquals("maintenance", saved!!.json().getString("kind"))
            assertEquals(saved, careRecordFromJson(saved!!.json()))
        }
        compose.onNodeWithText("엔진오일 교체").performScrollTo().performClick()
        capture("maintenance-oil.png")
        compose.onNodeWithText("기록 저장").performClick()
        compose.runOnIdle {
            assertEquals(CareKind.MAINTENANCE, saved!!.kind)
            assertEquals(MaintenanceType.ENGINE_OIL, saved!!.maintenanceType)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = compose.onNode(isRoot() and hasAnyDescendant(hasText("정비 항목"))).captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.cacheDir, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
}

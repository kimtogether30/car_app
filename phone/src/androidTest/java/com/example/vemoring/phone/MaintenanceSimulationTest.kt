package com.example.vemoring.phone

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Real editor and notifications; disposable verification app, no Bluetooth radio involved. */
class MaintenanceSimulationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explicitWorkItemControlsSavedRecordAndNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".verification"))
        var saved: Entry? = null
        val original = Entry(kind = MAINTENANCE, title = "시뮬레이션 정비", date = LocalDate.now(CARE_ZONE), detail = "실제 사용자 기록 아님")
        compose.setContent { MaterialTheme { RecordEditor(original,original.date,false,{}, {saved=it}) } }
        compose.onNodeWithText("타이어 공기압").performScrollTo().performClick()
        capture("maintenance-tire.png")
        compose.onNodeWithTag("form-save").performClick()
        compose.runOnIdle { assertEquals(MaintenanceType.TIRE_PRESSURE,saved!!.maintenanceType) }
        val tire = requireNotNull(saved)
        compose.onNodeWithText("엔진오일 교체").performScrollTo().performClick()
        capture("maintenance-oil.png")
        compose.onNodeWithTag("form-save").performClick()
        compose.runOnIdle { assertEquals(MaintenanceType.ENGINE_OIL,saved!!.maintenanceType) }
        val oil = requireNotNull(saved).copy(id=UUID.randomUUID().toString())
        val id = "maintenance-simulation-${UUID.randomUUID()}"
        val profile = JSONObject().put("fuel","휘발유").put("notifications",true).put("simulation",true).toString()
        val before = CareAuthority(id,1,profile,"[]")
        val afterTire = before.copy(version=2,records=JSONArray().put(tire.wireJson()).toString())
        BannerNotifications.recordChanges(context,before,afterTire)
        val afterOil = afterTire.copy(version=3,records=JSONArray().put(tire.wireJson()).put(oil.wireJson()).toString())
        BannerNotifications.recordChanges(context,afterTire,afterOil)
        BannerNotifications.recordChanges(context,afterTire,afterOil) // retransmission must not duplicate history
        NotificationHistory(context).use { history ->
            val rows = history.recent(id)
            assertEquals(2,rows.size)
            assertEquals("정비 기록을 저장했어!",rows.single {it.bannerKey=="MAINTENANCE_DONE"}.title)
            assertEquals("엔진오일 교체 완료!",rows.single {it.bannerKey=="OIL_DONE"}.title)
            assertTrue(rows.all {it.delivery==NotificationDelivery.Delivered})
        }
        // Persist and reopen an isolated journal to exercise subtype serialization, without publishing it.
        val journal = CareJournal(context,id)
        journal.saveAuthority(afterOil)
        val reopened = JSONArray(CareJournal(context,id).authority()!!.records)
        assertEquals("TIRE_PRESSURE",reopened.getJSONObject(0).getString("maintenanceType"))
        assertEquals("ENGINE_OIL",reopened.getJSONObject(1).getString("maintenanceType"))
        journal.deleteVehicle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.cacheDir,name).outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        image.recycle()
    }
}

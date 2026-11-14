package com.example.vemoring

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import com.vemoring.companion.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry

class EngineOilUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun editorOffersOilReplacementWithoutGeneralMaintenanceItems() {
        var saved:CareRecord?=null
        val record=CareRecord(date=LocalDate.now(CARE_ZONE),kind=CareKind.OIL,title="엔진오일 교체")
        compose.setContent {MaterialTheme {RecordDialog(record,record.date,{},save={saved=it})}}
        compose.onNodeWithText("정비 항목").assertDoesNotExist()
        compose.onNodeWithText("타이어 공기압").assertDoesNotExist()
        compose.onNodeWithText("기록 저장").performClick()
        compose.runOnIdle {assertEquals(MaintenanceType.ENGINE_OIL,saved!!.maintenanceType)}
    }
    @Test fun homeShowsOilCarePeriodAndNoWarningPanel() {
        val date=LocalDate.now(CARE_ZONE)
        val oil=EngineOilAssessment(true,0.0,0,reason="엔진오일 교체 시기 도래",percent=0.0,dueDate=date)
        val data=AppData(CarProfile("현대","아반떼","베모링"),complete=true)
        compose.setContent {MaterialTheme {
            HomeScreen(data,BatteryReading(),{},MascotMood.OIL,
                condition=CareCondition(CareSignals(oilDue=true),oilAssessment=oil),demo=true)
        }}
        compose.onNodeWithText("엔진오일 관리 잔여율").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("차량 안전 경고").assertDoesNotExist()
        val screenshot=compose.onRoot().captureToImage().asAndroidBitmap()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        java.io.File(context.cacheDir,"engine-oil-home.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}

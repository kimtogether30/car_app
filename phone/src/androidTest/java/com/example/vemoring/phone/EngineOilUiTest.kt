package com.example.vemoring.phone

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vemoring.companion.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class EngineOilUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun editorSavesExplicitEngineOilReplacement() {
        var saved:Entry?=null
        val record=Entry(kind=MAINTENANCE,title="엔진오일 교체",date=LocalDate.now(CARE_ZONE),detail="")
        compose.setContent {MaterialTheme {RecordEditor(record,record.date,false,{}, {saved=it})}}
        compose.onNodeWithText("정비 항목").assertDoesNotExist()
        compose.onNodeWithText("타이어 공기압").assertDoesNotExist()
        compose.onNodeWithTag("form-save").performClick()
        compose.runOnIdle {assertEquals(MaintenanceType.ENGINE_OIL,saved!!.maintenanceType)}
    }
}

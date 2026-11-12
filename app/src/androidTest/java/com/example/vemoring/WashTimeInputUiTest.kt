package com.example.vemoring

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class WashTimeInputUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun manualTimestampIsReachableWithVehicleTypography() {
        val date=LocalDate.now(com.vemoring.companion.CARE_ZONE).minusDays(1)
        val record=CareRecord(date=date,kind=CareKind.WASH,title="수동 세차",source="Manual",
            completedAt=date.atTime(14,30).atZone(com.vemoring.companion.CARE_ZONE).toInstant().toString())
        compose.setContent {MaterialTheme(typography=com.example.vemoring.ui.theme.Typography) {
            RecordDialog(record,date,close={},save={})
        }}
        compose.onNodeWithText("14:30").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("기록 저장").assertIsDisplayed()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val screenshot=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.cacheDir,"wash-time-form.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
        screenshot.recycle()
    }
}

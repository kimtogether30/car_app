package com.example.vemoring

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Renders the calendar from in-memory records only; no store is opened or written. */
class CalendarKindsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multipleRecordsShowCountAndMaintenanceLabel() {
        val month = YearMonth.now(ZoneId.of("Asia/Seoul"))
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        // Days that are already in the past (or today) in the current month, so none are future dates.
        val busy = month.atDay(1)
        val single = month.atDay(minOf(2, today.dayOfMonth)).takeIf { it != busy } ?: busy
        val records = listOf(
            CareRecord(date = busy, kind = CareKind.WASH, title = "손세차"),
            CareRecord(date = busy, kind = CareKind.FUEL, title = "주유"),
            CareRecord(date = busy, kind = CareKind.OIL, title = "경고등 해결"),
            CareRecord(date = single, kind = CareKind.OIL, title = "정비")).distinctBy { it.date to it.kind }
        compose.setContent {
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography) { CalendarScreen(records) { _, _ -> } }
        }
        compose.onNodeWithText("${records.count { it.date == busy }}건").assertIsDisplayed()
        compose.onAllNodesWithText("● 정비").assertCountEquals(1)
        compose.onAllNodesWithText("엔진오일 교체", substring = true).assertCountEquals(0)
        // Optional screenshot into the app cache (not its saved data; delete after pulling): -e capture 1
        if (InstrumentationRegistry.getArguments().getString("capture") == "1") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "calendar-kinds.png").outputStream()
                .use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

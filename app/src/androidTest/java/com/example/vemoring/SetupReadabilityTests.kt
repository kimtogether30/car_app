package com.example.vemoring

import android.graphics.Bitmap
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class SetupReadabilityTests {
    @get:Rule val compose = createComposeRule()

    @Test fun allSetupStepsRemainReachableWithVehicleTypography() {
        var finished = false
        compose.setContent {
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography) {
                SetupScreen { _, _ -> finished = true }
            }
        }
        capture("01-brand")
        compose.onNodeWithText("현대").performClick()
        capture("02-model")
        compose.onNodeWithText("아반떼", useUnmergedTree = true).performScrollTo().performClick()
        capture("03-fuel")
        compose.onNodeWithText("전기차").performClick()
        capture("04-dates")
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        capture("05-nickname")
        compose.onNode(hasSetTextAction()).performTextInput("가독성 확인")
        compose.onNodeWithText("다음").performScrollTo().performClick()
        capture("06-summary")
        compose.onNodeWithText("VEMORING 시작하기").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(finished) }
    }

    @Test fun koreanNicknameIsSavedAndCompletionShowsHomeWithoutDroppingRecords() {
        val suffix = java.util.UUID.randomUUID().toString()
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("setup_${suffix}_$name", mode)
        }
        val store = GarageStore(context)
        val existing = CareRecord(date = LocalDate.now().minusDays(1), kind = CareKind.WASH, title = "보존할 기록")
        store.save(AppData(CarProfile("현대", "코나", "기존 이름"), listOf(existing), true))
        compose.setContent {
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography) {
                GarageApp(storeOverride = store, startWithSetup = true)
            }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("현대").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("현대").performClick()
        compose.onNodeWithText("아반떼", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("전기차").performScrollTo().performClick()
        compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("몽글이 한글")
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNodeWithText("VEMORING 시작하기").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("차량 컨디션").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("홈", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("홈", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("몽글이 한글", store.load().profile.nickname)
            assertEquals(listOf(existing), store.load().records)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.filesDir, "setup-previews")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

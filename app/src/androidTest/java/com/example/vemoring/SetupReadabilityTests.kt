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
        compose.onNodeWithText("휘발유").performScrollTo().performClick()
        capture("04-dates")
        compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNodeWithText("세차 날짜를 선택하거나 ‘세차일이 기억이 안 나요’를 체크해 주세요.").assertExists()
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        compose.onNodeWithText("6개월 이상 전").performScrollTo().performClick()
        compose.onNodeWithText("교체 당시 실제 누적거리 (km, 선택)").assertDoesNotExist()
        capture("04-dates-without-mileage")
        compose.onNodeWithText("다음").assertIsEnabled()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        capture("05-nickname")
        compose.onNode(hasSetTextAction() and hasText("차량 닉네임", substring = true)).performTextInput("가독성 확인")
        compose.onNodeWithText("다음").performScrollTo().performClick()
        capture("06-summary")
        compose.onNodeWithText("고정바 추가 방법").performScrollTo().performClick()
        compose.onNodeWithText("고정바에 VEMORING 추가").assertIsDisplayed()
        compose.onNodeWithText("2. VEMORING 아이콘을 길게 눌러 주세요.").assertIsDisplayed()
        capture("07-pin-guide")
        compose.onNodeWithText("나중에 할게요").performClick()
        compose.onNodeWithText("고정바에 VEMORING 추가").assertDoesNotExist()
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
        compose.onNode(hasSetTextAction() and hasText("차량 닉네임", substring = true)).performTextInput("몽글이 한글")
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNodeWithText("VEMORING 시작하기").performScrollTo().performClick()
        // Saving removes the setup root before the home root is attached. Wait through that transition.
        compose.waitUntil(15000) { runCatching {compose.onAllNodesWithText("이번 주 날씨").fetchSemanticsNodes().isNotEmpty()}.getOrDefault(false) }
        compose.onNodeWithText("홈", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("몽글이 한글", store.load().profile.nickname)
            assertEquals(listOf(existing), store.load().records)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Compose idle does not wait for the system IME dismissal animation.
        android.os.SystemClock.sleep(400)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.filesDir, "setup-previews")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

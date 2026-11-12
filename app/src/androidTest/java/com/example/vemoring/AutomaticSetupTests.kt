package com.example.vemoring

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.vemoring.vehicle.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AutomaticSetupTests {
    @get:Rule val compose = createComposeRule()

    @Test fun completeIdentitySkipsVehicleQuestionsAndSavesConfirmedValues() {
        var saved: CarProfile? = null
        var requestedGuide = false
        compose.setContent {
            MaterialTheme { SetupScreen(VehicleIdentityReading(ReadingState.Available,
                ConfirmedVehicleInfo("현대", "코나", "전기차", "12라 1234")),
                onDockChoice = { requestedGuide = it }) { profile, _ -> saved = profile } }
        }
        compose.onNodeWithText("나에게 맞는 차량 관리의 시작 · 1 / 4").assertIsDisplayed()
        compose.onNodeWithText("어떤 브랜드의\n차량을 타고 계신가요?").assertDoesNotExist()
        compose.onNodeWithText("마지막 충전일:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("차량 닉네임", substring = true)).performTextInput("우리 코나")
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNodeWithText("하단 바에 VEMORING을\n추가할까요?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("네, 추가 방법 볼게요").performScrollTo().performClick()
        compose.onNodeWithText("전기차 · 12라 1234").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("VEMORING 시작하기").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("코나", saved?.model)
            assertEquals("전기차", saved?.fuel)
            assertEquals("12라 1234", saved?.plate)
            assertEquals("우리 코나", saved?.nickname)
            assertTrue(requestedGuide)
        }
    }

    @Test fun partialIdentityAsksFuelAndBackSkipsDetectedQuestions() {
        compose.setContent { MaterialTheme {
            SetupScreen(VehicleIdentityReading(ReadingState.Available, ConfirmedVehicleInfo("기아", "니로"))) { _, _ -> }
        } }
        compose.onNodeWithText("나에게 맞는 차량 관리의 시작 · 1 / 5").assertIsDisplayed()
        compose.onNodeWithText("주유 방식을 선택해 주세요").assertIsDisplayed()
        compose.onNodeWithText("하이브리드").performScrollTo().performClick()
        compose.onNodeWithText("‹ 이전").performScrollTo().performClick()
        compose.onNodeWithText("주유 방식을 선택해 주세요").assertIsDisplayed()
        compose.onNodeWithText("차종을 선택해 주세요").assertDoesNotExist()
    }

    @Test fun choosingManualEntryDoesNotJumpWhenLateIdentityArrives() {
        var reading by mutableStateOf(VehicleIdentityReading(ReadingState.Loading))
        compose.setContent { MaterialTheme { SetupScreen(reading) { _, _ -> } } }
        compose.onNodeWithText("직접 입력하기").performClick()
        compose.onNodeWithText("현대").performClick()
        compose.runOnIdle { reading = VehicleIdentityReading(ReadingState.Available, ConfirmedVehicleInfo("기아", "니로", "전기차")) }
        compose.onNodeWithText("차종을 선택해 주세요").assertIsDisplayed()
        compose.onNodeWithText("현대 차종").assertIsDisplayed()
    }
}

package com.example.vemoring

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GarageLayoutTests {
    @get:Rule val compose = createComposeRule()
    private val data = AppData(CarProfile("현대", "코나", "몽글이", fuel = "전기차"), complete = true)

    @Test fun vehicleLayoutCentersCharacterAboveStatusWithoutProfileEditor() {
        compose.setContent {
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography) {
                Box(Modifier.width(1240.dp)) { HomeScreen(data, BatteryReading(), {}) }
            }
        }
        val character = compose.onNodeWithContentDescription("파란색 기본 캐릭터").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithText("남은 배터리").fetchSemanticsNode().boundsInRoot
        assertTrue(status.top > character.bottom)
        compose.onNodeWithText("수정").assertDoesNotExist()
        compose.onNodeWithText("현대 코나").assertIsDisplayed()
        compose.onNodeWithText("이번 주 날씨").assertIsDisplayed()
        compose.onNodeWithText("오늘").assertIsDisplayed()
        compose.onNodeWithText("정비소").assertIsDisplayed()
    }

    @Test fun narrowLayoutWithLargerTextKeepsStatusAndSearchReachable() {
        compose.setContent {
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                    Box(Modifier.width(420.dp)) { HomeScreen(data, BatteryReading(), {}) }
                }
            }
        }
        compose.onNodeWithText("VEMORING").assertIsDisplayed()
        compose.onNodeWithText("남은 배터리").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이번 주 날씨").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("정비소").performScrollTo().assertIsDisplayed()
    }
}

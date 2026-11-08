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
        val status = compose.onNodeWithText("차량 컨디션").fetchSemanticsNode().boundsInRoot
        assertTrue(status.top > character.bottom)
        assertTrue(kotlin.math.abs(status.center.x - character.center.x) < 4f)
        compose.onNodeWithText("수정").assertDoesNotExist()
        compose.onNodeWithText("현대 코나").assertIsDisplayed()
        compose.onNodeWithText("1 / 3 · 좌우로 넘겨보세요").assertIsDisplayed()
        compose.onNodeWithContentDescription("다음 장소 배너").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
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
        compose.onNodeWithText("차량 컨디션").performScrollTo().assertIsDisplayed()
        // Scroll the vertical page via the caption outside the horizontal pager.
        compose.onNodeWithText("1 / 3 · 좌우로 넘겨보세요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("가까운 주유소 찾기").assertIsDisplayed()
    }
}

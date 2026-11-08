package com.example.vemoring

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NearbyPlacesTests {
    @get:Rule val compose = createComposeRule()

    @Test fun buttonsAndSwipeShowOneCategoryAndSearchTheSelectedPlace() {
        var searched = ""
        compose.setContent {
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography) {
                NearbyPlacesBanner { searched = it }
            }
        }
        compose.onNodeWithText("가까운 주유소 찾기").assertIsDisplayed()
        compose.onNodeWithContentDescription("이전 장소 배너").assertIsNotEnabled()
        compose.onNodeWithContentDescription("다음 장소 배너").performClick()
        compose.onNodeWithText("가까운 세차장 찾기").assertIsDisplayed()
        compose.onNodeWithContentDescription("가까운 세차장 검색").performClick()
        compose.runOnIdle { assertEquals("세차장", searched) }
        compose.onNodeWithContentDescription("다음 장소 배너").performClick()
        compose.onNodeWithText("가까운 정비소 찾기").assertIsDisplayed()
        compose.onNodeWithContentDescription("다음 장소 배너").assertIsNotEnabled()
        compose.onNodeWithContentDescription("가까운 정비소 검색").performTouchInput { swipeRight() }
        compose.onNodeWithText("가까운 세차장 찾기").assertIsDisplayed()
    }
}

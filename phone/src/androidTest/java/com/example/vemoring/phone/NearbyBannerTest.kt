package com.example.vemoring.phone

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NearbyBannerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allCategoriesKeepTheReferenceWidthAndSameHeightWithLargeText() {
        var scale by mutableFloatStateOf(1f)
        var searched = ""
        compose.setContent {
            val deviceDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(deviceDensity, scale)) {
                MaterialTheme { Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).testTag("reference"))
                    NearbyPlaces { searched = it }
                } }
            }
        }
        listOf(1f, 1.6f).forEach { fontScale ->
            compose.runOnIdle { scale = fontScale }
            compose.onNodeWithText("주유소", useUnmergedTree = true).performClick()
            val initial = compose.onNodeWithTag("nearby-banner").fetchSemanticsNode().boundsInRoot
            val reference = compose.onNodeWithTag("reference").fetchSemanticsNode().boundsInRoot
            assertEquals(reference.width, initial.width, 0.5f)
            listOf("세차장", "정비소", "주유소").forEach { category ->
                compose.onNodeWithText(category, useUnmergedTree = true).performClick()
                val bounds = compose.onNodeWithTag("nearby-banner").fetchSemanticsNode().boundsInRoot
                assertEquals(initial.width, bounds.width, 0.5f)
                assertEquals(initial.height, bounds.height, 0.5f)
                compose.onNodeWithTag("nearby-banner").performClick()
                compose.runOnIdle { assertEquals(category, searched) }
            }
        }
    }

    @Test fun chosenProviderReceivesTheCurrentCategoryAsASearchQuery() {
        val query = "세차장 & 정비소"
        val naver = phoneMapIntent("com.nhn.android.nmap", query, "com.example.vemoring.phone")
        assertEquals("nmap", naver.data!!.scheme)
        assertEquals(query, naver.data!!.getQueryParameter("query"))
        assertEquals("com.example.vemoring.phone", naver.data!!.getQueryParameter("appname"))
        val kakao = phoneMapIntent("net.daum.android.map", query, "com.example.vemoring.phone")
        assertEquals("kakaomap", kakao.data!!.scheme)
        assertEquals(query, kakao.data!!.getQueryParameter("q"))
        assertEquals("net.daum.android.map", kakao.`package`)
        assertThrows(IllegalArgumentException::class.java) { phoneMapIntent("unsupported.example", query, "com.example.vemoring.phone") }
    }
}

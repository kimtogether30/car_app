package com.example.vemoring

import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NavigationSearchTests {
    @get:Rule val compose = createComposeRule()

    @Test fun providerLinksCarryTheSelectedKoreanKeywordAndTargetPackage() {
        for (keyword in listOf("주유소", "세차장", "정비소")) {
            val naver = NavigationApp.NAVER.searchIntent(keyword, "com.example.vemoring")
            assertEquals("nmap", naver.data!!.scheme)
            assertEquals("search", naver.data!!.host)
            assertEquals(keyword, naver.data!!.getQueryParameter("query"))
            assertEquals("com.example.vemoring", naver.data!!.getQueryParameter("appname"))
            assertEquals("com.nhn.android.nmap", naver.`package`)
            val kakao = NavigationApp.KAKAO.searchIntent(keyword, "com.example.vemoring")
            assertEquals("kakaomap", kakao.data!!.scheme)
            assertEquals("search", kakao.data!!.host)
            assertEquals(keyword, kakao.data!!.getQueryParameter("q"))
            assertEquals("net.daum.android.map", kakao.`package`)
            assertEquals(Intent.ACTION_VIEW, kakao.action)
        }
    }

    @Test fun pickerHidesUnavailableAppsAndPassesTheSelectedProvider() {
        var selected: NavigationApp? = null
        compose.setContent {
            MaterialTheme(typography = com.example.vemoring.ui.theme.Typography) {
                NavigationAppPicker("세차장", {}, setOf(NavigationApp.PLEOS, NavigationApp.NAVER)) {
                    selected = it
                }
            }
        }
        compose.onNodeWithText("세차장 검색 · 지도 앱 선택").assertIsDisplayed()
        compose.onNodeWithText("카카오맵").assertDoesNotExist()
        compose.onNodeWithText("네이버지도").performClick()
        compose.runOnIdle { assertEquals(NavigationApp.NAVER, selected) }
    }
}

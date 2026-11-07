package com.example.vemoring

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GarageUiTests {
    @get:Rule val compose = createComposeRule()
    @Test fun registerCarCreateAndEditRecordThenChangeProfile() {
        val suffix = java.util.UUID.randomUUID().toString()
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("ui_${suffix}_$name", mode)
        }
        val store = GarageStore(context)
        compose.setContent { MaterialTheme { GarageApp(store) } }
        compose.onNodeWithText("현대").performClick()
        compose.onNodeWithText("아반떼", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("전기차").performClick()
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithText("다음").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("테스트 몽글이")
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("VEMORING 시작하기").performScrollTo().performClick()
        compose.onNodeWithText("관리 기록", useUnmergedTree = true).performClick()
        compose.onNodeWithText("＋ 추가").performClick()
        compose.onNodeWithText("기록 이름").performTextInput("첫 세차")
        compose.onNodeWithText("상세 내용 (장소·금액 등)").performTextInput("20000원")
        compose.onNodeWithText("기록 저장").performClick()
        compose.onNodeWithText("세차 · 첫 세차").performScrollTo().performClick()
        compose.onNodeWithText("기록 이름").performTextReplacement("수정한 세차")
        compose.onNodeWithText("기록 저장").performClick()
        compose.onNodeWithText("세차 · 수정한 세차").assertExists()
        compose.onNodeWithText("내 차", useUnmergedTree = true).performClick()
        compose.onNodeWithText("닉네임").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("새 몽글이")
        compose.onNodeWithText("저장하기").performClick()
        compose.runOnIdle {
            check(store.load().profile.nickname == "새 몽글이")
            check(store.load().records.single().title == "수정한 세차")
            check(store.load().records.single().detail == "20000원")
        }
    }
}

@RunWith(AndroidJUnit4::class)
class GarageStartupTests {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualLauncherActivityDisplaysSetupOrHome() {
        compose.waitUntil(timeoutMillis = 15000) {
            compose.onAllNodesWithText("VEMORING").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("VEMORING").assertIsDisplayed()
    }
}

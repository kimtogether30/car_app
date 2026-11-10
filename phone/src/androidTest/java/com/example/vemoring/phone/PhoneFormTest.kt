package com.example.vemoring.phone

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PhoneFormTest {
    @get:Rule val compose = createComposeRule()

    @Test fun koreanMemoCanBeSavedWhileTheKeyboardIsOpen() {
        var saved: Entry? = null
        compose.setContent { MaterialTheme { RecordEditor(null, LocalDate.now(), false, {}, { saved = it }) } }
        compose.onNodeWithTag("record-title").performClick().performTextInput("한글 세차 기록")
        compose.onNodeWithTag("record-memo").performClick().performTextInput("서울 세차장 · 30,000원 · 깨끗하게 세차했어요")
        compose.onNodeWithTag("form-save").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("한글 세차 기록", saved!!.title)
            assertEquals("서울 세차장 · 30,000원 · 깨끗하게 세차했어요", saved!!.detail)
        }
    }

    @Test fun explicitKeyboardCloseAndImeDoneKeepTheDraftAndSaveActionReachable() {
        var saved: Entry? = null
        compose.setContent { MaterialTheme { RecordEditor(null, LocalDate.now(), false, {}, { saved = it }) } }
        compose.onNodeWithTag("record-title").performClick().performTextInput("주유 기록")
        compose.onNodeWithTag("record-memo").performClick().performTextInput("한글 입력 중")
        compose.onNodeWithTag("hide-keyboard").assertIsDisplayed().performClick()
        compose.onNodeWithTag("record-memo").assertTextContains("한글 입력 중")
        compose.onNodeWithTag("record-memo").performClick().performImeAction()
        compose.onNodeWithTag("form-save").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("한글 입력 중", saved!!.detail) }
    }
}

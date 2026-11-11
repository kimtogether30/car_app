package com.vemoring.companion

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.LocaleList

/** Suggest Korean for free text without forcing or replacing the user's system IME. */
val KoreanTextKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, hintLocales = LocaleList("ko-KR"))

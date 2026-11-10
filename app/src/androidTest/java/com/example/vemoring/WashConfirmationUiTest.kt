package com.example.vemoring

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Injected qualified visit only. Uses isolated preferences and never changes the saved garage. */
class WashConfirmationUiTest {
    @get:Rule val compose = createComposeRule()
    private val id = UUID.randomUUID().toString()
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getDatabasePath(name: String) = base.getDatabasePath("banner_test_${id}_$name")
        override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) = base.openOrCreateDatabase("banner_test_${id}_$name",mode,factory)
        override fun openOrCreateDatabase(name: String,mode: Int,factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,errorHandler: android.database.DatabaseErrorHandler?) = base.openOrCreateDatabase("banner_test_${id}_$name",mode,factory,errorHandler)
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("banner_test_${id}_$name", mode)
    }

    private fun start() = runBlocking {
        val profile = JSONObject().put("nickname", "시험 차량").put("model", "시험 모델").put("plate", "")
            .put("fuel", "휘발유").put("washAlert", 30).put("fuelAlert", 20).put("notifications", false)
        val content = JSONObject().put("profile", profile).put("records", JSONArray()).put("mood", "NORMAL")
            .put("message", "시험 공급자 · 실제 SDK 세차 감지 아님").put("demo", false)
            .put("fuel", JSONObject.NULL).put("battery", JSONObject.NULL).put("cleanliness", JSONObject.NULL).put("oilRemainingKm", JSONObject.NULL)
        CareExchange.receive(context, MirrorSnapshot(id, 1, System.currentTimeMillis(), content.toString()))
        CareExchange.detected(context, id, DetectedWash("qualified-visit", Instant.now().minusSeconds(60)))
        compose.setContent {
            MaterialTheme {
                val authority by CareExchange.canonical.collectAsState()
                val scope = rememberCoroutineScope()
                val score by produceState<Int?>(null, authority) {
                    value = withContext(Dispatchers.IO) {
                        val store = CareJournal(context, id)
                        store.score(JSONArray(store.authority()!!.records)).displayed
                    }
                }
                // The component's loader uses LocalContext: supply the same isolated test context.
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides context) {
                    val isolated by rememberWashConfirmation(id, authority)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                        Text("시험 공급자 · 실제 SDK 세차 감지 아님")
                        isolated?.let { candidate -> WashConfirmationBanner(candidate, false,
                            onReject = { scope.launch { CareExchange.cancel(context, id, candidate.visitId) } },
                            onDismiss = { scope.launch { CareExchange.dismissConfirmation(context, id, candidate.visitId) } }, vehicle = true) }
                        Text("청결도: ${score?.let { "$it%" } ?: "기록 없음"}")
                    }
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("청결도: 100%").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("청결도: 100%").assertExists()
        compose.onNodeWithText("세차한 것 같아!").assertIsDisplayed()
    }

    @After fun cleanup() { CareJournal(context, id).deleteVehicle() }

    @Test fun wrongRecognitionButtonImmediatelyRestoresUnknownAndDoesNotResurrect() {
        start()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "screencap -p /data/local/tmp/wash-confirmation-banner.png").let { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        compose.onNodeWithTag("wash-reject").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("wash-confirmation").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("청결도: 기록 없음").assertExists()
        assertFalse(runBlocking { CareExchange.detected(context, id, DetectedWash("qualified-visit", Instant.now())) })
    }
    @Test fun closingBannerKeepsTheAutomaticWash() {
        start()
        compose.onNodeWithTag("wash-dismiss").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("wash-confirmation").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("청결도: 100%").assertExists()
    }
}

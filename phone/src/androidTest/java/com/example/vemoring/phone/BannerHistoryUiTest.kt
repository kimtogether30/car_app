package com.example.vemoring.phone

import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BannerHistoryUiTest {
    @get:Rule val compose=createComposeRule()
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private val id="history-${UUID.randomUUID()}"
    private val context=object:ContextWrapper(base) {
        override fun getApplicationContext():Context=this
        override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?)=base.openOrCreateDatabase("${id}_$name",mode,factory)
        override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?,errorHandler:android.database.DatabaseErrorHandler?)=base.openOrCreateDatabase("${id}_$name",mode,factory,errorHandler)
    }
    private fun shell(command:String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {input->input.readBytes()}
        }
    }
    @After fun cleanup() {
        base.getSystemService(NotificationManager::class.java).activeNotifications.filter {it.tag?.startsWith("$id:")==true}
            .forEach {base.getSystemService(NotificationManager::class.java).cancel(it.tag,it.id)}
        NotificationHistory(context).use {it.deleteVehicle(id)}
        base.deleteDatabase("${id}_care_notification_history_v1.db")
    }
    @Test fun suppliedArtworkAndDeliveryStatusRenderInBothThemesAndReadStatePersists() {
        val d=BannerCatalog["WASH_DONE"]
        val event=CareNotificationEntry(id,"done",d.key,d.title,d.body,d.mood,d.art,System.currentTimeMillis(),source="Simulation")
        BannerNotifications.dispatch(context,event)
        Thread.sleep(400)
        shell("cmd statusbar expand-notifications");Thread.sleep(400)
        shell("screencap -p /data/local/tmp/banner-phone-notification.png")
        shell("cmd statusbar collapse")
        base.getSystemService(NotificationManager::class.java).cancel("$id:done",3010)
        Thread.sleep(400)
        var dark by mutableStateOf(false)
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config) {
                MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
                    NotificationHistoryDialog(id,onDismiss={})
                }
            }
        }
        compose.waitUntil(5000) {compose.onAllNodesWithTag("banner-art:WASH_DONE",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("banner-art:WASH_DONE",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithText("시스템 알림 게시됨").assertIsDisplayed()
        Thread.sleep(300) // Wait for asynchronous bitmap decode, without UI polling in production.
        shell("screencap -p /data/local/tmp/banner-history-light.png")
        compose.runOnIdle {dark=true}
        compose.waitForIdle();Thread.sleep(300)
        shell("screencap -p /data/local/tmp/banner-history-dark.png")
        compose.onNodeWithTag("banner-art:WASH_DONE",useUnmergedTree=true).performClick()
        compose.onNodeWithText("시험 입력으로 생성한 알림이에요. 실제 차량 수신을 뜻하지 않아요.").assertIsDisplayed()
        compose.waitUntil(3000) {NotificationHistory(context).use {it.get(id,"done")!!.read}}
        assertTrue(NotificationHistory(context).use {it.get(id,"done")!!.read})
    }
}

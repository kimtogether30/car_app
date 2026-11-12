package com.example.vemoring.phone

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WashTimeInputUiTest {
    @Test fun manualTimestampRemainsEditableInRecordForm() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".verification"))
        val date=LocalDate.now(com.vemoring.companion.CARE_ZONE).minusDays(1)
        val entry=Entry(kind="세차",title="수동 세차",date=date,detail="메모",source="Manual",
            completedAt=date.atTime(14,30).atZone(com.vemoring.companion.CARE_ZONE).toInstant().toString())
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity {activity -> activity.setContent {MaterialTheme {RecordEditor(entry,date,false,{}, {})}}}
            instrumentation.waitForIdleSync()
            val deadline=android.os.SystemClock.uptimeMillis()+5000
            var found=false
            while(!found && android.os.SystemClock.uptimeMillis()<deadline) {
                val nodes=java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
                instrumentation.uiAutomation.rootInActiveWindow?.let(nodes::add)
                while(nodes.isNotEmpty()) {
                    val node=nodes.removeFirst()
                    if(node.text?.contains("14:30")==true && node.isEnabled) found=true
                    repeat(node.childCount) {node.getChild(it)?.let(nodes::add)}
                }
                if(!found) android.os.SystemClock.sleep(100)
            }
            val screenshot=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            java.io.File(instrumentation.targetContext.cacheDir,"wash-time-form.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            screenshot.recycle()
            assertTrue("Manual completion time must remain available in the editor",found)
        }
    }
}

package com.example.vemoring.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.PhoneWashAssistControl
import org.junit.Test
import org.junit.Assert.assertTrue
import java.io.File
import android.graphics.Bitmap

class PhoneWashAssistUiTest {
    @Test fun permissionEntryIsReadableWithoutStartingLocationService() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        check(instrumentation.targetContext.packageName.endsWith(".verification"))
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
        scenario.onActivity {activity -> activity.setContent {MaterialTheme {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {PhoneWashAssistControl()}}}}
        instrumentation.waitForIdleSync()
        val deadline=android.os.SystemClock.uptimeMillis()+5000
        var found=false
        while(!found && android.os.SystemClock.uptimeMillis()<deadline) {
            val nodes=java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            instrumentation.uiAutomation.rootInActiveWindow?.let {nodes.add(it)}
            var inspected=0
            while(nodes.isNotEmpty() && inspected++<500 && !found) {
                val node=nodes.removeFirst()
                found=node.text?.contains("세차 위치 보완 켜기")==true || node.contentDescription?.contains("세차 위치 보완 켜기")==true
                repeat(node.childCount) {node.getChild(it)?.let(nodes::add)}
            }
            if(!found) android.os.SystemClock.sleep(100)
        }
        val screenshot=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(instrumentation.targetContext.cacheDir,"wash-assist-control.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
        screenshot.recycle()
        assertTrue("Permission entry must be visible to accessibility",found)
        }
    }
}

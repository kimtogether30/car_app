package com.example.vemoring

import android.content.ComponentName
import android.content.Intent
import android.app.ActivityManager
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotLauncherTests {
    @Test fun switchingIconsAndRelaunchingKeepsOneTask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(ActivityManager::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun open(mood: MascotMood) {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(context.packageName, "${context.packageName}.${mood.launcher}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
        }
        fun background() {
            context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            // Allow onStop's worker-thread icon update to finish before this explicit test override.
            android.os.SystemClock.sleep(300)
        }
        try {
            background()
            MascotLauncher.sync(context, MascotMood.NORMAL)
            open(MascotMood.NORMAL)
            for (mood in listOf(MascotMood.FUEL, MascotMood.ANGRY, MascotMood.EXHAUSTED, MascotMood.NORMAL)) {
                background()
                MascotLauncher.sync(context, mood)
                assertTrue(context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName,
                    "${context.packageName}.MainActivity")) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
                open(mood)
                open(mood)
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                while (manager.appTasks.isEmpty() && android.os.SystemClock.uptimeMillis() < deadline) {
                    android.os.SystemClock.sleep(50)
                }
                assertEquals("Duplicate app tasks at $mood", 1, manager.appTasks.size)
            }
        } finally {
            background()
            MascotLauncher.sync(context, MascotMood.NORMAL)
            open(MascotMood.NORMAL)
        }
    }

    @Test fun eachMoodHasExactlyOneLaunchableIconAndRestoresDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        try {
            MascotMood.entries.forEach { mood ->
                MascotLauncher.sync(context, mood)
                val entries = pm.queryIntentActivities(query, 0)
                assertEquals(1, entries.size)
                assertEquals("${context.packageName}.${mood.launcher}", entries.single().activityInfo.name)
                assertTrue(pm.getActivityIcon(ComponentName(context.packageName,
                    "${context.packageName}.${mood.launcher}")).intrinsicWidth > 0)
            }
        } finally {
            MascotLauncher.sync(context, MascotMood.NORMAL)
        }
    }
}

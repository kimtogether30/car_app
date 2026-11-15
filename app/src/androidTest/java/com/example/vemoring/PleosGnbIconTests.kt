package com.example.vemoring

import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.database.DatabaseUtils
import android.net.Uri
import android.os.SystemClock
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeNotNull
import java.io.File

/** Runs on the vehicle AVD only; no vehicle registration or companion data is written. */
class PleosGnbIconTests {
    @Test fun refreshesOwnShortcutsPreservingPositionsAndOtherApps() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        assumeNotNull(context.packageManager.resolveContentProvider("com.android.systemui.gnb", 0))
        val uri = Uri.parse("content://com.android.systemui.gnb/gnb-mixed")
        val columns = arrayOf("_id", "id", "intent_uri", "leftOrder", "rightOrder", "bottomOrder")
        fun rows(): List<List<String?>> = resolver.query(uri, columns, null, null, "_id")!!.use { cursor ->
            buildList { while (cursor.moveToNext()) add(columns.indices.map { cursor.getString(it) }) }
        }
        val before = rows()
        val own = before.filter { row -> MascotMood.entries.any { mood ->
            row[1] == ComponentName(context.packageName, "$APP_CLASS_PREFIX.${mood.launcher}").flattenToString() ||
                row[1] == ComponentName(context.packageName,
                    "$APP_CLASS_PREFIX.${mood.launcher.replace("Launcher", "Gnb")}").flattenToString()
        } }
        val originals = mutableMapOf<String, ContentValues>()
        resolver.query(uri, null, null, null, null)!!.use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(cursor.getColumnIndexOrThrow("_id"))
                if (own.any { it[0] == key }) originals[key] = ContentValues().also {
                    DatabaseUtils.cursorRowToContentValues(cursor, it)
                }
            }
        }
        assertTrue("Need an existing VEMORING GNB shortcut", own.isNotEmpty())
        val originalMood = MascotMood.entries.first { mood ->
            val state = context.packageManager.getComponentEnabledSetting(ComponentName(context.packageName,
                "$APP_CLASS_PREFIX.${mood.launcher}"))
            state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        try {
            val automation = instrumentation.uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            fun contains(node: AccessibilityNodeInfo?, description: String): Boolean {
                if (node == null) return false
                if (node.contentDescription?.toString() == description) return true
                return (0 until node.childCount).any { contains(node.getChild(it), description) }
            }
            val requested = InstrumentationRegistry.getArguments().getString("mood")
            MascotMood.entries.filter { requested == null || it.name == requested }.forEach { mood ->
                MascotLauncher.sync(context, mood)
                val after = rows().associateBy { it[0] }
                before.forEach { old ->
                    val updated = after[old[0]]
                    if (old in own) {
                        if (updated == null) assertTrue("A pinned shortcut was deleted", old.drop(3).all { it == "-1" })
                        else {
                            assertEquals(PleosGnbIcon.component(context, mood).flattenToString(), updated[1])
                            assertEquals("Shortcut placement changed", old.drop(3), updated.drop(3))
                        }
                    } else assertEquals("Another app shortcut changed", old, updated)
                }
                assertEquals("Duplicate recent VEMORING entries", 1, after.values.count {
                    it[1] == PleosGnbIcon.component(context, mood).flattenToString() && it.drop(3).all { position -> position == "-1" }
                })
                context.startActivity(Intent().setClassName(context.packageName, "$APP_CLASS_PREFIX.StatePreviewActivity")
                    .putExtra("stage", when(mood) {
                        MascotMood.NORMAL -> 0; MascotMood.FUEL -> 1; MascotMood.WASH -> 2
                        MascotMood.OIL -> 3; MascotMood.ANGRY -> 4; MascotMood.EXHAUSTED -> 5
                    }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                val deadline = SystemClock.uptimeMillis() + 30000
                while (SystemClock.uptimeMillis() < deadline &&
                    automation.windows.none { contains(it.root, mood.description) }) SystemClock.sleep(250)
                assertTrue("Preview did not render the matching character: $mood",
                    automation.windows.any { contains(it.root, mood.description) })
                SystemClock.sleep(if (requested == null) 1000 else 20000)
                runCatching { instrumentation.uiAutomation.takeScreenshot() }.getOrNull()?.let { bitmap ->
                    File(context.filesDir, "gnb-${mood.name}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            MascotLauncher.sync(context, originalMood)
            own.forEach { old ->
                val count = resolver.update(uri, ContentValues().apply { put("id", old[1]); put("intent_uri", old[2]) },
                    "_id = ?", arrayOf(old[0]!!))
                if (count == 0) resolver.insert(uri, originals.getValue(old[0]!!))
            }
        }
    }
}

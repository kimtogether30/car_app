package com.example.vemoring.phone

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * The disposable verification app has the same VEMORING launcher entries as the real phone app, so after a test run
 * it showed a second VEMORING icon in the app list. This runner gives the test copy its normal launcher entry only
 * while tests run, and hides every launcher entry when the run ends. Data and notifications are not touched, and it
 * works no matter who starts the tests (Gradle, adb, another assistant).
 */
class VerificationRunner : AndroidJUnitRunner() {
    override fun onStart() {
        setLaunchers(visible = true)
        super.onStart()
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        setLaunchers(visible = false)
        super.finish(resultCode, results)
    }

    private fun setLaunchers(visible: Boolean) {
        val context = targetContext
        if (!context.packageName.endsWith(".verification")) return // never hide the real phone app
        val prefix = MainActivity::class.java.name.substringBeforeLast('.')
        val pm = context.packageManager
        listOf("LauncherNormal", "LauncherFuel", "LauncherWash", "LauncherOil", "LauncherAngry", "LauncherExhausted").forEach { alias ->
            val state = if (visible && alias == "LauncherNormal") PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching {
                pm.setComponentEnabledSetting(ComponentName(context.packageName, "$prefix.$alias"), state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}

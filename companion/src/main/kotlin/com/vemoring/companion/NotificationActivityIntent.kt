package com.vemoring.companion

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** A notification must outlive mood changes that disable its original launcher alias. Caller uses IO. */
internal fun notificationActivityIntent(context: Context): Intent {
    val pm=context.packageManager
    val launch=requireNotNull(pm.getLaunchIntentForPackage(context.packageName)) { "No application launcher" }
    val entry=requireNotNull(launch.component)
    val activity=pm.getActivityInfo(entry,PackageManager.MATCH_DISABLED_COMPONENTS).targetActivity ?: entry.className
    // Resolve the manifest namespace rather than guessing from applicationId (verification has a suffix).
    return launch.setClassName(context.packageName,activity)
}

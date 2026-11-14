package com.vemoring.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The link the user turned on: peer address and role only. Cleared by an explicit user stop or a lost bond. */
internal object MirrorLinkSettings {
    data class Saved(val host: Boolean, val peer: String)
    private const val PREFS = "mirror_link_v1"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remember(context: Context, host: Boolean, peer: String) {
        prefs(context).edit().putBoolean("host", host).putString("peer", peer).apply()
    }
    fun forget(context: Context) { prefs(context).edit().clear().apply() }
    fun saved(context: Context): Saved? {
        val p = prefs(context)
        val peer = p.getString("peer", null) ?: return null
        return Saved(p.getBoolean("host", false), peer)
    }
}

/**
 * Resumes a user-enabled Bluetooth link after reboot or app update. Both broadcasts are documented exemptions
 * for starting a foreground service from the background; connectedDevice is not a boot-restricted FGS type.
 */
class MirrorLinkRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            runCatching { MirrorBluetoothService.restore(context) }
    }
}

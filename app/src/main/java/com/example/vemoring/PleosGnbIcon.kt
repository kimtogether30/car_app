package com.example.vemoring

import android.content.ComponentName
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/** Best-effort compatibility with the exported provider in Pleos Connect 2.1.0.
 * This is not a public Pleos SDK contract. Never insert shortcuts or change their positions.
 * Other platforms (or a future permission restriction) keep the ordinary launcher behaviour.
 * Called on the same worker as component switching, never from Compose recomposition.
 */
internal object PleosGnbIcon {
    private const val AUTHORITY = "com.android.systemui.gnb"
    private val uri = Uri.parse("content://$AUTHORITY/gnb-mixed")
    fun component(context: Context, mood: MascotMood) = ComponentName(context.packageName,
        "${context.packageName}.${mood.launcher}")
    private data class Shortcut(val key: Long, val id: String, val original: String,
        val updated: String, val left: Int, val right: Int, val bottom: Int, val timestamp: Long) {
        val unplaced get() = left == -1 && right == -1 && bottom == -1
    }

    fun sync(context: Context, mood: MascotMood) {
        try {
            val target = component(context, mood)
            val aliases = MascotMood.entries.flatMap {
                listOf(component(context, it), ComponentName(context.packageName,
                    "${context.packageName}.${it.launcher.replace("Launcher", "Gnb")}")) // Migrate local diagnostic aliases.
            }.toSet()
            val changes = arrayListOf<ContentProviderOperation>()
            val shortcuts = mutableListOf<Shortcut>()
            context.contentResolver.query(uri, arrayOf("_id", "id", "type", "intent_uri",
                "leftOrder", "rightOrder", "bottomOrder", "timestamp"),
                "type = ?", arrayOf("mixed_gnb_app"), null)?.use { rows ->
                while (rows.moveToNext()) {
                    val original = rows.getString(3) ?: continue
                    val intent = runCatching { Intent.parseUri(original, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: continue
                    val component = intent.component ?: continue
                    // Both identity and intent must belong to one of our declared aliases.
                    if (component !in aliases || rows.getString(1) != component.flattenToString()) continue
                    val updated = Intent(intent).setComponent(target).toUri(Intent.URI_INTENT_SCHEME)
                    shortcuts += Shortcut(rows.getLong(0), rows.getString(1), original, updated,
                        rows.getInt(4), rows.getInt(5), rows.getInt(6), rows.getLong(7))
                }
            }
            // Alias switching and package replacement can leave several recent entries for
            // the same launch. Keep the newest; never merge pinned slots or different extras.
            val redundant = shortcuts.filter { it.unplaced }.groupBy { it.updated }.values.flatMap { group ->
                group.sortedWith(compareByDescending<Shortcut> { it.timestamp }.thenBy { it.key }).drop(1)
            }.map { it.key }.toSet()
            shortcuts.forEach { shortcut ->
                if (shortcut.key in redundant) {
                    changes += ContentProviderOperation.newDelete(uri)
                        .withSelection("_id = ? AND id = ? AND intent_uri = ? AND leftOrder = -1 AND rightOrder = -1 AND bottomOrder = -1",
                            arrayOf(shortcut.key.toString(), shortcut.id, shortcut.original))
                        .withExpectedCount(1).build()
                } else if (shortcut.original != shortcut.updated || shortcut.id != target.flattenToString()) {
                    changes += ContentProviderOperation.newUpdate(uri)
                        .withSelection("_id = ? AND id = ? AND intent_uri = ?",
                            arrayOf(shortcut.key.toString(), shortcut.id, shortcut.original))
                        .withValue("id", target.flattenToString())
                        .withValue("intent_uri", shortcut.updated)
                        .withExpectedCount(1)
                        .build()
                }
            }
            if (changes.isNotEmpty()) context.contentResolver.applyBatch(AUTHORITY, changes)
        } catch (error: Exception) {
            // No root, shell commands, polling, or modification of system application files.
            Log.w("PleosGnbIcon", "GNB shortcut refresh unavailable; launcher icon still updated", error)
        }
    }
}

package com.vemoring.companion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

data class MirrorLink(val active: Boolean = false, val message: String = "차량 미연결", val running: Boolean = false)
data class ReceivedMirror(val snapshot: MirrorSnapshot, val receivedAt: Long)

/** All preference and socket I/O runs outside the UI thread. No timers or polling. */
object MirrorBus {
    val link = MutableStateFlow(MirrorLink())
    internal val outgoing = MutableStateFlow<MirrorSnapshot?>(null)
    private val cached = MutableStateFlow<ReceivedMirror?>(null)
    val received = cached.asStateFlow()
    private val mutex = Mutex()

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (cached.value == null) {
                cached.value = runCatching { MirrorCacheStore(context).load() }.getOrNull()
            }
        }
    }
    internal suspend fun loadOutgoing(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (outgoing.value == null) outgoing.value = context.getSharedPreferences("companion_publisher_v1", Context.MODE_PRIVATE)
                .getString("snapshot", null)?.let { runCatching { MirrorSnapshot.decode(it) }.getOrNull() }
        }
    }

    suspend fun publish(context: Context, content: String, vehicleId: String? = null) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (outgoing.value?.content == content && (vehicleId == null || outgoing.value?.vehicleId == vehicleId)) return@withLock
            val prefs = context.getSharedPreferences("companion_publisher_v1", Context.MODE_PRIVATE)
            val id = vehicleId ?: prefs.getString("vehicleId", null) ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val revision = maxOf(now, prefs.getLong("revision", 0) + 1)
            val next = MirrorSnapshot.decode(MirrorSnapshot(id, revision, now, content).encode())
            check(prefs.edit().putString("vehicleId", id).putLong("revision", revision).putString("snapshot", next.encode()).commit())
            outgoing.value = next
        }
    }

    internal suspend fun receive(context: Context, snapshot: MirrorSnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val journal = MirrorJournal(cached.value?.snapshot)
            if (journal.accept(snapshot)) {
                val now = System.currentTimeMillis()
                MirrorCacheStore(context).save(ReceivedMirror(snapshot, now))
                cached.value = ReceivedMirror(snapshot, now)
            }
        }
    }
}

/** A single atomic preference edit; no delete or reset on transport failure. Caller uses IO. */
internal class MirrorCacheStore(context: Context, name: String = "companion_mirror_v1") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun load(): ReceivedMirror? {
        val raw = prefs.getString("snapshot", null) ?: return null
        val snapshot = MirrorSnapshot.decode(raw)
        return ReceivedMirror(snapshot, prefs.getLong("receivedAt", snapshot.capturedAt))
    }
    @android.annotation.SuppressLint("UseKtx") // Keep commit's success/failure result.
    fun save(received: ReceivedMirror) {
        val encoded = received.snapshot.encode()
        MirrorSnapshot.decode(encoded)
        check(prefs.edit().putString("snapshot", encoded).putLong("receivedAt", received.receivedAt).commit())
    }
}

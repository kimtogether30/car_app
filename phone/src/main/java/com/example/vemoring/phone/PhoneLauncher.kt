package com.example.vemoring.phone

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.vemoring.companion.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/** Same rule as the vehicle app's CareSignals.mood(): one issue = its color, two = angry, three = exhausted. */
internal fun moodFor(fuel: Boolean, wash: Boolean, warning: Boolean): String = when (listOf(fuel, wash, warning).count { it }) {
    3 -> "EXHAUSTED"; 2 -> "ANGRY"; 1 -> if (fuel) "FUEL" else if (wash) "WASH" else "OIL"; else -> "NORMAL"
}

internal fun moodMessage(mood: String): String = when (mood) {
    "FUEL" -> "배가 고파요…\n에너지를 채워줄래요?"
    "WASH" -> "꼬질꼬질해졌어요…\n깨끗하게 씻겨줄래요?"
    "OIL" -> "엔진오일 교체 시기예요!\n교체 기록을 확인해 주세요."
    "ANGRY" -> "두 가지 관리가 필요해요!\n하나씩 챙겨줄래요?"
    "EXHAUSTED" -> "챙겨줄 게 세 가지예요…\n기운을 되찾게 도와줘요."
    else -> "안녕! 준비됐나요?\n오늘도 함께 달려요."
}

/**
 * The mood the phone home screen shows: the vehicle's state when vehicle data exists, otherwise the
 * phone-only wash record. Returns null for demo data, which never changes the real launcher icon. Caller uses IO.
 */
internal fun currentPhoneMood(context: Context, received: ReceivedMirror?, authority: CareAuthority?): String? {
    val selected=context.getSharedPreferences("phone_vehicle_selection",Context.MODE_PRIVATE).getString("selectedId",null)
        ?.takeIf {CareJournal(context,it).authority()!=null}
    val current=if(selected!=null) MirrorBus.vehicles.value[selected] else received
    if (current != null) {
        val content = JSONObject(current.snapshot.content)
        if (content.optBoolean("demo")) return null
        val vehicleId = current.snapshot.vehicleId
        val state = authority?.takeIf { it.vehicleId == vehicleId } ?: CareJournal(context, vehicleId).authority()
            ?: return content.optString("mood", "NORMAL")
        val score = CareJournal(context, vehicleId).score(JSONArray(state.records)).displayed
        val signals = content.optJSONObject("signals")
        return moodFor(EnergyPolicy.low(JSONObject(state.profile), content) == true,
            score?.let { it <= JSONObject(state.profile).getInt("washAlert") } == true,
            EngineOilCatalog.assess(context,JSONObject(state.profile).put("vehicleId",vehicleId),state.records).due==true)
    }
    if(selected!=null) CareJournal(context,selected).authority()?.let {state ->
        val score=CareJournal(context,selected).score(JSONArray(state.records)).displayed
        return moodFor(false,score!=null && score<=JSONObject(state.profile).getInt("washAlert"),
            EngineOilCatalog.assess(context,JSONObject(state.profile).put("vehicleId",selected),state.records).due==true)
    }
    val raw = context.getSharedPreferences("phone_ui_v1", Context.MODE_PRIVATE).getString("data", null) ?: return "NORMAL"
    val j = JSONObject(raw)
    val entries = j.optJSONArray("entries") ?: JSONArray()
    val washes = (0 until entries.length()).map { entries.getJSONObject(it) }.filter { it.optString("kind") == "세차" }.map {
        WashBaseline(it.getString("id"), LocalDate.parse(it.getString("date")),
            it.optString("completedAt").takeUnless { v -> v.isBlank() || v == "null" }?.let(Instant::parse),
            it.optString("visitId").takeUnless { v -> v.isBlank() || v == "null" }, it.optBoolean("cancelled"), it.optString("source", "Manual"))
    }
    val clean = WashPolicy.score(washes, emptyList(), Instant.now()).displayed
    return if (clean != null && clean <= j.optInt("washAlert", 30)) "WASH" else "NORMAL"
}

/** Swaps the enabled launcher alias, like the vehicle app's MascotLauncher. Worker thread only. */
internal object PhoneLauncher {
    private val prefix = MainActivity::class.java.name.substringBeforeLast('.')
    private val aliases = linkedMapOf("NORMAL" to "LauncherNormal", "FUEL" to "LauncherFuel", "WASH" to "LauncherWash",
        "OIL" to "LauncherOil", "ANGRY" to "LauncherAngry", "EXHAUSTED" to "LauncherExhausted")
    /** Last alias this process applied. Only this object changes the launcher components. */
    private var applied: String? = null

    @Synchronized
    fun sync(context: Context, mood: String) {
        val target = aliases[mood] ?: aliases.getValue("NORMAL")
        if (target == applied) return
        val pm = context.packageManager
        fun component(alias: String) = ComponentName(context.packageName, "$prefix.$alias")
        val changed = aliases.values.filter { alias ->
            val current = pm.getComponentEnabledSetting(component(alias))
            val enabled = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias == "LauncherNormal")
            enabled != (alias == target)
        }
        if (changed.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.setComponentEnabledSettings(changed.map {
                    PackageManager.ComponentEnabledSetting(component(it), if(it==target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                })
            } else {
                // Enable the new entry first so there is never a moment without a launcher icon.
                pm.setComponentEnabledSetting(component(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                changed.filter { it != target }.forEach {
                    pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
        }
        applied = target
    }
}

/**
 * Keeps the phone launcher icon in step with the vehicle state. Reacts only to received vehicle data,
 * phone-owned record changes and leaving the app; no polling. Like the vehicle app, the icon is swapped
 * only while VEMORING is not on screen.
 */
@OptIn(FlowPreview::class)
class PhoneApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val foreground = MutableStateFlow(false)
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++; foreground.value = true }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0); foreground.value = started > 0
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        scope.launch {
            MirrorBus.load(this@PhoneApplication)
            combine(MirrorBus.received, CareExchange.canonical, foreground,CareExchange.weatherRevision) { received, authority, visible,_ ->
                Triple(received, authority, visible)
            }.debounce(500).collectLatest { (received, authority, visible) ->
                if (visible) return@collectLatest
                withContext(Dispatchers.IO) {
                    val mood = runCatching { currentPhoneMood(this@PhoneApplication, received, authority) }
                        .onFailure { android.util.Log.w("PhoneLauncher", "Mood unavailable", it) }.getOrNull() ?: return@withContext
                    if (!foreground.value) runCatching { PhoneLauncher.sync(this@PhoneApplication, mood) }
                        .onFailure { android.util.Log.w("PhoneLauncher", "Icon update failed", it) }
                }
            }
        }
    }
}

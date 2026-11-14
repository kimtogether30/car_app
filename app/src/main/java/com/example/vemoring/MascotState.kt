package com.example.vemoring

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Unknown signals never create warnings. Supply validated live data here later. */
data class CareSignals(val lowFuel: Boolean? = null, val needsWash: Boolean? = null, val oilDue: Boolean? = null) {
    fun mood(): MascotMood = when (listOf(lowFuel, needsWash, oilDue).count { it == true }) {
        3 -> MascotMood.EXHAUSTED
        2 -> MascotMood.ANGRY
        1 -> when { lowFuel == true -> MascotMood.FUEL; needsWash == true -> MascotMood.WASH; else -> MascotMood.OIL }
        else -> MascotMood.NORMAL
    }
}

enum class MascotMood(val character: Int, val launcher: String, val description: String, val message: String) {
    NORMAL(R.drawable.mascot_normal, "LauncherNormal", "파란색 기본 캐릭터", "안녕! 준비됐나요?\n오늘도 함께 달려요."),
    FUEL(R.drawable.mascot_fuel, "LauncherFuel", "배고픈 노란색 캐릭터", "배가 고파요…\n에너지를 채워줄래요?"),
    WASH(R.drawable.mascot_wash, "LauncherWash", "세차가 필요한 갈색 캐릭터", "꼬질꼬질해졌어요…\n깨끗하게 씻겨줄래요?"),
    OIL(R.drawable.mascot_oil, "LauncherOil", "엔진오일 교체 시기를 알리는 보라색 캐릭터", "엔진오일 교체 시기예요!\n교체 기록을 확인해 주세요."),
    ANGRY(R.drawable.mascot_angry, "LauncherAngry", "분노한 빨간색 캐릭터", "두 가지 관리가 필요해요!\n하나씩 챙겨줄래요?"),
    EXHAUSTED(R.drawable.mascot_exhausted, "LauncherExhausted", "영혼이 빠져나가는 차콜색 캐릭터", "챙겨줄 게 세 가지예요…\n기운을 되찾게 도와줘요."),
}

/** Worker-thread operation, invoked only on mood changes; skips unchanged launcher entries. */
object MascotLauncher {
    /** Last mood this process applied. Only this object changes the launcher components. */
    private var applied: MascotMood? = null
    @Synchronized
    fun sync(context: Context, mood: MascotMood) {
        if (mood == applied) return
        syncComponents(context, mood)
        applied = mood
    }
    private fun syncComponents(context: Context, mood: MascotMood) {
        val pm = context.packageManager
        // The screen itself must stay enabled even when the launcher color changes.
        val screen = ComponentName(context.packageName, "${context.packageName}.MainActivity")
        if (pm.getComponentEnabledSetting(screen) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(screen, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        fun component(m: MascotMood) = ComponentName(context.packageName, "${context.packageName}.${m.launcher}")
        fun desired(m: MascotMood) = if (m == mood) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val changed = MascotMood.entries.filter { m ->
            val current = pm.getComponentEnabledSetting(component(m))
            val enabled = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && m == MascotMood.NORMAL)
            enabled != (m == mood)
        }
        if (changed.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(changed.map {
                PackageManager.ComponentEnabledSetting(component(it), desired(it), PackageManager.DONT_KILL_APP)
            })
        } else {
            pm.setComponentEnabledSetting(component(mood), desired(mood), PackageManager.DONT_KILL_APP)
            changed.filter { it != mood }.forEach {
                pm.setComponentEnabledSetting(component(it), desired(it), PackageManager.DONT_KILL_APP)
            }
        }
    }
}

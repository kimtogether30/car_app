package com.vemoring.companion

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** IO callers only. Missing or ambiguous model data never supplies an invented interval. */
object EngineOilCatalog {
    @Volatile private var data: JSONObject? = null
    @Synchronized fun rule(context: Context, model: String, fuel: String): EngineOilRule? {
        val j=data ?: runCatching {context.assets.open("engine-oil-intervals.json").bufferedReader().use {JSONObject(it.readText())}}
            .getOrNull()?.also {data=it} ?: return null
        return select(j,model,fuel)
    }
    fun select(data:JSONObject,model:String,fuel:String):EngineOilRule? {
        if(fuel in setOf("전기차","수소차","수소전기차") || model.endsWith(" EV")) return null
        require(data.getInt("schemaVersion")==1)
        val models=data.getJSONArray("models")
        val item=(0 until models.length()).map {models.getJSONObject(it)}.singleOrNull { m ->
            val brand=m.getString("brand")
            val variants=m.getJSONArray("catalogVariants")
            (0 until variants.length()).any {n -> model=="$brand ${variants.getString(n)}"}
        } ?: return null
        if(!item.getBoolean("oilApplicable")) return null
        val ref=item.getJSONObject("conservativeModelReference")
        return EngineOilRule(item.getString("id")+":"+data.getString("updatedAt"),model,fuel,
            ref.getDouble("distanceKm"),ref.getInt("calendarMonths"),
            "차종별 최단 참고값 · 연식·엔진 미지정 · "+item.getString("sourceKind")).also {require(it.valid())}
    }
    fun excluded(model:String,fuel:String):Boolean {
        if(fuel in setOf("전기차","수소차","수소전기차") || model.endsWith(" EV")) return true
        val models=data?.optJSONArray("models") ?: return false
        return (0 until models.length()).map {models.getJSONObject(it)}.any {m ->
            val variants=m.getJSONArray("catalogVariants")
            !m.getBoolean("oilApplicable") && (0 until variants.length()).any {model==m.getString("brand")+" "+variants.getString(it)}
        }
    }
    fun assess(context: Context, profile: JSONObject, records: String, today: LocalDate = LocalDate.now(CARE_ZONE)): EngineOilAssessment {
        return EngineOilLedger(context,profile.getString("vehicleId")).assess(profile,records,today)
    }
}

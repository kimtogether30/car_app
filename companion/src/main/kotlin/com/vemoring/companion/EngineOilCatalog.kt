package com.vemoring.companion

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** IO callers only. Missing or ambiguous model data never supplies an invented interval. */
object EngineOilCatalog {
    /** "brand variant" -> matching model entries, built once; more than one match stays ambiguous (no rule). */
    private class Index(val updatedAt:String,val byName:Map<String,List<JSONObject>>)
    @Volatile private var index: Index? = null
    private val nonEngine=setOf("전기차","수소차","수소전기차")
    private fun nonEngine(model:String,fuel:String)=fuel in nonEngine || model.endsWith(" EV")
    private fun index(data:JSONObject):Index {
        require(data.getInt("schemaVersion")==1)
        val models=data.getJSONArray("models")
        val byName=HashMap<String,MutableList<JSONObject>>()
        for(i in 0 until models.length()) {
            val m=models.getJSONObject(i);val variants=m.getJSONArray("catalogVariants")
            for(n in 0 until variants.length()) byName.getOrPut(m.getString("brand")+" "+variants.getString(n)) {mutableListOf()}.add(m)
        }
        return Index(data.getString("updatedAt"),byName)
    }
    @Synchronized private fun loaded(context:Context):Index? = index ?: runCatching {
        context.assets.open("engine-oil-intervals.json").bufferedReader().use {index(JSONObject(it.readText()))}
    }.getOrNull()?.also {index=it}
    fun rule(context: Context, model: String, fuel: String): EngineOilRule? = loaded(context)?.let {select(it,model,fuel)}
    fun select(data:JSONObject,model:String,fuel:String):EngineOilRule? = select(index(data),model,fuel)
    private fun select(index:Index,model:String,fuel:String):EngineOilRule? {
        if(nonEngine(model,fuel)) return null
        val item=index.byName[model]?.singleOrNull() ?: return null
        if(!item.getBoolean("oilApplicable")) return null
        val ref=item.getJSONObject("conservativeModelReference")
        return EngineOilRule(item.getString("id")+":"+index.updatedAt,model,fuel,
            ref.getDouble("distanceKm"),ref.getInt("calendarMonths"),
            "차종별 최단 참고값 · 연식·엔진 미지정 · "+item.getString("sourceKind")).also {require(it.valid())}
    }
    fun excluded(model:String,fuel:String):Boolean =
        nonEngine(model,fuel) || index?.byName?.get(model)?.any {!it.getBoolean("oilApplicable")}==true
    fun assess(context: Context, profile: JSONObject, records: String, today: LocalDate = LocalDate.now(CARE_ZONE)): EngineOilAssessment {
        return EngineOilLedger(context,profile.getString("vehicleId")).assess(profile,records,today)
    }
}

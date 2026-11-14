package com.vemoring.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Small per-registration ledger. All methods run on IO; no timer, SDK lease or Compose disk reads. */
class EngineOilLedger(private val context:Context,val vehicleId:String) {
    private val prefs=context.getSharedPreferences("engine_oil_v1",Context.MODE_PRIVATE)
    private fun load()=prefs.getString(vehicleId,null)?.let(::JSONObject) ?: JSONObject()
    private fun save(j:JSONObject) {check(prefs.edit().putString(vehicleId,j.toString()).commit())}
    fun observe(km:Double?,at:Long?,now:Long=System.currentTimeMillis())=synchronized(LOCK) {
        if(km==null || !km.isFinite() || km !in 0.0..10_000_000.0 || at==null || now-at !in 0..60_000) return@synchronized
        val j=load()
        if(at<=j.optLong("meterAt") || j.has("currentKm") && km<j.getDouble("currentKm")) return@synchronized
        if(j.has("enrolled") && !j.has("anchorKm")) j.put("anchorKm",km)
        j.put("currentKm",km).put("meterAt",at);save(j)
    }
    fun assess(profile:JSONObject,records:String,today:LocalDate=LocalDate.now(CARE_ZONE)):EngineOilAssessment=synchronized(LOCK) {
        val j=load(); val old=j.toString()
        j.put("vehicleId",vehicleId)
        if(!j.has("enrolled")) {
            j.put("enrolled",today.toString()).put("initialIds",JSONArray().also {a ->
                val r=JSONArray(records);repeat(r.length()) {if(isEngineOilRecord(r.getJSONObject(it))) a.put(r.getJSONObject(it).getString("id"))}
            })
            if(j.has("currentKm")) j.put("anchorKm",j.getDouble("currentKm"))
            profile.optDouble("oilBaselineKm",Double.NaN).takeIf {it.isFinite() && it>=0}?.let {j.put("initialKm",it)}
            val initial=initialOilDate(profile.optString("oilEstimate"),today)
            initial?.let {j.put("initialDate",it.toString()).put("initialApproximate",true)}
        }
        val selected=EngineOilCatalog.rule(context,profile.optString("model"),profile.optString("fuel"))
        j.put("excluded",EngineOilCatalog.excluded(profile.optString("model"),profile.optString("fuel")))
        if(selected==null) j.remove("rule") else if(j.optJSONObject("rule")?.toString()!=selected.json().toString()) j.put("rule",selected.json())
        if(old!=j.toString()) save(j)
        EngineOilCalculation.assess(j,selected,JSONArray(records),today,profile.optString("fuel") in setOf("전기차","수소차","수소전기차"))
    }
    fun frame():JSONObject=synchronized(LOCK) {load()}
    fun attachCurrentMileage(before:JSONArray,after:JSONArray,now:Long=System.currentTimeMillis()):JSONArray=synchronized(LOCK) {
        val j=load();val km=j.optDouble("currentKm",Double.NaN)
        val result=JSONArray(after.toString())
        if(!km.isFinite() || now-j.optLong("meterAt") !in 0..60_000) return@synchronized result
        val old=(0 until before.length()).map {before.getJSONObject(it)}.associateBy {it.getString("id")}
        repeat(result.length()) {n -> val r=result.getJSONObject(n); val prior=old[r.getString("id")]
            if(isEngineOilRecord(r) && !r.optBoolean("cancelled") && r.optString("source","Manual")=="Manual" &&
                r.optString("date")==LocalDate.now(CARE_ZONE).toString() &&
                (prior==null || prior.optString("date")!=r.getString("date"))) r.put("odometerKm",km)
        }
        result
    }
    /** Vehicle is the source of measured km; preserve the older enrollment when receiving its ledger. */
    fun importVehicle(frame:JSONObject)=synchronized(LOCK) {
        require(frame.toString().length<=256_000)
        require(frame.optString("vehicleId")==vehicleId)
        val local=load()
        if(!frame.has("enrolled")) return@synchronized
        require(!LocalDate.parse(frame.getString("enrolled")).isAfter(LocalDate.now(CARE_ZONE)))
        require(frame.optJSONArray("initialIds")?.length()?.let {it<=5000}!=false)
        val next=JSONObject((if(!local.has("enrolled") || frame.getString("enrolled")<=local.getString("enrolled")) frame else local).toString())
        if(local.has("currentKm"))
            next.put("currentKm",local.getDouble("currentKm")).put("meterAt",local.getLong("meterAt"))
        if(frame.optLong("meterAt")>local.optLong("meterAt")) {
            val km=frame.optDouble("currentKm",Double.NaN)
            if(km.isFinite() && km>=local.optDouble("currentKm",0.0)) next.put("currentKm",km).put("meterAt",frame.getLong("meterAt"))
        }
        if(next.toString()!=local.toString()) save(next)
    }
    fun clear()=synchronized(LOCK) {check(prefs.edit().remove(vehicleId).commit())}
    companion object {private val LOCK=Any()}
}

fun initialOilDate(value:String,enrolled:LocalDate):LocalDate?=when(value) {
    "약 1개월 전","1개월 이내"->enrolled.minusMonths(1)
    "약 3개월 전","3개월 이내"->enrolled.minusMonths(3)
    "6개월 이상 전","6개월 이내"->enrolled.minusMonths(6)
    else->null
}

object EngineOilCalculation {
    fun assess(ledger:JSONObject,rule:EngineOilRule?,records:JSONArray,today:LocalDate,electric:Boolean=false):EngineOilAssessment {
        if(electric || ledger.optBoolean("excluded")) return EngineOilAssessment(false,reason="엔진오일 교체 대상이 아니에요")
        if(rule==null || !rule.valid()) return EngineOilAssessment(null,reason="차량별 가혹조건 교체 주기 데이터 없음")
        val reference="가혹조건 ${rule.severeKm?.toInt()}km / ${rule.severeMonths}개월 · ${rule.source}"
        val enrolled=LocalDate.parse(ledger.getString("enrolled"))
        val latest=(0 until records.length()).map {records.getJSONObject(it)}.filter {
            isEngineOilRecord(it) && !it.optBoolean("cancelled") && !it.optBoolean("deleted") &&
                runCatching {LocalDate.parse(it.getString("date"))<=today}.getOrDefault(false)
        }.maxWithOrNull(compareBy<JSONObject> {it.getString("date")}.thenBy {it.optString("completedAt")}.thenBy {it.getString("id")})
        val initialIds=ledger.optJSONArray("initialIds") ?: JSONArray()
        val initial=latest?.let {r -> (0 until initialIds.length()).any {initialIds.getString(it)==r.getString("id")}}==true
        val date=latest?.getString("date")?.let(LocalDate::parse)
            ?: ledger.optString("initialDate").takeIf {it.isNotBlank()}?.let(LocalDate::parse)
        val current=ledger.optDouble("currentKm",Double.NaN).takeIf {it.isFinite() && it>=0}
        val anchor=ledger.optDouble("anchorKm",Double.NaN).takeIf {it.isFinite() && it>=0}
        val baseline=latest?.optDouble("odometerKm",Double.NaN)?.takeIf {it.isFinite() && it>=0}
            ?: ledger.optDouble("initialKm",Double.NaN).takeIf {it.isFinite() && it>=0 && (initial || latest==null && initialIds.length()==0)}
        val sinceEnrollment=if(current!=null && anchor!=null && current>=anchor) current-anchor else null
        val estimated=date!=null && baseline==null && (initial || latest==null)
        val used=when {
            baseline!=null && current!=null && current>=baseline -> current-baseline
            estimated && sinceEnrollment!=null && date!!<=enrolled -> ChronoUnit.DAYS.between(date,enrolled)*20.0+sinceEnrollment
            else->null
        }
        val cycle=(latest?.getString("id") ?: if(date!=null) "initial:$date" else "unknown:$enrolled")+":"+rule.id
        if(date==null && baseline!=null) {
            val km=if(current!=null && current>=baseline) rule.severeKm!!-(current-baseline) else null
            return EngineOilAssessment(if(km!=null && km<=0) true else null,remainingKm=km,
                reason="교체일 미등록 · 실제 교체 거리 기준 안내",cycleId=cycle,reference=reference)
        }
        if(date==null) {
            val installationDue=sinceEnrollment?.let {it>=rule.severeKm!!}==true || !today.isBefore(enrolled.plusMonths(rule.severeMonths!!.toLong()))
            return EngineOilAssessment(if(installationDue) true else null,reason=if(installationDue)
                "설치 이후 교체 주기에 도달했어요. 그동안 교체하지 않았다면 교체 시기를 확인해 주세요" else "교체 이력 미등록",cycleId=cycle,reference=reference)
        }
        val remaining=EngineOilPolicy.assess(rule,date,if(used!=null) 0.0 else null,used,today)
        val expiry=date.plusMonths(rule.severeMonths!!.toLong())
        val period=ChronoUnit.DAYS.between(date,expiry).toDouble()
        val timeUsed=ChronoUnit.DAYS.between(date,today).toDouble()/period
        val percent=used?.let {100.0*(1.0-maxOf(it/rule.severeKm!!,timeUsed))}?.coerceIn(0.0,100.0)
        val forecast=used?.let {today.plusDays(kotlin.math.ceil((rule.severeKm!!-it).coerceAtLeast(0.0)/20.0).toLong())}
        return remaining.copy(reference=reference,percent=percent,estimated=estimated,cycleId=cycle,dueDate=if(forecast==null || expiry<forecast) expiry else forecast,
            reason=if(remaining.due==true) "엔진오일 교체 시기 도래" else if(estimated) "가입 전 20km/일 추정 + 가입 후 실제 거리" else remaining.reason)
    }
}

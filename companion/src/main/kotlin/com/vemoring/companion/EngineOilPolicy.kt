package com.vemoring.companion

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/** Verified severe-service schedules only. No generic mileage or time fallback. */
data class EngineOilRule(val id:String,val model:String,val fuel:String,val severeKm:Double?,val severeMonths:Int?,val source:String) {
    fun valid()=id.isNotBlank() && model.isNotBlank() && fuel.isNotBlank() && source.isNotBlank() &&
        (severeKm!=null || severeMonths!=null) &&
        (severeKm==null || severeKm.isFinite() && severeKm>0 && severeKm<=100_000) &&
        (severeMonths==null || severeMonths in 1..60)
    fun json()=JSONObject().put("id",id).put("model",model).put("fuel",fuel).put("severeKm",severeKm)
        .put("severeMonths",severeMonths).put("source",source)
    companion object {
        fun from(j:JSONObject)=EngineOilRule(j.getString("id"),j.getString("model"),j.getString("fuel"),
            if(j.isNull("severeKm")) null else j.getDouble("severeKm"),
            if(j.isNull("severeMonths")) null else j.getInt("severeMonths"),j.getString("source")).also {require(it.valid())}
    }
}

data class EngineOilAssessment(val due:Boolean?,val remainingKm:Double?=null,val remainingDays:Long?=null,val reason:String, val percent:Double?=null, val estimated:Boolean=false, val cycleId:String="", val dueDate:LocalDate?=null, val reference:String="")
object EngineOilPolicy {
    fun assess(rule:EngineOilRule?,lastDate:LocalDate?,baselineKm:Double?,currentKm:Double?,today:LocalDate,
               electric:Boolean=false):EngineOilAssessment {
        if(electric) return EngineOilAssessment(false,reason="전기차는 엔진오일 교체 대상이 아니에요")
        if(rule==null || !rule.valid()) return EngineOilAssessment(null,reason="차량별 가혹조건 교체 주기 데이터 준비 중")
        if(lastDate==null || lastDate>today) return EngineOilAssessment(null,reason="마지막 엔진오일 교체일을 등록해 주세요")
        val km=if(rule.severeKm!=null && baselineKm!=null && currentKm!=null && baselineKm.isFinite() &&
            currentKm.isFinite() && baselineKm>=0 && currentKm>=baselineKm) rule.severeKm-(currentKm-baselineKm) else null
        val days=rule.severeMonths?.let {ChronoUnit.DAYS.between(today,lastDate.plusMonths(it.toLong()))}
        val due=when {km?.let {it<=0}==true || days?.let {it<=0}==true -> true
            (rule.severeKm==null || km!=null) && (rule.severeMonths==null || days!=null) -> false
            else -> null}
        return EngineOilAssessment(due,km,days,when(due){true->"가혹조건 엔진오일 교체 주기를 넘겼어요"
            false->"차량별 가혹조건 교체 주기 기준";else->"교체 당시 거리와 확인된 누적 거리가 필요해요"})
    }
}

/** Only explicit engine-oil work is a baseline. Old general repairs never become oil changes. */
fun isEngineOilRecord(record:JSONObject)=record.optString("kind") in setOf("maintenance","oil") &&
    record.optString("maintenanceType")==MaintenanceType.ENGINE_OIL.name

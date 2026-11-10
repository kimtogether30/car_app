package com.vemoring.companion
import org.json.JSONObject
object VehicleCatalogData {
    private val catalog = JSONObject("""{
  "현대": {
    "승용 / 세단 & 해치백": {
      "아반떼": ["아반떼", "아반떼 N", "아반떼 하이브리드"],
      "쏘나타": ["쏘나타 디 엣지", "쏘나타 하이브리드"],
      "그랜저": ["그랜저", "그랜저 하이브리드"],
      "아이오닉 6": ["아이오닉 6"]},
    "SUV & CUV": {
      "베뉴": ["베뉴"],
      "코나": ["코나", "코나 하이브리드", "코나 EV"],
      "투싼": ["투싼", "투싼 하이브리드"],
      "싼타페": ["싼타페", "싼타페 하이브리드"],
      "팰리세이드": ["팰리세이드"],
      "아이오닉 5": ["아이오닉 5", "아이오닉 5 N"],
      "아이오닉 9": ["아이오닉 9"],
      "넥쏘": ["넥쏘"]},
    "MPV / RV": {
      "스타리아": ["스타리아", "스타리아 라운지", "스타리아 하이브리드"]},
    "소형 상용 / 트럭": {
      "포터 II": ["포터 II", "포터 II EV"]}},
  "기아": {
    "승용 / 세단 & 해치백": {
      "K3": ["K3"],
      "K4": ["K4"],
      "K5": ["K5", "K5 하이브리드"],
      "K8": ["K8", "K8 하이브리드"],
      "K9": ["K9"]},
    "SUV & CUV": {
      "셀토스": ["셀토스"],
      "니로": ["니로 하이브리드", "니로 EV", "니로 플러스"],
      "스포티지": ["스포티지", "스포티지 하이브리드"],
      "쏘렌토": ["쏘렌토", "쏘렌토 하이브리드"],
      "모하비": ["모하비"],
      "EV3": ["EV3"],
      "EV6": ["EV6", "EV6 GT"],
      "EV9": ["EV9"]},
    "RV / 해치백 / 박스카": {
      "레이": ["레이", "레이 EV"],
      "모닝": ["모닝"],
      "카니발": ["카니발", "카니발 하이브리드"]},
    "소형 상용 / 트럭": {
      "봉고 III": ["봉고 III", "봉고 III EV"]}},
  "제네시스": {
    "세단": {
      "G70": ["G70", "G70 슈팅브레이크"],
      "G80": ["G80", "G80 전동화 모델"],
      "G90": ["G90", "G90 롱휠베이스"]},
    "SUV & CUV": {
      "GV60": ["GV60"],
      "GV70": ["GV70", "Electrified GV70"],
      "GV80": ["GV80", "GV80 쿠페"]}}}""")
    val brands = listOf("현대", "기아", "제네시스")
    fun categories(brand: String): List<Pair<String,List<String>>> {
        val categories = catalog.optJSONObject(brand) ?: return emptyList()
        return categories.keys().asSequence().map { name ->
            name to categories.getJSONObject(name).keys().asSequence().toList()
        }.toList()
    }
}
package com.vemoring.companion

/** Care-only catalog. Warning artwork/diagnosis is no longer part of the product. */
data class BannerDefinition(val key:String,val title:String,val body:String,val mood:String,val art:String?=null)
object BannerCatalog {
    val entries=listOf(
        BannerDefinition("FUEL_DONE","주유 완료!","에너지를 채웠어요.","NORMAL","banner_fuel_done"),
        BannerDefinition("WASH_DONE","세차 완료!","깨끗하게 관리했어요.","NORMAL","banner_wash_done"),
        BannerDefinition("OIL_DONE","엔진오일 교체를 기록했어요!","확인한 교체일로 다음 교체 시기를 계산해요.","NORMAL","banner_oil_done"),
        BannerDefinition("ALL_DONE","모든 관리 완료!","주유·세차·엔진오일 관리를 기록했어요.","NORMAL","banner_all_done"),
        BannerDefinition("FUEL_NEEDED","주유할 시간이야!","배고파졌어! 슬슬 밥 먹으러 가자!","FUEL","banner_fuel_needed"),
        BannerDefinition("WASH_NEEDED","세차가 필요해!","나 너무 꼬질꼬질해... 씻겨주면 안 돼?","WASH","banner_wash_needed"),
        BannerDefinition("OIL_NEEDED","엔진오일 교체 시기예요","차량별 가혹조건 교체 주기에 도달했어요.","OIL"),
        BannerDefinition("FUEL_WASH_NEEDED","주유와 세차가 필요해요","에너지와 청결을 함께 챙겨 주세요.","ANGRY"),
        BannerDefinition("FUEL_OIL_NEEDED","주유와 엔진오일 교체 시기예요","에너지와 가혹조건 교체 주기를 확인해 주세요.","ANGRY"),
        BannerDefinition("WASH_OIL_NEEDED","세차와 엔진오일 교체 시기예요","청결과 가혹조건 교체 주기를 확인해 주세요.","ANGRY"),
        BannerDefinition("ALL_NEEDED","세 가지 관리가 필요해요","주유·세차·엔진오일 교체 시기를 확인해 주세요.","EXHAUSTED"),
        BannerDefinition("WASH_QUESTION","세차한 것 같아!","세차가 아니라면 알려 주세요.","WASH"),
        BannerDefinition("OIL_CONFIRMATION","엔진오일 교체했나요?","교체했어요를 눌러야 교체 기록이 저장돼요.","OIL")
    ).associateBy {it.key}
    operator fun get(key:String)=entries.getValue(key)
}

package com.vemoring.companion

import org.json.JSONObject

object BannerPolicy {
    fun recordCompletion(kind:String,type:MaintenanceType):BannerDefinition?=when(kind) {
        "wash"->BannerCatalog["WASH_DONE"]
        "fuel"->BannerCatalog["FUEL_DONE"]
        "maintenance","oil"->if(type==MaintenanceType.ENGINE_OIL) BannerCatalog["OIL_DONE"] else null
        else->null
    }
    fun issues(fuel:Boolean,wash:Boolean,oilDue:Boolean=false):BannerDefinition?=when {
        fuel && wash && oilDue->BannerCatalog["ALL_NEEDED"]
        fuel && wash->BannerCatalog["FUEL_WASH_NEEDED"]
        fuel && oilDue->BannerCatalog["FUEL_OIL_NEEDED"]
        wash && oilDue->BannerCatalog["WASH_OIL_NEEDED"]
        fuel->BannerCatalog["FUEL_NEEDED"]
        wash->BannerCatalog["WASH_NEEDED"]
        oilDue->BannerCatalog["OIL_NEEDED"]
        else->null
    }
    fun forProfile(definition:BannerDefinition,profile:JSONObject):BannerDefinition {
        if(profile.optString("fuel")!="전기차" || !definition.title.contains("주유")) return definition
        return definition.copy(title=definition.title.replace("주유","충전"),body=definition.body.replace("주유","충전"),art=null)
    }
}
enum class WashCompletionEvidence { EstimatedVisit, ClearFacilityPattern, UserRecord, VerifiedCompletion }

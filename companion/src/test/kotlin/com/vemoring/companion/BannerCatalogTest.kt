package com.vemoring.companion

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BannerCatalogTest {
    @Test fun allSuppliedPairsHaveOneUniqueStateMapping() {
        assertEquals(66,BannerCatalog.entries.values.count {it.art!=null})
        assertEquals(66,BannerCatalog.entries.values.mapNotNull {it.art}.toSet().size)
        val groups=BannerPolicy.maintenanceGroups.values.toSet()-"WASHER"
        assertEquals(15,groups.size)
        for(group in groups) for(fuel in listOf(false,true)) for(wash in listOf(false,true)) {
            val d=requireNotNull(BannerPolicy.issues(fuel,wash,group))
            assertNotNull("Missing supplied art: ${d.key}",d.art)
            assertEquals(if(fuel&&wash) "EXHAUSTED" else if(fuel||wash) "ANGRY" else "OIL",d.mood)
        }
    }
    @Test fun healthyStateDoesNotInventCompletionAndFallbackDoesNotInventFault() {
        assertNull(BannerPolicy.issues(false,false,null))
        assertEquals("FUEL_WASH_NEEDED",BannerPolicy.issues(true,true,null)!!.key)
        assertNull(BannerPolicy.issues(true,true,null)!!.art)
        assertFalse(BannerPolicy.issues(true,true,null)!!.title.contains("엔진"))
        assertEquals("EXHAUSTED",BannerPolicy.issues(true,true,"WASHER")!!.mood)
    }
    @Test fun electricEnergyDoesNotUseRefuellingTextInAnEmbeddedImage() {
        val ev=JSONObject().put("fuel","전기차")
        val d=BannerPolicy.forProfile(BannerCatalog["FUEL_NEEDED"],ev)
        assertTrue(d.title.contains("충전"));assertFalse(d.title.contains("주유"));assertNull(d.art)
        assertNotNull(BannerPolicy.forProfile(BannerCatalog["FUEL_NEEDED"],JSONObject().put("fuel","휘발유")).art)
    }
}

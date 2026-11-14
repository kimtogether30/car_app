package com.vemoring.companion
import org.junit.Assert.*
import org.junit.Test
class BannerCatalogTest {
 @Test fun onlyCareBannersRemain() {
  assertEquals(6,BannerCatalog.entries.values.count {it.art!=null})
  assertFalse(BannerCatalog.entries.containsKey("ABS"))
  assertNull(BannerPolicy.recordCompletion("maintenance",MaintenanceType.GENERAL))
  assertEquals("OIL_DONE",BannerPolicy.recordCompletion("maintenance",MaintenanceType.ENGINE_OIL)?.key)
 }
 @Test fun oilPeriodCombinesWithFuelAndWash() {
  assertNull(BannerPolicy.issues(false,false,false))
  assertEquals("OIL",BannerPolicy.issues(false,false,true)?.mood)
  assertEquals("ANGRY",BannerPolicy.issues(true,false,true)?.mood)
  assertEquals("EXHAUSTED",BannerPolicy.issues(true,true,true)?.mood)
 }
}

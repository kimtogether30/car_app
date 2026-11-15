package com.example.vemoring.phone

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vemoring.companion.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Same UI and notification pipeline as production, isolated verification ID and unique synthetic garage. */
class SettingsBehaviorTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val c get()=compose.activity.applicationContext
 private val id="settings-${UUID.randomUUID()}"
 private var selectedBefore:String?=null
 @Before fun rememberSelection() {selectedBefore=c.getSharedPreferences("phone_vehicle_selection",android.content.Context.MODE_PRIVATE).getString("selectedId",null)}
 private val manager get()=c.getSystemService(NotificationManager::class.java)
 private fun authority()=requireNotNull(CareJournal(c,id).authority())
 private fun setting()=compose.onNodeWithTag("care-notifications-switch")
 private fun evaluateLow() {
  val j=CareJournal(c,id);val a=authority()
  CareNotifications.evaluate(c,j,a,j.score(JSONArray(a.records)),null,
   JSONObject().put("observedAt",System.currentTimeMillis()).put("fuel",5).put("fuelAt",System.currentTimeMillis()))
 }
 @After fun cleanup() {
  manager.activeNotifications.filter {it.tag?.startsWith("$id:")==true}.forEach {manager.cancel(it.tag,it.id)}
  CareJournal(c,id).deleteVehicle();NotificationHistory(c).use {it.deleteVehicle(id)}
  c.getSharedPreferences("phone_vehicle_selection",android.content.Context.MODE_PRIVATE).edit().apply {
   if(selectedBefore==null) remove("selectedId") else putString("selectedId",selectedBefore)
  }.commit()
 }
 @Test fun notificationSwitchBlocksRealAndroidDeliveryPersistsAndResumes() {
  check(c.packageName.endsWith(".verification"))
  val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
  automation.grantRuntimePermission(c.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
  val profile=JSONObject().put("nickname","알림 설정 검증").put("model","현대 아반떼").put("plate","")
   .put("fuel","휘발유").put("notifications",true).put("washAlert",30).put("fuelAlert",20).put("simulation",true)
  val content=JSONObject().put("profile",profile).put("records",JSONArray()).put("mood","NORMAL")
   .put("message","설정 검증").put("demo",false).put("fuel",80).put("battery",JSONObject.NULL)
   .put("cleanliness",JSONObject.NULL).put("oilRemainingKm",JSONObject.NULL)
   .put("signals",JSONObject().put("lowFuel",false).put("oilDue",false))
  runBlocking {val frame=MirrorSnapshot(id,1,System.currentTimeMillis(),content.toString());MirrorBus.receive(c,frame);CareExchange.receive(c,frame)}
  compose.waitUntil(10000) {compose.onAllNodesWithTag("vehicle-picker").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("vehicle-picker").performScrollTo().performClick()
  compose.onNodeWithTag("vehicle:$id").performScrollTo().performClick()
  compose.onNodeWithText("내 차").performClick()
  setting().performScrollTo().assertIsOn().performClick()
  compose.waitUntil(10000){!JSONObject(authority().profile).getBoolean("notifications")}
  evaluateLow()
  // Every state/completion/confirmation banner shares the saved profile's delivery gate.
  val enabled=JSONObject(authority().profile).getBoolean("notifications")
  BannerCatalog.entries.values.forEach {d ->
   val e=CareNotificationEntry(id,"off:${d.key}",d.key,d.title,d.body,d.mood,d.art,System.currentTimeMillis(),source="Simulation")
   assertEquals(d.key,NotificationDelivery.Disabled,BannerNotifications.dispatch(c,e,enabled))
  }
  assertFalse("Notification posted despite the actual UI switch being OFF",manager.activeNotifications.any {it.tag?.startsWith("$id:")==true})
  NotificationHistory(c).use {history ->assertTrue("Disabled event not recorded as blocked",history.recent(id,100).any {it.bannerKey=="FUEL_NEEDED" && it.delivery==NotificationDelivery.Disabled})}
  compose.activityRule.scenario.recreate()
  compose.waitUntil(10000) {compose.onAllNodesWithText("내 차").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("내 차").performClick()
  setting().performScrollTo().assertIsOff().performClick()
  compose.waitUntil(10000){JSONObject(authority().profile).getBoolean("notifications")}
  evaluateLow()
  compose.waitUntil(5000) {manager.activeNotifications.any {it.tag?.startsWith("$id:")==true}}
  NotificationHistory(c).use {history ->assertTrue(history.recent(id,100).any {it.bannerKey=="FUEL_NEEDED" && it.delivery==NotificationDelivery.Delivered})}
  // Icon control belongs to vehicle app; phone has explanatory text, not a nonfunctional switch.
  compose.onNodeWithText("앱 밖에서도 상태 아이콘 갱신").performScrollTo().assertExists()
  assertEquals("Phone UI must expose only its real notification switch",1,compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
 }
 @Test fun vehicleSettingRequestDisablesAndReenablesPhoneNotifications()=runBlocking {
  check(c.packageName.endsWith(".verification"))
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(c.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
  val p=JSONObject().put("nickname","차량 설정 수신 검증").put("model","현대 아반떼").put("plate","")
   .put("fuel","휘발유").put("notifications",false).put("washAlert",30).put("fuelAlert",20).put("simulation",true)
  fun frame(revision:Long,request:Boolean):MirrorSnapshot {
   val content=JSONObject().put("profile",p).put("records",JSONArray()).put("mood","FUEL").put("demo",false).put("message","설정 수신 검증")
    .put("fuel",5).put("fuelAt",System.currentTimeMillis()).put("battery",JSONObject.NULL)
    .put("cleanliness",JSONObject.NULL).put("oilRemainingKm",JSONObject.NULL)
    .put("signals",JSONObject().put("lowFuel",true).put("oilDue",false))
   if(request) content.put("care",JSONObject().put("request",JSONObject().put("id","vehicle-setting-on").put("base",authority().version).put("profile",p).put("records",JSONArray())))
   return MirrorSnapshot(id,revision,System.currentTimeMillis(),content.toString())
  }
  var f=frame(1,false);MirrorBus.receive(c,f);CareExchange.receive(c,f)
  assertFalse(JSONObject(authority().profile).getBoolean("notifications"))
  assertFalse(manager.activeNotifications.any {it.tag?.startsWith("$id:")==true})
  NotificationHistory(c).use {assertTrue(it.recent(id,100).any {e->e.delivery==NotificationDelivery.Disabled})}
  p.put("notifications",true);f=frame(2,true);MirrorBus.receive(c,f);CareExchange.receive(c,f)
  assertTrue(JSONObject(authority().profile).getBoolean("notifications"))
  compose.waitUntil(5000){manager.activeNotifications.any {it.tag?.startsWith("$id:")==true}}
 }
}

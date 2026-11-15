package com.example.vemoring

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.SharedVehicleConnection
import com.vemoring.companion.MirrorBus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real settings controls and production services. Restore preferences; never replace a user's garage. */
class SettingsBehaviorTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val c get()=compose.activity.applicationContext
 private fun setting(label:String)=compose.onNodeWithTag(when(label){"관리 알림"->"care-notifications-switch";"앱 밖에서 상태 아이콘 갱신"->"launcher-icon-switch";else->"background-receiving-switch"})
 private fun waitFor(label:String, predicate:()->Boolean) {
  val until=android.os.SystemClock.uptimeMillis()+20000
  while(!predicate() && android.os.SystemClock.uptimeMillis()<until) Thread.sleep(100)
  assertTrue(label,predicate())
 }
 private fun monitorRunning()=c.getSystemService(ActivityManager::class.java).getRunningServices(100)
  .any {it.service.className==VehicleIconMonitor::class.java.name}
 @Test fun actualSwitchesPersistStopIconsAndStopOnlyBackgroundReception() {
  val prefs=c.getSharedPreferences(VehicleIconMonitor.PREFS,Context.MODE_PRIVATE)
  val icon=prefs.getBoolean(VehicleIconMonitor.ENABLED,true)
  val receive=prefs.getBoolean(VehicleIconMonitor.RECEIVE_ENABLED,true)
  val original=GarageStore(c).load()
  assertTrue("A registered car is required without overwriting user data",original.complete)
  fun set(label:String,value:Boolean) {
   val node=setting(label).performScrollTo()
   if(node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] !=
    if(value) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off) node.performClick()
   compose.waitForIdle()
  }
  try {
   compose.waitUntil(20000){compose.onAllNodesWithText("내 차",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("내 차",useUnmergedTree=true).performClick()
   compose.waitUntil(10000){compose.onAllNodesWithText("내 차 설정").fetchSemanticsNodes().isNotEmpty()}
   set("관리 알림",false)
   waitFor("Notification OFF was not persisted"){!GarageStore(c).load().profile.notifications}
   waitFor("Notification OFF did not reach outgoing vehicle data") {
    MirrorBus.outgoing.value?.let {JSONObject(it.content).getJSONObject("profile").optBoolean("notifications",true)==false}==true
   }
   set("관리 알림",true)
   waitFor("Notification ON was not persisted"){GarageStore(c).load().profile.notifications}
   set("다른 화면에서도 차량 정보 받기",true)
   waitFor("Receiving service not running"){monitorRunning()}
   set("앱 밖에서 상태 아이콘 갱신",false)
   waitFor("Icon switch did not persist"){!prefs.getBoolean(VehicleIconMonitor.ENABLED,true)}
   assertTrue("Icon OFF must preserve receiving",prefs.getBoolean(VehicleIconMonitor.RECEIVE_ENABLED,false))
   assertTrue("Icon OFF stopped the receiving service",monitorRunning())
   set("앱 밖에서 상태 아이콘 갱신",true)
   waitFor("Icon ON did not persist"){prefs.getBoolean(VehicleIconMonitor.ENABLED,false)}
   set("다른 화면에서도 차량 정보 받기",false)
   waitFor("Receiving OFF did not stop the service"){!monitorRunning()}
   set("다른 화면에서도 차량 정보 받기",true)
   waitFor("Receiving ON did not restart service"){monitorRunning()}
  } finally {
   c.sendBroadcast(Intent(c,IconMonitorPreviewReceiver::class.java).putExtra("stage",-1))
   prefs.edit().putBoolean(VehicleIconMonitor.ENABLED,icon).putBoolean(VehicleIconMonitor.RECEIVE_ENABLED,receive).commit()
   // Restore only this setting on the latest data, preserving records arriving during verification.
   val store=GarageStore(c);store.save(store.load().copy(profile=store.load().profile.copy(notifications=original.profile.notifications)),queueForPhone=true)
   if(receive) VehicleIconMonitor.start(c) else c.stopService(Intent(c,VehicleIconMonitor::class.java))
  }
 }
}

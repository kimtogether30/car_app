package com.example.vemoring.phone

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.vemoring.companion.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class PhoneFleetUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=compose.activity.applicationContext
    private val token=UUID.randomUUID().toString().take(8)
    private val ids=mutableListOf<String>()
    private fun capture(name:String) {
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/$name.png").let {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {input->input.readBytes()}
            }
    }
    private fun add(name:String,kind:String)=runBlocking {
        val p=JSONObject().put("nickname",name).put("model","현대 아반떼").put("plate","")
            .put("fuel","휘발유").put("fuelAlert",20).put("washAlert",30).put("notifications",false).put("simulation",true)
        CareExchange.registerPhone(context,p,JSONArray().put(JSONObject().put("id","record-$name").put("kind",kind)
            .put("date",LocalDate.now(CARE_ZONE).minusDays(2)).put("title","$name 기록").put("detail","보존 메모")))
            .also {ids+=it.vehicleId}
    }
    private fun select(id:String) {
        compose.waitUntil(5000) {compose.onAllNodesWithTag("vehicle-picker").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("vehicle-picker").performScrollTo().performClick()
        compose.onNodeWithTag("vehicle:$id").performScrollTo().performClick()
    }
    @After fun cleanup() {
        CareJournal.vehicles(context).filter {it.vehicleId in ids || JSONObject(it.profile).optString("nickname")=="추가차-$token"}
            .forEach {CareJournal(context,it.vehicleId).deleteVehicle()}
    }
    @Test fun carsKeepSeparateRecordsDeletionNeedsConfirmationAndPlateIsOptional() {
        check(context.packageName.endsWith(".verification"))
        val a=add("첫차-$token","wash");val b=add("둘째차-$token","fuel")
        select(a.vehicleId)
        val other=JSONObject().put("profile",JSONObject(b.profile)).put("records",JSONArray(b.records)).put("mood","FUEL")
            .put("message","다른 차량의 시험 수신").put("demo",false).put("fuel",5).put("battery",JSONObject.NULL)
            .put("cleanliness",JSONObject.NULL).put("oilRemainingKm",JSONObject.NULL)
            .put("signals",JSONObject().put("lowFuel",true).put("oilDue",false))
        runBlocking {val frame=MirrorSnapshot(b.vehicleId,10,System.currentTimeMillis(),other.toString())
            MirrorBus.receive(context,frame);CareExchange.receive(context,frame)}
        compose.onNodeWithTag("vehicle-picker").assertTextContains("첫차-$token",substring=true)
        assertEquals(5,JSONObject(MirrorBus.vehicles.value.getValue(b.vehicleId).snapshot.content).getInt("fuel"))
        compose.onNodeWithText("관리 기록").performClick()
        compose.onNodeWithText("첫차-$token 기록",substring=true).performScrollTo().performClick()
        compose.onNodeWithText("기록 삭제").performScrollTo().performClick()
        capture("phone-fleet-delete")
        compose.onNodeWithText("돌아가기").performClick()
        assertFalse(JSONArray(CareJournal(context,a.vehicleId).authority()!!.records).getJSONObject(0).optBoolean("deleted"))
        compose.onNodeWithText("기록 삭제").performScrollTo().performClick()
        compose.onNodeWithText("삭제",substring=false).performClick()
        compose.waitUntil(5000) {JSONArray(CareJournal(context,a.vehicleId).authority()!!.records).getJSONObject(0).optBoolean("deleted")}
        assertFalse(JSONArray(CareJournal(context,b.vehicleId).authority()!!.records).getJSONObject(0).optBoolean("cancelled"))
        select(b.vehicleId)
        compose.onNodeWithText("둘째차-$token 기록",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("vehicle-picker").performScrollTo().performClick()
        compose.onNodeWithTag("add-vehicle").performClick()
        compose.onNodeWithTag("car-model").performScrollTo().performTextInput("직접 입력 시험차")
        compose.onNodeWithTag("car-nickname").performScrollTo().performTextReplacement("추가차-$token")
        compose.onNodeWithTag("hide-keyboard").performClick()
        compose.onNodeWithText("저장하기").assertIsEnabled().performClick()
        compose.waitUntil(5000) {CareJournal.vehicles(context).any {JSONObject(it.profile).optString("nickname")=="추가차-$token"}}
        val created=CareJournal.vehicles(context).first {JSONObject(it.profile).optString("nickname")=="추가차-$token"}
        assertEquals("",JSONObject(created.profile).getString("plate"))
        assertNotEquals(a.vehicleId,created.vehicleId);assertNotEquals(b.vehicleId,created.vehicleId)
        assertEquals("보존 메모",JSONArray(CareJournal(context,b.vehicleId).authority()!!.records).getJSONObject(0).getString("detail"))
        compose.onNodeWithTag("vehicle-picker").performScrollTo().performClick()
        capture("phone-fleet-list")
        compose.onNodeWithText("닫기").performClick()
    }
}

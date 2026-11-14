package com.example.vemoring.phone

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
import java.util.UUID

/** Actual phone settings UI -> phone-owned profile, with the connected-vehicle branch selected. */
class PhoneFuelSettingTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=compose.activity.applicationContext
    private val vehicle="fuel-setting-${UUID.randomUUID()}"
    @After fun cleanup() {CareJournal(context,vehicle).deleteVehicle()}

    @Test fun connectedVehicleFuelThresholdIsPersistedFromTheSlider() {
        check(context.packageName.endsWith(".verification"))
        val p=JSONObject().put("nickname","임계값 시험 차량").put("model","시험 모델").put("plate",vehicle)
            .put("fuel","휘발유").put("notifications",false).put("washAlert",30).put("fuelAlert",20).put("simulation",true)
        val content=JSONObject().put("profile",p).put("records",JSONArray()).put("mood","NORMAL")
            .put("message","시험 데이터").put("demo",false).put("fuel",80).put("battery",JSONObject.NULL)
            .put("cleanliness",JSONObject.NULL).put("oilRemainingKm",JSONObject.NULL)
            .put("signals",JSONObject().put("lowFuel",false).put("oilDue",false))
        val frame=MirrorSnapshot(vehicle,1,System.currentTimeMillis(),content.toString())
        runBlocking {MirrorBus.receive(context,frame);CareExchange.receive(context,frame)}
        compose.waitUntil(5000) {compose.onAllNodesWithTag("vehicle-picker").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("vehicle-picker").performScrollTo().performClick()
        compose.onNodeWithTag("vehicle:$vehicle").performScrollTo().performClick()
        compose.onNodeWithText("내 차").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithText("임계값 시험 차량").fetchSemanticsNodes().isNotEmpty()}
        val slider=SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        compose.waitUntil(5000) {compose.onAllNodes(slider).fetchSemanticsNodes().size==2}
        compose.onAllNodes(slider).onLast().performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) {it(25f)}
        compose.waitUntil(5000) {CareJournal(context,vehicle).authority()?.profile?.let {JSONObject(it).optInt("fuelAlert")==25}==true}
        val saved=JSONObject(CareJournal(context,vehicle).authority()!!.profile)
        assertEquals(25,saved.getInt("fuelAlert"))
        assertEquals(30,saved.getInt("washAlert"))
    }
}

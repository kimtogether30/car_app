package com.example.vemoring

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class GarageFleetUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=object:ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val prefix="fleet_ui_${UUID.randomUUID()}_"
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(prefix+name,mode)
    }
    private val store=GarageStore(context)
    private fun capture(name:String) {
        compose.waitForIdle();Thread.sleep(300)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name.png").let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {input->input.readBytes()}
        }
    }
    @Test fun switchingViewedCarsDoesNotSwitchTheReceivingCar() {
        val a=AppData(CarProfile("현대","아반떼","시험 첫차",""),complete=true)
        val b=AppData(CarProfile("현대","아반떼","시험 둘째차",""),complete=true)
        store.save(a);store.save(b)
        compose.setContent {MaterialTheme(typography=com.example.vemoring.ui.theme.Typography,colorScheme=GarageColors) {GarageApp(storeOverride=store)}}
        compose.waitUntil(5000) {compose.onAllNodesWithTag("vehicle-picker").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("vehicle-picker").performClick()
        compose.onNodeWithText("등록 차량").assertIsDisplayed()
        capture("fleet-vehicles")
        compose.onNodeWithTag("vehicle:${a.profile.vehicleId}").performScrollTo().performClick()
        compose.waitUntil(5000) {store.load().profile.vehicleId==a.profile.vehicleId}
        compose.onNodeWithTag("vehicle-picker").performClick()
        compose.onNodeWithTag("vehicle:${b.profile.vehicleId}").performScrollTo().performClick()
        compose.waitUntil(5000) {store.load().profile.vehicleId==b.profile.vehicleId}
        assertEquals(a.profile.vehicleId,store.receiving()!!.profile.vehicleId)
        compose.onNodeWithText("이 차량에 연결").performClick()
        compose.onNodeWithText("취소").performClick()
        assertEquals(a.profile.vehicleId,store.receiving()!!.profile.vehicleId)
    }
    @Test fun plateCanBeClearedAndUnknownWashDateKeepsTheRecordedHistory() {
        val record=CareRecord(date=LocalDate.now().minusDays(2),kind=CareKind.WASH,title="유지할 세차 기록")
        val a=AppData(CarProfile("현대","아반떼","시험 차","12가 3456"),listOf(record),true);store.save(a)
        compose.setContent {MaterialTheme(typography=com.example.vemoring.ui.theme.Typography,colorScheme=GarageColors) {VehicleDialog(a.profile,a.records,{},save={p,dates -> store.save(a.copy(profile=p,records=updateLatest(a.records,dates)))})}}
        compose.onNode(hasSetTextAction() and hasText("번호판 (선택)")).performScrollTo().performTextClearance()
        compose.onAllNodes(isToggleable())[1].performScrollTo().performClick()
        compose.onNodeWithText("저장하기").assertIsEnabled().performClick()
        assertEquals("",store.load().profile.plate)
        assertEquals(a.profile.vehicleId,store.load().profile.vehicleId)
        assertEquals(listOf(record),store.load().records)
    }
    @Test fun deletingARecordRequiresAnExplicitConfirmation() {
        val record=CareRecord(date=LocalDate.now().minusDays(2),kind=CareKind.WASH,title="시험 삭제 기록")
        val a=AppData(CarProfile("현대","아반떼","삭제 시험",""),listOf(record),true);store.save(a)
        compose.setContent {MaterialTheme(typography=com.example.vemoring.ui.theme.Typography,colorScheme=GarageColors) {RecordDialog(record,record.date,{},remove={store.save(a.copy(records=deleteRecord(a.records,record.id)))},save={})}}
        compose.onNodeWithText("기록 삭제").performScrollTo().performClick()
        compose.onNodeWithText("기록을 삭제할까요?").assertIsDisplayed()
        capture("fleet-delete-confirm")
        compose.onNodeWithText("돌아가기").performClick()
        assertFalse(store.load().records.single().cancelled)
        compose.onNodeWithText("기록 삭제").performScrollTo().performClick()
        compose.onNodeWithText("삭제",substring=false).performClick()
        assertTrue(store.load().records.single().deleted)
        assertTrue(store.load().records.single().cancelled)
    }
}

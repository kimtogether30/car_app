package com.example.vemoring.phone

import android.app.NotificationManager
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.*

/** Opt-in, paced demonstration. All data lives in the disposable verification application. */
class FeatureWalkthroughTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val c=instrumentation.targetContext
    private val id="walkthrough-20261110"
    private var revision=System.currentTimeMillis()
    private var caption by mutableStateOf("기능 시연 준비")
    private var detail by mutableStateOf("")
    private var dark by mutableStateOf(false)
    private var history by mutableStateOf(false)
    private val journal get()=CareJournal(c,id)
    private val manager get()=c.getSystemService(NotificationManager::class.java)
    private fun shell(command:String) {
        instrumentation.uiAutomation.executeShellCommand(command).let {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {input->input.readBytes()}
        }
    }
    private fun scene(title:String,body:String,seconds:Long=7) {
        compose.runOnIdle {caption=title;detail=body}
        instrumentation.sendStatus(0,Bundle().apply {putString("stream","\nDEMO: $title\n")})
        if(seconds>0) Thread.sleep(seconds*1000)
    }
    private fun shade(seconds:Long=5) {
        shell("cmd statusbar expand-notifications");Thread.sleep(seconds*1000);shell("cmd statusbar collapse")
    }
    private fun profile()=JSONObject().put("nickname","시연용 베모링").put("model","시뮬레이션 차량")
        .put("plate","SIMULATION").put("fuel","휘발유").put("fuelAlert",20).put("washAlert",30)
        .put("notifications",true).put("simulation",true)
    private fun records(dirty:Boolean)=JSONArray().put(JSONObject().put("id",if(dirty) "demo-old-wash" else "demo-recent-wash")
        .put("kind","wash").put("date",LocalDate.now(CARE_ZONE).minusDays(if(dirty) 42 else 5))
        .put("title","시뮬레이션 세차 기록").put("detail","실제 사용자 기록 아님").put("source","Manual"))
    private suspend fun state(fuel:Int,dirty:Boolean,warning:Boolean) {
        journal.authority()?.let {journal.saveAuthority(it.copy(version=it.version+1,records=records(dirty).toString()))}
        val now=System.currentTimeMillis()
        val content=JSONObject().put("profile",profile()).put("records",records(dirty)).put("demo",false)
            .put("mood",if(warning && dirty && fuel<=20) "EXHAUSTED" else if(listOf(warning,dirty,fuel<=20).count {it}>=2) "ANGRY" else if(warning) "OIL" else if(dirty) "WASH" else if(fuel<=20) "FUEL" else "NORMAL")
            .put("message","시뮬레이션 · 실제 SDK/블루투스 수신 아님")
            .put("fuel",fuel).put("fuelAt",now).put("battery",JSONObject.NULL)
            .put("care",JSONObject().put("warnings",JSONObject().put("ENGINE",JSONObject()
                .put("status","Available").put("warning",warning).put("eligible",true).put("source","Real").put("at",now))))
        val frame=MirrorSnapshot(id,++revision,now,content.toString())
        val bytes=ByteArrayOutputStream().also {MirrorFrames.write(it,frame)}.toByteArray()
        val reply=CareExchange.receive(c,MirrorFrames.read(ByteArrayInputStream(bytes)))
        assertEquals(revision,reply.getLong("ack"))
    }
    private suspend fun visit(visitId:String,where:WashLocationEvidence,zone:ZoneEvidence=ZoneEvidence.Inside):Boolean {
        val detector=WashVisitDetector(visitId,WashFacility.Stationary,locationEvidence=where)
        val start=Instant.now().minusSeconds(300)
        var applied=false
        for(t in 0L..230L step 5) {
            val inside=t<200 && zone==ZoneEvidence.Inside
            val at=start.plusSeconds(t)
            applied=CareExchange.observeWashVisit(c,id,detector,WashObservation(at,at,2.0,
                if(inside) ZoneEvidence.Inside else ZoneEvidence.Outside,EvidenceSource.Vehicle,
                stationary=inside,gear=if(inside) "P" else "D",consistentMotion=!inside,
                vehicleReconnected=true,facilityExit=!inside)) || applied
        }
        return applied
    }
    @Test fun guidedLiveDemonstration()=runBlocking {
        assumeTrue("Run explicitly with -e walkthrough 1",InstrumentationRegistry.getArguments().getString("walkthrough")=="1")
        check(c.packageName.endsWith(".verification"))
        journal.deleteVehicle()
        manager.activeNotifications.filter {it.tag?.startsWith("$id:")==true}.forEach {manager.cancel(it.tag,it.id)}
        state(80,false,false)
        compose.setContent {
            MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
                val canonical by CareExchange.canonical.collectAsState()
                val scope=rememberCoroutineScope()
                val confirmation by rememberWashConfirmation(id,canonical)
                val summary by produceState("",canonical) {
                    value=withContext(Dispatchers.IO) {
                        val a=journal.authority()!!;val r=JSONArray(a.records)
                        "청결도 ${journal.score(r).displayed?.let {"$it%"} ?: "기록 없음"} · 유효 기록 ${(0 until r.length()).count {!r.getJSONObject(it).optBoolean("cancelled")}}건"
                    }
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text("시뮬레이션 · 실제 Bluetooth/SDK 수신 아님",style=MaterialTheme.typography.labelLarge)
                        Text(caption,style=MaterialTheme.typography.headlineSmall)
                        Text(detail,fontSize=17.sp)
                        Text(summary,style=MaterialTheme.typography.titleLarge)
                        confirmation?.let {candidate -> WashConfirmationBanner(candidate,false,
                            onReject={scope.launch {CareExchange.cancel(c,id,candidate.visitId)}},
                            onDismiss={scope.launch {CareExchange.dismissConfirmation(c,id,candidate.visitId)}}) }
                        Text("시험 입력만 격리 앱에 저장해요. 세차 체류 시간은 가상 시각으로 압축 재생해요.")
                        TextButton(onClick={history=true}) {Text("저장된 알림 내역 보기")}
                    }
                }
                if(history) NotificationHistoryDialog(id,onDismiss={history=false})
            }
        }
        scene("1. 프레임 수신·저장·ACK","연료 80%, 최근 세차 기록. 시험 프레임을 실제 수신 처리기로 읽고 ACK와 저장을 확인했어요.")
        val states=listOf(Triple(10,false,false),Triple(80,true,false),Triple(80,false,true),Triple(10,true,false),Triple(10,false,true),Triple(10,true,true))
        val names=listOf("주유 필요 · 노랑","세차 필요 · 갈색","차량 경고 · 보라","주유 + 세차 · 빨강","주유 + 경고 · 빨강","세 가지 문제 · 짙은 회색")
        states.forEachIndexed {index,(fuel,dirty,warning) ->
            scene("2-${index+1}. ${names[index]}","시험 차량 상태를 바꿉니다. 정비 경고는 검증된 이상 신호를 가정한 입력이에요.",0)
            state(fuel,dirty,warning);Thread.sleep(2500);shade(5)
        }
        val count=NotificationHistory(c).use {it.recent(id,200).size}
        state(10,true,true)
        assertEquals(count,NotificationHistory(c).use {it.recent(id,200).size})
        scene("3. 같은 상태 재수신 → 중복 없음","동일 상태를 다시 보냈지만 알림 내역은 ${count}건 그대로예요.")
        state(80,false,false)
        val before=journal.authority()!!.records
        assertFalse(visit("fuel-only",WashLocationEvidence.SharedOrUncertain,ZoneEvidence.Outside))
        assertEquals(before,journal.authority()!!.records)
        scene("4. 주유만 한 경우 → 세차 생성 안 함","주유 구역에만 머문 시험이에요. 세차 구역 진입 근거가 없어 기록을 만들지 않았어요.")
        scene("5. 애매한 세차 방문 · P단 정차식","세차 구역 체류 180초와 출차를 압축 재생합니다. 반영부터 하고 확인 질문을 보낼 거예요.",0)
        assertTrue(visit("shared-wash",WashLocationEvidence.SharedOrUncertain))
        Thread.sleep(3000);shade(6);Thread.sleep(4000)
        compose.onNodeWithTag("wash-reject").performScrollTo().performClick()
        compose.waitUntil(4000) {compose.onAllNodesWithTag("wash-confirmation").fetchSemanticsNodes().isEmpty()}
        assertFalse(CareExchange.detected(c,id,DetectedWash("shared-wash",Instant.now())))
        scene("6. 세차 안 했어요 → 취소·점수 복원","방금 실제 배너 버튼을 눌렀어요. 해당 방문을 취소했고 같은 방문의 재전송도 거절했어요.")
        scene("7. 확실한 세차 구역 → 완료 배너","전용 셀프 세차장의 P단 정차·체류·출차 조건을 모두 충족하는 시험이에요.",0)
        assertTrue(visit("self-wash",WashLocationEvidence.DedicatedSelfWash))
        assertNull(journal.pendingWashConfirmation())
        Thread.sleep(2500);shade(6)
        val stable=journal.authority()!!
        val lost=JSONObject().put("profile",profile()).put("records",JSONArray(stable.records)).put("demo",false)
            .put("care",JSONObject().put("warnings",JSONObject().put("ENGINE",JSONObject().put("status","Disconnected").put("at",System.currentTimeMillis()))))
        CareExchange.receive(c,MirrorSnapshot(id,++revision,System.currentTimeMillis(),lost.toString()))
        assertEquals(stable.records,journal.authority()!!.records)
        scene("8. 연결 끊김 입력 → 기록 유지","시험 프레임의 경고 상태를 Disconnected로 바꿨어요. 정상·정비 완료나 새 세차로 처리하지 않았어요. 실제 무선 단절 시험은 아니에요.")
        val a=journal.authority()!!
        val manual=JSONArray(a.records).put(JSONObject().put("id","demo-fuel-record").put("kind","fuel").put("date",LocalDate.now(CARE_ZONE)).put("source","Manual").put("title","시연 주유 기록").put("detail","시뮬레이션 기록"))
        CareExchange.editPhone(c,id,JSONObject(a.profile),manual)
        scene("9. 수동 관리기록 → 완료 알림","오늘 주유했다고 직접 기록하면 주유 완료 알림을 보냅니다. 메모 수정은 새 완료 알림이 아니에요.",3)
        shade(5)
        val s=LocalDate.of(2026,11,2).atStartOfDay(CARE_ZONE).toInstant();val e=s.plusSeconds(3600)
        val score=WashPolicy.score(listOf(WashBaseline("calc",LocalDate.of(2026,11,1))),listOf(
            TravelInterval("rain",s,s.plusSeconds(1200),10.0,located=true,weather=HourWeather(s,.1,0.0,0.0,null,null,e,"walkthrough")),
            TravelInterval("dry",s.plusSeconds(1200),e,20.0)),e)
        assertEquals(95,score.displayed)
        scene("10. 주행·날씨 점수 계산","고정 시험 시각에서 실제 30km 중 비 10km를 입력하면 청결도 95%입니다. 실제 거리 구간은 평균 추정을 대체해 중복 차감하지 않아요. 날씨 실수집 성공을 뜻하지 않아요.")
        scene("11. 제공 디자인과 알림 내역","지금까지 게시한 알림과 취소 이력을 밝은 화면으로 봅니다.",0)
        compose.runOnIdle {history=true};Thread.sleep(8500)
        compose.runOnIdle {dark=true};Thread.sleep(8500)
        compose.runOnIdle {history=false}
        scene("12. 시연 완료","알림 게시·세차 취소·완료·중복 억제·기록 유지·점수 계산·내역 저장을 확인했어요. 실제 Bluetooth, 세차 구역/위치 정확도 연동은 별도 검증이 필요해요.",8)
        val final=journal.authority()!!
        val cached=JSONObject().put("profile",JSONObject(final.profile)).put("records",JSONArray(final.records))
            .put("fuel",80).put("battery",JSONObject.NULL).put("cleanliness",journal.score(JSONArray(final.records)).displayed ?: JSONObject.NULL)
            .put("oilRemainingKm",JSONObject.NULL).put("mood","NORMAL").put("message","시뮬레이션 · 실제 차량/블루투스 수신 아님").put("demo",false)
        MirrorBus.receive(c,MirrorSnapshot(id,++revision,System.currentTimeMillis(),cached.toString()))
        // Retain only this isolated Simulation vehicle so the user can inspect the archive afterwards.
    }
}

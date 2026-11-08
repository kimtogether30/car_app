package com.example.vemoring.integration

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.*
import ai.pleos.playground.navi.data.*

interface NavigationGateway {
    fun start()
    fun close()
    fun query()
    fun route(place: Place, append: Boolean)
    fun routeWithWaypoints(destination: Place, waypoints: List<Place>)
    fun cancel()
    fun option(option: RouteOption)
    fun reroute()
    fun remove(index: WaypointIndex)
    fun screen()
}
class PleosNavigationGateway(context: Context, private val publish: (NavigationState) -> Unit,
    private val log: (String,String) -> Unit) : NavigationGateway {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sdk: NaviHelper? = null
    private var state = NavigationState()
    private var token = 0
    private var listener: NaviHelperEventListener? = null
    private var waypointValues = emptyList<WaypointInfo>()
    private var queryAt = 0L
    private var callbackSerial = 0
    private fun update(event: String, change: NavigationState = state) {
        state = change.copy(event = event, receivedAt = System.currentTimeMillis())
        publish(state); log("Navi callback", event)
    }
    override fun start() {
        if(sdk != null) return
        val session = ++token
        try {
            val client = NaviHelper(context)
            sdk = client
            state = NavigationState(connection = ResultState.Loading); publish(state)
            val events = object : NaviHelperEventListener {
                private fun deliver(action: () -> Unit) { main.post { if(session == token) { callbackSerial++; action() } } }
                override fun onRouteStarted(routeStartInfo: RouteStartInfo) = deliver { update("경로 탐색 시작") }
                override fun onRoutingCompleted() = deliver { update("경로 탐색 완료"); query() }
                override fun onReRouteEnded() = deliver { update("재탐색 완료"); query() }
                override fun onRouteEnded() = deliver { update("안내 종료"); query() }
                override fun onRouteCancelled() = deliver { update("경로 취소", state.copy(destination=null, destinationKnown=true)); query() }
                override fun onRouteOptionChanged(routeOption: RouteOption) = deliver { update("경로 옵션: " + routeOption.name) }
                override fun onDestinationChanged() = deliver { update("목적지 변경",state.copy(destinationKnown=false)); query() }
                override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = deliver { update("경유지 변경",state.copy(waypointsKnown=false)); query() }
                override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = deliver { update("목적지 도착 · 관리 완료 처리 없음") }
                override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = deliver { update("경유지 도착 · 관리 완료 처리 없음") }
                override fun onDestinationInfo(destinationInfo: DestinationInfo) = deliver { update("목적지 조회 수신", state.copy(destination=destinationInfo.name, destinationKnown=true)) }
                override fun onWayPointInfo(list: List<WaypointInfo>) = deliver {
                    waypointValues = list
                    update("경유지 조회 수신", state.copy(waypointNames=list.map { it.name }, waypointsKnown=true))
                }
                override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = deliver { update("경로 상태 수신: " + routeStateInfo.naviMode.name) }
                override fun onNavigationStatus(navigationStatus: NavigationStatus) = deliver { update("내비 상태: " + navigationStatus.name) }
                override fun onDrivingInfo(drivingInfo: DrivingInfo) = deliver { update("주행 정보 수신") }
                override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = deliver { update("내비 위치 수신") }
                override fun onTBTInfo(list: List<TBTInfo>) = deliver { update("회전 안내 정보 수신") }
                override fun onError(errorCode: NaviErrorCode) = deliver { update(errorCode.name, state.copy(connection=ResultState.Error)) }
            }
            listener = events
            client.initialize({
                main.post { if(session == token) {
                    client.addListener(events)
                    update("내비 서비스 연결", state.copy(connection=ResultState.Available)); query()
                } }
            }, { error -> main.post { if(session == token) failure(error) } })
            main.postDelayed({ if(session == token && state.connection == ResultState.Loading) failure(IllegalStateException("연결 시간 초과")) },15000)
        } catch(e: Exception) { failure(e) }
    }
    private fun failure(error: Throwable) {
        state = state.copy(connection = if(error is SecurityException) ResultState.PermissionDenied else ResultState.Error, event=error.javaClass.simpleName)
        publish(state); log("Navi error",error.javaClass.simpleName)
    }
    private fun command(name: String, action: (NaviHelper) -> Unit) {
        val client = sdk
        if(client == null || state.connection != ResultState.Available) { log(name,"내비 서비스 미연결"); return }
        try {
            val serial = callbackSerial; val session = token
            action(client); log(name,"요청 전송 · SDK 이벤트 대기")
            main.postDelayed({ if(session == token && callbackSerial == serial) {
                state=state.copy(event=name + " · 콜백 미수신"); publish(state); log(name,"15초 내 콜백 없음")
            } },15000)
        } catch(e: Exception) { failure(e) }
    }
    override fun query() {
        queryAt = System.currentTimeMillis()
        state=state.copy(destinationKnown=false,waypointsKnown=false); publish(state)
        command("Navi query") { it.getDestinationInfo(); it.getWaypointInfo(); it.getRouteStateInfo(); it.getCurrentLocationInfo(); it.getTBTInfo() }
    }
    override fun route(place: Place, append: Boolean) {
        if(append) {
            if(!state.destinationKnown || state.destination.isNullOrBlank() || !state.waypointsKnown || System.currentTimeMillis()-queryAt > 60000) {
                log("Add waypoint","최신 목적지·경유지 조회가 필요합니다"); return
            }
            if(waypointValues.size >= 2) { log("Add waypoint","기존 경유지 2개 유지 · 추가 불가"); return }
            command("Add waypoint") { it.addWaypoint(place.waypoint(if(waypointValues.isEmpty()) WaypointIndex.FIRST else WaypointIndex.SECOND)) }
        } else command("Request route") { it.requestRoute(place.route()) }
    }
    override fun routeWithWaypoints(destination: Place, waypoints: List<Place>) {
        require(waypoints.size <= 2)
        command("Route with waypoints") { it.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination.route(), waypoints.mapIndexed { index,p -> p.waypoint(if(index==0) WaypointIndex.FIRST else WaypointIndex.SECOND) })) }
    }
    override fun cancel() = command("Cancel route") { it.cancelRoute() }
    override fun option(option: RouteOption) = command("Route option") { it.changeRouteOption(option) }
    override fun reroute() = command("Re-route") { it.requestReRoute() }
    override fun remove(index: WaypointIndex) = command("Remove waypoint") { it.removeWaypoint(index) }
    override fun screen() = command("Navigation screen") { it.requestNavigationScreen(NavigationScreenRequest.Foreground) }
    override fun close() {
        token++
        sdk?.let { client -> listener?.let { runCatching { client.removeListener(it) } }; runCatching { client.release() } }
        sdk=null; listener=null; waypointValues=emptyList(); state=NavigationState(); publish(state)
    }
}
private fun Place.route() = RouteInfo(latitude=latitude,longitude=longitude,displayLatitude=null,displayLongitude=null,poiName=name,
    poiId="",address=address,poiSubId="0",routeOption=RouteOption.RECOMMENDED)
private fun Place.waypoint(index: WaypointIndex) = RequestWaypointInfo(latitude=latitude,longitude=longitude,poiName=name,
    waypointIndex=index,poiId="",address=address,poiSubId="0")

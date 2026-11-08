package com.example.vemoring.integration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ai.pleos.playground.navi.constants.*

class IntegrationViewModel(app: Application) : AndroidViewModel(app) {
    private val mutableMode=MutableStateFlow(DataMode.Real)
    val mode=mutableMode.asStateFlow()
    private val mutableLocation=MutableStateFlow(Result<Coordinates>())
    val location=mutableLocation.asStateFlow()
    private val mutableEnvironment=MutableStateFlow(Result<EnvironmentData>())
    val environment=mutableEnvironment.asStateFlow()
    private val mutablePlaces=MutableStateFlow(Result<List<Place>>())
    val places=mutablePlaces.asStateFlow()
    private val mutableNavigation=MutableStateFlow(NavigationState())
    val navigation=mutableNavigation.asStateFlow()
    private val mutableLogs=MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val logs=mutableLogs.asStateFlow()
    private val repository: ExternalDataRepository=HttpExternalDataRepository()
    private val locationGateway: LocationGateway=PleosLocationGateway(app, { mutableLocation.value=it }, ::log)
    private val navigationGateway: NavigationGateway=PleosNavigationGateway(app, { mutableNavigation.value=it }, ::log)
    private var vehicleJob: Job?=null
    fun observeVehicle(flow: StateFlow<com.example.vemoring.vehicle.VehicleSnapshot>) {
        if(vehicleJob!=null) return
        vehicleJob=viewModelScope.launch {
            var previous: com.example.vemoring.vehicle.VehicleSnapshot?=null
            flow.collect { snapshot ->
                snapshot.readings.forEach { (field,reading) ->
                    if(previous?.readings?.get(field)!=reading) log("Vehicle ${field.name}",reading.state.name+" "+reading.reason)
                }
                previous=snapshot
            }
        }
    }
    fun reconnectNavigation() {
        if(mode.value==DataMode.Real && foreground) { navigationGateway.close(); navigationGateway.start() }
    }
    private var foreground=false
    private var featureOpen=false
    private var requestEpoch=0
    private var weatherJob: Job?=null
    private var placesJob: Job?=null
    private fun log(operation: String, outcome: String) {
        mutableLogs.update { (listOf(DiagnosticEvent(operation=operation,outcome=outcome,source=mode.value.name))+it).take(100) }
    }
    fun resume() { foreground=true; if(featureOpen && mode.value==DataMode.Real) navigationGateway.start() }
    fun pause() {
        foreground=false; locationGateway.stop(); navigationGateway.close()
        requestEpoch++; weatherJob?.cancel(); placesJob?.cancel()
        mutableLocation.value=Result(detail="위치 기능 중지")
        if(mutableEnvironment.value.state==ResultState.Loading) mutableEnvironment.value=Result(detail="요청 중지")
        if(mutablePlaces.value.state==ResultState.Loading) mutablePlaces.value=Result(detail="요청 중지")
    }
    fun open() { featureOpen=true; if(foreground && mode.value==DataMode.Real) navigationGateway.start() }
    fun closeFeature() { val active=foreground; featureOpen=false; pause(); foreground=active }
    fun switchMode(value: DataMode) {
        if(value==mode.value) return
        val wasForeground=foreground
        pause(); mutableMode.value=value
        mutableLocation.value=Result(source=value.name); mutableEnvironment.value=Result(source=value.name); mutablePlaces.value=Result(source=value.name)
        mutableLogs.value=emptyList()
        foreground=wasForeground
        if(value==DataMode.Real) { if(featureOpen && foreground) navigationGateway.start() }
        else {
            mutableLocation.value=Result.success(Coordinates(37.5,127.0,System.currentTimeMillis()),"Demo")
            mutableEnvironment.value=Result.success(EnvironmentData(22.0,3.0,40.0,15.0,10.0,System.currentTimeMillis(),"Demo · 가상 예보"),"Demo")
            mutablePlaces.value=Result.success(listOf(Place("demo","예시 세차장 (가상)","실제 장소 아님",37.501,127.001,142.0)),"Demo")
            log("Mode","가상 데이터 · 실제 SDK/API 호출 없음")
        }
    }
    fun requestLocation(continuous: Boolean) {
        if(mode.value==DataMode.Real && foreground) locationGateway.start(continuous)
    }
    fun stopLocation() { locationGateway.stop(); mutableLocation.value=Result(detail="위치 구독·영역 알림 해제") }
    fun blockedPopup() { if(mode.value==DataMode.Real) locationGateway.showBlocked() }
    fun addArea(radius: Float) { location.value.value?.takeIf { it.fresh() }?.let { if(mode.value==DataMode.Real) locationGateway.addArea(it,radius) } }
    fun removeAreas() { locationGateway.removeAreas(); log("Proximity","해제 요청") }
    fun weather() {
        if(mode.value!=DataMode.Real) { log("Weather","Demo · API 호출 없음"); return }
        val point=location.value.value?.takeIf { it.fresh() }
        if(point==null) { mutableEnvironment.value=Result(ResultState.NoData,detail="최신 위치를 먼저 받아 주세요"); return }
        weatherJob?.cancel()
        val epoch=requestEpoch
        mutableEnvironment.value=Result(ResultState.Loading)
        weatherJob=viewModelScope.launch {
            val result=withContext(Dispatchers.IO) { repository.environment(point) }
            if(epoch==requestEpoch && mode.value==DataMode.Real) {
                mutableEnvironment.value=result; log("Weather/Air",result.state.name+" "+result.detail)
            }
        }
    }
    fun search(kind: PlaceKind) {
        if(mode.value!=DataMode.Real) { log("Place search","Demo · API 호출 없음"); return }
        val point=location.value.value?.takeIf { it.fresh() }
        if(point==null) { mutablePlaces.value=Result(ResultState.NoData,detail="최신 위치를 먼저 받아 주세요"); return }
        placesJob?.cancel()
        val epoch=requestEpoch
        mutablePlaces.value=Result(ResultState.Loading)
        placesJob=viewModelScope.launch {
            val result=withContext(Dispatchers.IO) { repository.places(point,kind) }
            if(epoch==requestEpoch && mode.value==DataMode.Real) { mutablePlaces.value=result; log("Place search",result.state.name+" "+result.detail) }
        }
    }
    fun queryNavigation() { if(mode.value==DataMode.Real) navigationGateway.query() }
    fun navigate(place: Place, append: Boolean) { if(mode.value==DataMode.Real) navigationGateway.route(place,append) }
    fun routeWithWaypoints(destination: Place, waypoints: List<Place>) { if(mode.value==DataMode.Real) navigationGateway.routeWithWaypoints(destination,waypoints) }
    fun cancelRoute() { if(mode.value==DataMode.Real) navigationGateway.cancel() }
    fun option(option: RouteOption) { if(mode.value==DataMode.Real) navigationGateway.option(option) }
    fun reroute() { if(mode.value==DataMode.Real) navigationGateway.reroute() }
    fun removeWaypoint(index: WaypointIndex) { if(mode.value==DataMode.Real) navigationGateway.remove(index) }
    fun navigationScreen() { if(mode.value==DataMode.Real) navigationGateway.screen() }
    override fun onCleared() { pause() }
}

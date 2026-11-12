package com.example.vemoring.vehicle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

class VehicleViewModel(application: Application) : AndroidViewModel(application) {
    private var repository = SharedVehicleConnection.repository(application)
    private val mutable = kotlinx.coroutines.flow.MutableStateFlow(VehicleSnapshot())
    private var collector: kotlinx.coroutines.Job? = null
    private var demo = false
    private val mutableIdentity = kotlinx.coroutines.flow.MutableStateFlow(VehicleIdentityReading(ReadingState.Loading))
    val identity = mutableIdentity.asStateFlow()
    private var cleared = false
    init {
        observe()
        repository.identify { if (!cleared) mutableIdentity.value = it }
    }
    private fun observe() {
        collector?.cancel()
        collector = viewModelScope.launch { repository.state.collect { mutable.value = it } }
    }
    fun setMode(value: com.example.vemoring.integration.DataMode) {
        val next = value == com.example.vemoring.integration.DataMode.Demo
        if(next == demo) return
        if (demo) repository.close() else SharedVehicleConnection.release(this)
        demo = next
        repository = if (demo) VehicleRepository(com.example.vemoring.integration.DemoVehicleGateway())
            else SharedVehicleConnection.repository(getApplication())
        mutable.value = VehicleSnapshot()
        observe()
        updateConnection()
    }
    val state = mutable.asStateFlow()
    private var enabled = false
    private var foreground = false
    fun setEnabled(value: Boolean) { enabled = value; updateConnection() }
    fun resume() { foreground = true; updateConnection() }
    fun pause() { foreground = false; updateConnection() }
    private fun updateConnection() {
        if (demo) { if (enabled && foreground) repository.start() else repository.close() }
        else if (enabled && foreground) SharedVehicleConnection.acquire(getApplication(), this)
        else SharedVehicleConnection.release(this)
    }
    fun refresh() {
        if(!demo && enabled && foreground) SharedVehicleConnection.acquire(getApplication(),this)
        if(!demo && enabled && foreground) SharedVehicleConnection.retryLocation(getApplication())
        repository.refresh()
    }
    override fun onCleared() {
        cleared = true
        if (demo) repository.close() else SharedVehicleConnection.release(this)
    }
}

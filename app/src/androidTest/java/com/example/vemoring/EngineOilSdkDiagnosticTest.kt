package com.example.vemoring

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.sdkFailure
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual SDK callbacks only. A completed diagnostic is not a claim that the sensor is supported. */
class EngineOilSdkDiagnosticTest {
    @Test fun capabilityGetterAndListener() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val completed=CountDownLatch(3)
        val events=JSONArray()
        val report=JSONObject().put("permission",context.checkSelfPermission("pleos.car.permission.CAR_SAFETY"))
            .put("events",events)
        fun result(stage:String,value:String,finish:Boolean=false)=synchronized(report) {
            events.put(JSONObject().put("stage",stage).put("value",value).put("at",System.currentTimeMillis()))
            if(finish) completed.countDown()
        }
        fun failure(stage:String,error:Exception,finish:Boolean=false)=
            result(stage,"${sdkFailure(error).state}: ${error.javaClass.name}: ${error.message}",finish)
        lateinit var vehicle:Vehicle
        lateinit var listener:OilLevelStateListener
        instrumentation.runOnMainSync {
            vehicle=Vehicle(context)
            vehicle.initialize()
            val safety=vehicle.getSafety()
            try {safety.checkEngineOilLevelStateCapabilities({config, supported ->
                result("capability","$config / $supported",true)
            },{failure("capability",it,true)})} catch(e:Exception){failure("capability",e,true)}
            try {safety.getEngineOilLevelState({result("getter",it?.name ?: "null",true)},
                {failure("getter",it,true)})} catch(e:Exception){failure("getter",e,true)}
            try {safety.hasLowWasherFluidWarning({result("washer-control",it?.toString() ?: "null",true)},
                {failure("washer-control",it,true)})} catch(e:Exception){failure("washer-control",e,true)}
            listener=object:OilLevelStateListener {
                override fun onOilLevelUpdated(value:OilLevelState?) {result("listener",value?.name ?: "null")}
                override fun onFailed(error:Exception) {failure("listener",error)}
            }
            try {safety.registerEngineOilLevelState(listener);result("registration","returned")}
            catch(e:Exception){failure("registration",e)}
        }
        try {
            report.put("callbacksCompleted",completed.await(25,TimeUnit.SECONDS))
            val seconds=InstrumentationRegistry.getArguments().getString("observeOilSeconds")?.toLongOrNull()?.coerceIn(0,180) ?: 5
            Thread.sleep(seconds*1000)
        } finally {
            instrumentation.runOnMainSync {
                runCatching {vehicle.getSafety().unregisterEngineOilLevelState(listener)}
                vehicle.release()
            }
            synchronized(report){File(context.filesDir,"engine-oil-diagnostic.json").writeText(report.toString(2))}
        }
    }
}

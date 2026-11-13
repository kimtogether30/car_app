package com.example.vemoring

import ai.pleos.playground.vehicle.Vehicle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.sdkFailure
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the public getter without the capability API that requires PRIVATE_CAR_SAFETY. */
class TelltaleSdkDiagnosticTest {
    @Test fun publicCapabilityAndGetter() {
        val i = InstrumentationRegistry.getInstrumentation()
        val c = i.targetContext
        val done = CountDownLatch(1)
        val report = JSONObject().put("permission", c.checkSelfPermission("pleos.car.permission.CAR_SAFETY"))
        lateinit var vehicle: Vehicle
        fun result(stage: String, value: String) = synchronized(report) {
            report.put(stage, value)
            done.countDown()
        }
        i.runOnMainSync {
            vehicle = Vehicle(c)
            vehicle.initialize()
            val safety = vehicle.getSafety()
            safety.getTelltalesInfo({ result("getter", it?.telltales?.joinToString { t -> "${t.type}:${t.state}:${t.color}" } ?: "null") },
                { result("getter", "${sdkFailure(it).state}: ${it.javaClass.name}: ${it.message}") })
        }
        try { assertTrue("Public SDK callbacks timed out", done.await(20, TimeUnit.SECONDS)) }
        finally {
            File(c.filesDir, "telltale-diagnostic.json").writeText(report.toString(2))
            i.runOnMainSync { vehicle.release() }
        }
    }
}

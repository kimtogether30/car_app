package com.example.vemoring

import androidx.test.platform.app.InstrumentationRegistry
import com.example.vemoring.vehicle.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Observe only. Never changes a VHAL value or promotes an unavailable property to OFF. */
class IgnitionEvidenceTest {
    @Test fun reportActualIgnitionProperty() {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val results=java.util.concurrent.CopyOnWriteArrayList<String>();val latch=CountDownLatch(1)
        var gateway:PleosVehicleGateway?=null
        i.runOnMainSync {gateway=PleosVehicleGateway(c);gateway!!.start {field,reading ->
            if(field==VehicleField.IGNITION) {results.add("${reading.state}:${reading.value?.rawState}:${reading.reason}");latch.countDown()}
        }}
        try {latch.await(15,TimeUnit.SECONDS)} finally {i.runOnMainSync {gateway?.close()}}
        val report=results.joinToString("\n").ifBlank {"No ignition callback received"}
        java.io.File(c.cacheDir,"ignition-evidence.txt").writeText(report)
        println("IGNITION_EVIDENCE=$report")
    }
}

package com.example.vemoring

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class GarageTests {
    private fun isolatedContext(): Context {
        val suffix = java.util.UUID.randomUUID().toString()
        return object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("test_${suffix}_$name", mode)
        }
    }
    @Test fun profileAndEditedRecordSurviveReopen() {
        val context = isolatedContext()
        val original = CareRecord("record", LocalDate.now().minusDays(3), CareKind.WASH, "세차", "첫 기록")
        val profile = CarProfile("현대", "코나", "몽글이", "123가 4567", "전기차", fuelAlert = 15, washAlert = 25)
        val data = AppData(profile, listOf(original), true)
        GarageStore(context).save(data)
        assertEquals(data, GarageStore(context).load())
        val updated = updateLatest(data.records, mapOf(CareKind.WASH to LocalDate.now()))
        assertEquals(1, updated.size)
        assertEquals(original.id, updated.single().id)
        assertEquals(original.detail, updated.single().detail)
        GarageStore(context).save(data.copy(records = updated))
        assertEquals(LocalDate.now(), GarageStore(context).load().records.latest(CareKind.WASH))
    }
    @Test fun futureRecordCannotOverwriteSavedData() {
        val store = GarageStore(isolatedContext())
        val good = AppData(CarProfile("현대", "코나", "몽글이"), complete = true)
        store.save(good)
        try {
            store.save(good.copy(records = listOf(CareRecord(date = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1), kind = CareKind.FUEL, title = "미래"))))
            fail("Expected future date validation")
        } catch (_: IllegalArgumentException) { }
        assertEquals(good, store.load())
    }
    @Test fun flutterPreferencesAreImportedWithoutDeletingOriginals() {
        val context = isolatedContext()
        val old = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        old.edit().putString("flutter.vehicle_brand", "현대").putString("flutter.vehicle_model", "코나")
            .putString("flutter.personal_setup_v1", """{"nickname":"몽글이","fuel":"전기차","fuelAlert":15,"washAlert":25}""")
            .putString("flutter.care_history_v1", """[{"id":"legacy","date":"2025-01-01T00:00:00.000","kind":"wash","title":"세차","detail":"보존"}]""").commit()
        val imported = GarageStore(context).load()
        assertTrue(imported.complete)
        assertEquals("몽글이", imported.profile.nickname)
        assertEquals("보존", imported.records.single().detail)
        GarageStore(context).save(imported)
        assertEquals(imported, GarageStore(context).load())
        assertTrue(old.contains("flutter.personal_setup_v1"))
    }
}

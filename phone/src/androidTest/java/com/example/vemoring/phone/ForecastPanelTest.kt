package com.example.vemoring.phone

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.vemoring.companion.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Uses a disposable application and public Seoul coordinates; never writes the real garage or BT stream. */
class ForecastPanelTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext

    @Test fun actualForecastCacheAndManualRefreshWorkOnAndroid(): Unit=runBlocking {
        assumeTrue("Opt in to the public live API check",InstrumentationRegistry.getArguments().getString("weatherLive")=="1")
        check(context.packageName.endsWith(".verification"))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val region=ForecastLocationPolicy.region(TravelPoint(37.5665,126.9780,Instant.now()),Instant.now())!!
        val fetched=WeeklyWeatherRepository().fetch(region.latitude,region.longitude)
        assertEquals(7,fetched.days.size)
        assertTrue(fetched.days.any {it.maxTemperatureC != null})
        val id="forecast-test-${UUID.randomUUID()}"
        val store=ForecastStore(context,id)
        store.region(region);store.forecast(fetched.copy(fetchedAt=Instant.now().minusSeconds(60)))
        val before=store.read().second!!.fetchedAt
        compose.setContent {MaterialTheme {ForecastWeatherPanel(id,locate={TravelPoint(37.5665,126.9780,Instant.now())})}}
        compose.waitUntil(15000) {compose.onAllNodes(hasText("새로고침") and isEnabled()).fetchSemanticsNodes().size==1}
        compose.onNodeWithText("이번 주 날씨").assertIsDisplayed()
        compose.onNodeWithText("오늘").assertIsDisplayed()
        compose.onNodeWithText("새로고침").performClick()
        compose.waitUntil(35000) {store.read().second?.fetchedAt?.let {it>before}==true}
        assertEquals(region,store.read().first)
        assertEquals(7,store.read().second!!.days.size)
        compose.waitForIdle()
        instrumentation.uiAutomation.takeScreenshot()?.let {bitmap ->
            java.io.File(context.cacheDir,"forecast-live.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithText("기준 ›").performClick()
        compose.onNodeWithText("날씨와 세차 추천").assertIsDisplayed()
        compose.onNodeWithText("확인").performClick()
        Unit
    }

    @Test fun missingCurrentLocationDoesNotExposePreviouslySavedForecast(): Unit=runBlocking {
        check(context.packageName.endsWith(".verification"))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val id="forecast-no-location-${UUID.randomUUID()}"
        val now=Instant.now();val today=now.atZone(FORECAST_ZONE).toLocalDate()
        val store=ForecastStore(context,id)
        store.region(ForecastRegion("서울특별시",37.5,127.0))
        store.forecast(WeeklyWeatherForecast(37.5,127.0,now,List(7) {DailyWeatherForecast(today.plusDays(it.toLong()),0,10.0,23.0,0.0,0.0,0.0)}))
        compose.setContent {MaterialTheme {ForecastWeatherPanel(id,locate={null})}}
        compose.waitUntil(15000) {compose.onAllNodesWithText("현위치 미수신 · 위치 설정 확인").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("23°").assertDoesNotExist()
        compose.onNodeWithText("지역").assertDoesNotExist()
        Unit
    }

    @Test fun approximateWeatherFixKeepsOriginalAgeVisibleWithoutNetwork(): Unit=runBlocking {
        check(context.packageName.endsWith(".verification"))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val now=Instant.now()
        val point=TravelPoint(37.5665,126.9780,now.minusSeconds(120))
        val region=ForecastLocationPolicy.phoneRegion(point,now)!!
        val id="forecast-approximate-${UUID.randomUUID()}"
        val store=ForecastStore(context,id)
        store.region(region)
        val forecast=WeeklyWeatherForecast(region.latitude,region.longitude,now,List(7) {
            DailyWeatherForecast(now.atZone(FORECAST_ZONE).toLocalDate().plusDays(it.toLong()),0,10.0,23.0,0.0,0.0,0.0)
        })
        store.forecast(forecast)
        compose.setContent {MaterialTheme {ForecastWeatherPanel(id,locate={point})}}
        compose.waitUntil(15000) {compose.onAllNodesWithText("최근 확인 위치",substring=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("최근 확인 위치 · "+point.at.atZone(FORECAST_ZONE)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("23°").fetchSemanticsNodes().isNotEmpty())
        assertEquals(now,store.read().second!!.fetchedAt)
        assertFalse(point.fresh(Instant.now()))
        instrumentation.uiAutomation.takeScreenshot()?.let {bitmap ->
            java.io.File(context.cacheDir,"forecast-approximate.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
        }
        Unit
    }

    @Test fun phoneCurrentLocationAdapterAcceptsFreshEmulatorFix(): Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("weatherLocation")=="1")
        check(context.packageName.endsWith(".verification"))
        // Approximate permission only, exactly what the app requests.
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,android.Manifest.permission.ACCESS_COARSE_LOCATION)
        // Exercise the foreground-only adapter with an actual resumed activity, even when run alone.
        compose.setContent {MaterialTheme {androidx.compose.material3.Text("위치 수신 검증")}}
        compose.waitForIdle()
        val point=phoneWeatherLocation(context)
        assertNotNull("A recent approximate emulator fix must reach the weather adapter",point)
        assertTrue(ForecastLocationPolicy.recentPhoneWeatherFix(point!!,Instant.now()))
        // Android blurs approximate locations by up to ~2 km; the forecast grid is ~0.05 degrees.
        assertEquals(37.5665,point.latitude,.05)
        assertEquals(126.9780,point.longitude,.05)
        Unit
    }

    @Test fun regionChangeCannotShowAnotherRegionsCachedForecast()=runBlocking {
        check(context.packageName.endsWith(".verification"))
        val id="forecast-isolation-${UUID.randomUUID()}"
        val store=ForecastStore(context,id)
        val now=Instant.now();val today=now.atZone(FORECAST_ZONE).toLocalDate()
        val region=ForecastRegion("시험 지역 · 실제 위치 아님",37.0,127.0)
        val forecast=WeeklyWeatherForecast(region.latitude,region.longitude,now,
            List(7) {DailyWeatherForecast(today.plusDays(it.toLong()),null,null,null,null,null,null)})
        store.region(region);store.forecast(forecast)
        assertEquals(forecast,ForecastStore(context,id).read().second)
        store.region(region.copy(latitude=35.0))
        assertNull(ForecastStore(context,id).read().second)
        assertNull(ForecastStore(context,"different-$id").read().first)
    }
}

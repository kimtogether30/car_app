package com.example.vemoring.integration

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import com.example.vemoring.vehicle.*

class IntegrationTest {
    @Test fun staleOrInvalidCoordinatesCannotBeUsed() {
        assertFalse(Coordinates(Double.NaN,127.0,1000).valid())
        assertFalse(Coordinates(91.0,127.0,1000).valid())
        assertFalse(Coordinates(37.0,127.0,1).fresh(1000000))
        assertTrue(Coordinates(0.0,0.0,1000).fresh(1000))
    }
    @Test fun demoGatewayCannotPublishRealReadings() {
        val repository=VehicleRepository(DemoVehicleGateway())
        repository.start()
        assertTrue(repository.state.value.readings.values.all { it.state==ReadingState.Demo })
        assertEquals(62.0,repository.state.value[VehicleField.FUEL].value!!.number!!,0.0)
        assertNull(confirmedKilometres(repository.state.value[VehicleField.ODOMETER],"km"))
        repository.close()
        assertTrue(repository.state.value.readings.values.all { it.state==ReadingState.Disconnected })
    }
    @Test fun distanceRequiresRealReadingAndOperatorConfirmedUnit() {
        val reading=Reading.available(SignalValue("1000",1000.0))
        assertNull(confirmedKilometres(reading,""))
        assertEquals(1.0,confirmedKilometres(reading,"m")!!,0.0)
        assertEquals(1000.0,confirmedKilometres(reading,"km")!!,0.0)
        assertNull(confirmedKilometres(Reading(ReadingState.NoData),"km"))
    }
    @Test fun maintenanceUsesDistanceAndNeverForecastRain() {
        val clean=CareEstimator.cleanliness(3,null,null,null,null)
        assertTrue(CareEstimator.cleanliness(3,300.0,null,null,null)<clean)
        assertEquals(clean,CareEstimator.cleanliness(3,null,20.0,null,null))
        assertEquals(clean,CareEstimator.cleanliness(3,null,null,100.0,50.0,10.0))
        assertEquals(0,CareEstimator.cleanliness(1000,null,null,null,null))
        assertEquals(8000.0,CareEstimator.oilRemaining(12000.0,10000.0,10000.0),0.0)
        assertThrows(IllegalArgumentException::class.java) { CareEstimator.oilRemaining(9000.0,10000.0,10000.0) }
    }
    @Test fun externalFailureDoesNotInventWeatherOrPlaces() {
        val fake=HttpExternalDataRepository { _,_ -> throw java.io.IOException() }
        val point=Coordinates(37.5,127.0,1000)
        assertEquals(ResultState.Error,fake.environment(point).state)
        assertNull(fake.environment(point).value)
        assertEquals(ResultState.Error,fake.places(point,PlaceKind.Wash).state)
        assertNull(fake.places(point,PlaceKind.Wash).value)
    }
    @Test fun placesParseNodeAndWayAndComputeDistancesWithoutFakeFields() {
        val queries=mutableListOf<String?>()
        val fake=HttpExternalDataRepository { _,query ->
            queries.add(query)
            JSONObject("""{"elements":[
                {"type":"node","id":1,"lat":37.5,"lon":127.0,"tags":{"name":"Test Fuel"}},
                {"type":"way","id":2,"center":{"lat":37.501,"lon":127.0},"tags":{"name":"Test Wash"}},
                {"type":"node","id":3,"lat":37.5,"lon":127.0,"tags":{}}
            ]}""")
        }
        PlaceKind.entries.forEach { kind ->
            val result=fake.places(Coordinates(37.5,127.0,1000),kind)
            assertEquals(ResultState.Available,result.state)
            assertEquals(2,result.value!!.size)
            assertEquals(0.0,result.value!![0].distanceMeters,0.01)
            assertEquals("주소 미제공",result.value!![0].address)
        }
        assertTrue(queries[0]!!.contains("amenity=fuel"))
        assertTrue(queries[1]!!.contains("amenity=charging_station"))
        assertTrue(queries[2]!!.contains("amenity=car_wash"))
        assertTrue(queries[3]!!.contains("shop=car_repair"))
    }
    @Test fun weatherKeepsZeroAndPartialAirFailureAndLabelsModelSource() {
        val fake=HttpExternalDataRepository { url,_ ->
            if(url.contains("air-quality")) throw java.io.IOException()
            JSONObject("""{"current":{"temperature_2m":0},"daily":{"precipitation_sum":[0,4]}}""")
        }
        val result=fake.environment(Coordinates(37.5,127.0,1))
        assertEquals(ResultState.Available,result.state)
        assertEquals(0.0,result.value!!.temperature!!,0.0)
        assertEquals(0.0,result.value!!.forecastRainMm!!,0.0)
        assertNull(result.value!!.pm10)
        assertTrue(result.source.contains("모델"))
        assertTrue(result.detail.contains("실패"))
    }
    @Test fun invalidCoordinatesNeverReachTransportAndEmptyPlacesAreNotErrors() {
        var calls=0
        val fake=HttpExternalDataRepository { _,_ -> calls++; JSONObject("""{"elements":[]}""") }
        assertEquals(ResultState.Error,fake.places(Coordinates(95.0,0.0,0),PlaceKind.Wash).state)
        assertEquals(0,calls)
        val result=fake.places(Coordinates(0.0,0.0,1),PlaceKind.Wash)
        assertEquals(ResultState.Available,result.state)
        assertTrue(result.value!!.isEmpty())
    }
}

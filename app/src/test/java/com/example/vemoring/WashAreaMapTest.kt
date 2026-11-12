package com.example.vemoring.integration

import com.vemoring.companion.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WashAreaMapTest {
    private fun area()=JSONObject("""{"type":"way","id":12,"tags":{"amenity":"car_wash"},"geometry":[
        {"lat":36.9995,"lon":126.9995},{"lat":37.0005,"lon":126.9995},
        {"lat":37.0005,"lon":127.0005},{"lat":36.9995,"lon":127.0005},{"lat":36.9995,"lon":126.9995}]}""")
    @Test fun geometryResponseKeepsAreaAndSearchCenterWithoutAnotherRequest() {
        var query=""
        val repo=HttpExternalDataRepository { _,q -> query=q!!;JSONObject().put("elements",org.json.JSONArray().put(area())) }
        val result=repo.places(Coordinates(37.0,127.0,1),PlaceKind.Wash)
        assertEquals(ResultState.Available,result.state)
        val place=result.value!!.single()
        assertEquals(37.0,place.latitude,0.000001)
        assertNotNull(place.washSite)
        assertEquals(ZoneEvidence.Inside,FacilityWashPolicy.zone(place.washSite!!,FacilityFix(37.0,127.0,1,1)))
        assertTrue(query.endsWith("out body geom;"))
    }
    @Test fun pointFuelParcelAndOpenWayCannotBecomeWashArea() {
        assertNull(mappedWashSite(area().put("type","node")))
        assertNull(mappedWashSite(area().put("tags",JSONObject().put("amenity","fuel"))))
        val open=area();open.getJSONArray("geometry").remove(4)
        assertNull(mappedWashSite(open))
        assertNull(mappedWashSite(area().put("type","relation")))
    }
}

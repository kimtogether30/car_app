package com.example.vemoring.integration

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

interface ExternalDataRepository {
    fun environment(point: Coordinates): Result<EnvironmentData>
    fun places(point: Coordinates, kind: PlaceKind): Result<List<Place>>
}
/** Called on IO only. No coordinates, keys or raw response bodies are logged. */
class HttpExternalDataRepository(private val request: (String, String?) -> JSONObject = ::requestJson) : ExternalDataRepository {
    override fun environment(point: Coordinates): Result<EnvironmentData> = try {
        require(point.valid())
        val query = "latitude=${point.latitude}&longitude=${point.longitude}"
        val weather = request("https://api.open-meteo.com/v1/forecast?$query&current=temperature_2m&daily=precipitation_sum&forecast_days=2&timezone=auto",null)
        val air = runCatching { request("https://air-quality-api.open-meteo.com/v1/air-quality?$query&current=pm10,pm2_5,dust",null) }
        val current = air.getOrNull()?.optJSONObject("current")
        Result.success(EnvironmentData(weather.optJSONObject("current")?.number("temperature_2m"),
            weather.optJSONObject("daily")?.optJSONArray("precipitation_sum")?.let { if(it.isNull(0)) null else it.optDouble(0).takeIf(Double::isFinite) },
            current?.number("pm10"), current?.number("pm2_5"), current?.number("dust"), System.currentTimeMillis()),
            "Real · Open-Meteo/CAMS 모델").copy(detail=if(air.isFailure) "대기질 조회 실패 · 날씨만 수신" else "")
    } catch(e: Exception) { Result(ResultState.Error, detail=safeError(e)) }
    override fun places(point: Coordinates, kind: PlaceKind): Result<List<Place>> {
        return try {
            require(point.valid())
            val tag=when(kind) {
                PlaceKind.Fuel -> "amenity=fuel"
                PlaceKind.Charge -> "amenity=charging_station"
                PlaceKind.Wash -> "amenity=car_wash"
                PlaceKind.Repair -> "shop=car_repair"
            }
            val output=if(kind==PlaceKind.Wash || kind==PlaceKind.Repair) "out body geom;" else "out center tags;"
            val query="[out:json][timeout:20];nwr(around:10000,${point.latitude},${point.longitude})[$tag];$output"
            val json=request("https://overpass-api.de/api/interpreter", query)
            if(json.has("remark")) throw java.io.IOException("Overpass incomplete response")
            val array=json.getJSONArray("elements")
            val values=(0 until array.length()).mapNotNull { index ->
                val item=array.getJSONObject(index)
                val tags=item.optJSONObject("tags") ?: return@mapNotNull null
                val center=item.optJSONObject("center") ?: geometryCenter(item) ?: item
                val lat=center.number("lat"); val lon=center.number("lon")
                val name=tags.optString("name").ifBlank { tags.optString("brand") }
                    .ifBlank {if(kind==PlaceKind.Wash && tags.optString("amenity")=="car_wash") "세차장" else ""}
                if(lat==null || lon==null || name.isBlank() || !Coordinates(lat,lon,1).valid()) null
                else Place(item.optString("type")+"/"+item.optString("id"),name,
                    listOf("addr:city","addr:street","addr:housenumber").map { tags.optString(it) }.filter { it.isNotBlank() }.joinToString(" ").ifBlank { "주소 미제공" },
                    lat,lon,distanceMeters(point,lat,lon),if(kind==PlaceKind.Wash) mappedWashSite(item) else if(kind==PlaceKind.Repair) mappedRepairSite(item) else null)
            }.sortedBy { it.distanceMeters }.take(20)
            Result.success(values,"Real · © OpenStreetMap contributors / ODbL").copy(detail="OSM 등록 장소 · 영업/충전 가용 상태 미확인")
        } catch(e: Exception) { Result(ResultState.Error, detail=safeError(e)) }
    }
}

/** Only a closed mapped wash WAY. Points, fuel parcels and unsupported relation geometry stay search-only. */
internal fun mappedWashSite(item:JSONObject):com.vemoring.companion.WashSite? {
    if(item.optString("type")!="way" || item.optJSONObject("tags")?.optString("amenity")!="car_wash") return null
    val geometry=item.optJSONArray("geometry") ?: return null
    if(geometry.length() !in 4..256) return null
    return runCatching {
        val boundary=(0 until geometry.length()).map {n -> geometry.getJSONObject(n).let {
            com.vemoring.companion.WashPoint(it.getDouble("lat"),it.getDouble("lon"))
        }}
        val center=item.optJSONObject("center") ?: geometryCenter(item) ?: return null
        com.vemoring.companion.WashSite("way/"+item.optString("id"),center.getDouble("lat"),center.getDouble("lon"),boundary)
            .takeIf {it.mappedArea()}
    }.getOrNull()
}

private fun geometryCenter(item:JSONObject):JSONObject? {
    val points=mutableListOf<Pair<Double,Double>>()
    fun add(a:org.json.JSONArray?) { if(a!=null) for(n in 0 until a.length()) {
        val p=a.optJSONObject(n) ?: continue
        val lat=p.number("lat");val lon=p.number("lon")
        if(lat!=null && lon!=null && Coordinates(lat,lon,1).valid()) points+=lat to lon
    } }
    add(item.optJSONArray("geometry"))
    item.optJSONArray("members")?.let {a -> for(n in 0 until a.length()) add(a.optJSONObject(n)?.optJSONArray("geometry"))}
    if(points.isEmpty()) return null
    return JSONObject().put("lat",(points.minOf {it.first}+points.maxOf {it.first})/2)
        .put("lon",(points.minOf {it.second}+points.maxOf {it.second})/2)
}
private fun requestJson(url: String, query: String?): JSONObject {
        val target=if(query==null) url else url+"?data="+URLEncoder.encode(query,"UTF-8")
        val connection=URI(target).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=10000; connection.readTimeout=30000
            connection.instanceFollowRedirects=false
            connection.setRequestProperty("User-Agent","Vemoring/1.0 (Android prototype)")

            val code=connection.responseCode
            if(code !in 200..299) throw HttpFailure(code)
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }
private class HttpFailure(val code: Int): Exception()
private fun safeError(e: Exception) = if(e is HttpFailure) "HTTP ${e.code}" else e.javaClass.simpleName
private fun JSONObject.number(key: String): Double? = if(isNull(key)) null else optDouble(key).takeIf(Double::isFinite)

/** Require a distinguishable mapped repair-shop WAY, never a POI radius or a fuel parcel. */
internal fun mappedRepairSite(item:JSONObject):com.vemoring.companion.WashSite? {
    if(item.optString("type")!="way" || item.optJSONObject("tags")?.optString("shop")!="car_repair") return null
    val copy=JSONObject(item.toString())
    copy.getJSONObject("tags").put("amenity","car_wash")
    return mappedWashSite(copy)
}

package com.vemoring.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

enum class WeatherSymbol { UNKNOWN, SUN, CLOUD, RAIN, SNOW, THUNDER, FOG }

/** Provider-independent UI input. No sample forecasts or network work in this view. */
data class DailyWeather(val date: LocalDate, val description: String? = null,
    val highCelsius: Double? = null, val lowCelsius: Double? = null,
    val precipitationProbability: Int? = null, val symbol: WeatherSymbol = WeatherSymbol.UNKNOWN)

@Composable
fun WeeklyWeather(modifier: Modifier = Modifier, forecasts: List<DailyWeather> = emptyList(),
    locationName: String? = null, vehicle: Boolean = false,
    today: LocalDate = LocalDate.now(FORECAST_ZONE),
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null) {
    val days = remember(today, forecasts) {
        val byDate = forecasts.associateBy { it.date }
        List(7) { n -> val date = today.plusDays(n.toLong()); byDate[date] ?: DailyWeather(date) }
    }
    val colors = MaterialTheme.colorScheme
    val font = if(vehicle) 18.sp else 12.sp
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(if(vehicle) 20.dp else 18.dp),
        color = colors.surface, border = BorderStroke(1.dp,colors.outlineVariant)) {
        Column(Modifier.padding(horizontal = if(vehicle) 16.dp else 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("이번 주 날씨", fontSize = if(vehicle) 20.sp else 14.sp, lineHeight=if(vehicle) 24.sp else 18.sp, fontWeight = FontWeight.Bold)
                    Text(locationName?.takeIf {it.isNotBlank()} ?: "예보 연결 대기", fontSize = if(vehicle) 16.sp else 11.sp,
                        lineHeight=if(vehicle) 20.sp else 15.sp,color = colors.onSurfaceVariant,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                headerActions?.invoke(this)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                days.forEachIndexed { index, day ->
                    val value = day.highCelsius?.takeIf { it.isFinite() }?.let { "${it.roundToInt()}°" } ?: "—"
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        color = if(index == 0) colors.primaryContainer else Color.Transparent) {
                        Column(Modifier.padding(vertical = if(vehicle) 6.dp else 2.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if(vehicle) 5.dp else 3.dp)) {
                            Text(if(index == 0) "오늘" else day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN),
                                fontSize = font, lineHeight=if(vehicle) 22.sp else 16.sp, color = if(index == 0) colors.primary else colors.onSurfaceVariant,
                                fontWeight = if(index == 0) FontWeight.Bold else FontWeight.Normal)
                            WeatherMark(day.symbol,day.description ?: "예보 미수신", Modifier.size(if(vehicle) 26.dp else 20.dp))
                            Text(value,fontSize = font,lineHeight=if(vehicle) 22.sp else 16.sp,fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            footer?.invoke()
        }
    }
}

/** Small native drawings keep the weather bar light and independent of icon/font dependencies. */
@Composable
private fun WeatherMark(symbol: WeatherSymbol, description: String, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.semantics { contentDescription = description }) {
        val w=size.width; val h=size.height
        val cloud=Color(0xFF9EAFC2); val blue=Color(0xFF4A87DC); val yellow=Color(0xFFFFB43B)
        when(symbol) {
            WeatherSymbol.UNKNOWN -> {
                drawCircle(muted.copy(alpha=.35f),w*.32f,Offset(w*.5f,h*.5f),style=Stroke(w*.065f))
                drawLine(muted,Offset(w*.35f,h*.5f),Offset(w*.65f,h*.5f),w*.06f)
            }
            WeatherSymbol.SUN -> {
                drawCircle(yellow,w*.23f,Offset(w*.5f,h*.5f))
                for(n in 0..7) { val a=n*Math.PI/4; fun at(r:Float)=Offset(w*.5f+kotlin.math.cos(a).toFloat()*r,h*.5f+kotlin.math.sin(a).toFloat()*r)
                    drawLine(yellow,at(w*.32f),at(w*.44f),w*.06f) }
            }
            else -> {
                drawOval(cloud,Offset(w*.08f,h*.3f),androidx.compose.ui.geometry.Size(w*.84f,h*.44f))
                drawCircle(cloud,w*.23f,Offset(w*.48f,h*.35f))
                when(symbol) {
                    WeatherSymbol.RAIN -> for(n in 0..2) drawLine(blue,Offset(w*(.25f+n*.23f),h*.75f),Offset(w*(.18f+n*.23f),h*.98f),w*.06f)
                    WeatherSymbol.SNOW -> for(n in 0..2) drawCircle(blue,w*.045f,Offset(w*(.25f+n*.23f),h*.9f))
                    WeatherSymbol.THUNDER -> { drawLine(yellow,Offset(w*.55f,h*.6f),Offset(w*.4f,h*.84f),w*.12f);drawLine(yellow,Offset(w*.4f,h*.84f),Offset(w*.58f,h*.8f),w*.1f);drawLine(yellow,Offset(w*.58f,h*.8f),Offset(w*.44f,h),w*.1f) }
                    WeatherSymbol.FOG -> for(n in 0..1) drawLine(muted,Offset(w*.15f,h*(.8f+n*.15f)),Offset(w*.85f,h*(.8f+n*.15f)),w*.04f)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun NearbyQuickLinks(vehicle: Boolean = false, onSearch: (String) -> Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=Color(0xFF1B3078)) {
        Row(Modifier.padding(horizontal=if(vehicle) 16.dp else 8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("내 주변",Modifier.weight(1f),fontSize=if(vehicle) 20.sp else 12.sp,color=Color.White,fontWeight=FontWeight.Bold)
            listOf("주유소","세차장","정비소").forEach { label ->
                TextButton(onClick={onSearch(label)},modifier=Modifier.heightIn(min=if(vehicle) 64.dp else 48.dp),
                    contentPadding=PaddingValues(horizontal=if(vehicle) 20.dp else 10.dp)) {
                    Text(label,fontSize=if(vehicle) 20.sp else 12.sp,color=Color.White)
                }
            }
        }
    }
}

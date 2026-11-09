package dev.scrollwerk.launcher.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import dev.scrollwerk.launcher.config.TemperatureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class DayForecast(val date: LocalDate, val code: Int, val high: Double, val low: Double)

data class Weather(
    val place: String,
    val temperature: Double,
    val code: Int,
    val isDay: Boolean,
    val days: List<DayForecast>,
    val unit: TemperatureUnit,
    val fetchedAt: Long,
)

sealed interface WeatherState {
    data object Loading : WeatherState
    /** No city configured and no location permission. */
    data object NeedsLocation : WeatherState
    data class Ready(val weather: Weather) : WeatherState
    data class Failed(val message: String) : WeatherState
}

/** WMO weather interpretation codes, as returned by Open-Meteo. */
enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Storm }

object WeatherCodes {
    fun sky(code: Int): Sky = when (code) {
        0 -> Sky.Clear
        1, 2 -> Sky.PartlyCloudy
        3 -> Sky.Cloudy
        45, 48 -> Sky.Fog
        51, 53, 55, 56, 57 -> Sky.Drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.Rain
        71, 73, 75, 77, 85, 86 -> Sky.Snow
        95, 96, 99 -> Sky.Storm
        else -> Sky.Cloudy
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, hail"
        else -> "Unknown"
    }
}

/** Pure parsing of Open-Meteo responses (unit-tested). */
object OpenMeteo {
    private val json = Json { ignoreUnknownKeys = true }

    fun forecastUrl(lat: Double, lon: Double, unit: TemperatureUnit): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${coord(lat)}&longitude=${coord(lon)}" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=6" +
            if (unit == TemperatureUnit.Fahrenheit) "&temperature_unit=fahrenheit" else ""

    fun geocodeUrl(name: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name=" + URLEncoder.encode(name, "UTF-8")

    /** Two decimals (~1 km): enough for weather, and doesn't send a precise position anywhere. */
    private fun coord(v: Double) = String.format(Locale.ROOT, "%.2f", v)

    fun parseForecast(body: String, place: String, unit: TemperatureUnit, now: Long): Weather {
        val root = json.parseToJsonElement(body).jsonObject
        val current = root.getValue("current").jsonObject
        val daily = root.getValue("daily").jsonObject
        fun arr(key: String): JsonArray = daily.getValue(key).jsonArray
        val dates = arr("time")
        val days = dates.indices.map { i ->
            DayForecast(
                date = LocalDate.parse(dates[i].jsonPrimitive.content),
                code = arr("weather_code")[i].jsonPrimitive.int,
                high = arr("temperature_2m_max")[i].jsonPrimitive.double,
                low = arr("temperature_2m_min")[i].jsonPrimitive.double,
            )
        }
        return Weather(
            place = place,
            temperature = current.getValue("temperature_2m").jsonPrimitive.double,
            code = current.getValue("weather_code").jsonPrimitive.int,
            isDay = current["is_day"]?.jsonPrimitive?.int != 0,
            days = days,
            unit = unit,
            fetchedAt = now,
        )
    }

    /** Returns (lat, lon, display name) or null if the place wasn't found. */
    fun parseGeocode(body: String): Triple<Double, Double, String>? {
        val results = (json.parseToJsonElement(body).jsonObject["results"] as? JsonArray) ?: return null
        val first = results.firstOrNull()?.jsonObject ?: return null
        return Triple(
            first.getValue("latitude").jsonPrimitive.double,
            first.getValue("longitude").jsonPrimitive.double,
            first.getValue("name").jsonPrimitive.content,
        )
    }
}

fun formatTemp(v: Double): String = "${v.roundToInt()}°"

/** Fetches weather, caching for [maxAgeMs] per location setting. */
class WeatherRepository(private val context: Context) {
    private var cached: Weather? = null
    private var cachedKey: String? = null
    private val maxAgeMs = 20 * 60_000L

    suspend fun load(city: String?, unit: TemperatureUnit, hasLocationPermission: Boolean): WeatherState {
        val key = "${city.orEmpty()}|$unit"
        cached?.let { if (cachedKey == key && System.currentTimeMillis() - it.fetchedAt < maxAgeMs) return WeatherState.Ready(it) }
        if (city.isNullOrBlank() && !hasLocationPermission) return WeatherState.NeedsLocation
        return withContext(Dispatchers.IO) {
            runCatching {
                val (lat, lon, place) = if (!city.isNullOrBlank()) {
                    OpenMeteo.parseGeocode(httpGet(OpenMeteo.geocodeUrl(city)))
                        ?: return@withContext WeatherState.Failed("Couldn't find \"$city\"")
                } else {
                    val loc = currentLocation() ?: return@withContext WeatherState.Failed("Location unavailable")
                    Triple(loc.latitude, loc.longitude, placeName(loc))
                }
                val weather = OpenMeteo.parseForecast(
                    httpGet(OpenMeteo.forecastUrl(lat, lon, unit)), place, unit, System.currentTimeMillis(),
                )
                cached = weather
                cachedKey = key
                WeatherState.Ready(weather)
            }.getOrElse { e ->
                cached?.takeIf { cachedKey == key }?.let { WeatherState.Ready(it) } // stale beats nothing
                    ?: WeatherState.Failed(if (e is java.io.IOException) "Offline" else "Weather unavailable")
            }
        }
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "Scrollwerk/0.1 (Android)")
        return try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    @SuppressLint("MissingPermission") // only called after the permission check
    private suspend fun currentLocation(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 60 * 60_000L) return last
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in providers } ?: return last
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                cont.invokeOnCancellation { signal.cancel() }
            }
        }
        return fresh ?: last
    }

    @Suppress("DEPRECATION") // the async variant needs API 33; this runs on Dispatchers.IO
    private fun placeName(loc: Location): String = runCatching {
        if (!Geocoder.isPresent()) return@runCatching null
        Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull() ?: "Current location"
}

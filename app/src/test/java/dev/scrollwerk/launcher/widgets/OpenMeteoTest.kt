package dev.scrollwerk.launcher.widgets

import dev.scrollwerk.launcher.config.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OpenMeteoTest {
    // Trimmed real-shaped Open-Meteo response.
    private val forecast = """
        {"latitude":59.33,"longitude":18.07,"timezone":"Europe/Stockholm",
         "current":{"time":"2026-11-09T15:30","interval":900,"temperature_2m":6.4,"weather_code":3,"is_day":0},
         "daily":{"time":["2026-11-09","2026-11-10","2026-11-11"],
                  "weather_code":[3,61,0],
                  "temperature_2m_max":[7.9,8.4,5.0],
                  "temperature_2m_min":[2.1,4.0,-1.5]}}
    """.trimIndent()

    @Test fun parsesCurrentAndDaily() {
        val w = OpenMeteo.parseForecast(forecast, "Stockholm", TemperatureUnit.Celsius, now = 42L)
        assertEquals(6.4, w.temperature, 0.0)
        assertEquals(3, w.code)
        assertFalse(w.isDay)
        assertEquals(3, w.days.size)
        assertEquals(DayForecast(LocalDate.of(2026, 11, 10), 61, 8.4, 4.0), w.days[1])
        assertEquals("Stockholm", w.place)
    }

    @Test fun parsesGeocodeAndHandlesNoResults() {
        val body = """{"results":[{"id":2673730,"name":"Stockholm","latitude":59.32938,"longitude":18.06871,"country":"Sweden"}]}"""
        assertEquals(Triple(59.32938, 18.06871, "Stockholm"), OpenMeteo.parseGeocode(body))
        assertNull(OpenMeteo.parseGeocode("""{"generationtime_ms":0.5}"""))
    }

    @Test fun forecastUrlRoundsCoordinatesAndSetsUnit() {
        val url = OpenMeteo.forecastUrl(59.329381, 18.068712, TemperatureUnit.Fahrenheit)
        assertTrue(url.contains("latitude=59.33&longitude=18.07"))
        assertTrue(url.endsWith("&temperature_unit=fahrenheit"))
        assertFalse(OpenMeteo.forecastUrl(1.0, 2.0, TemperatureUnit.Celsius).contains("temperature_unit"))
    }

    @Test fun weatherCodesMapToSkies() {
        assertEquals(Sky.Clear, WeatherCodes.sky(0))
        assertEquals(Sky.Rain, WeatherCodes.sky(81))
        assertEquals(Sky.Snow, WeatherCodes.sky(73))
        assertEquals(Sky.Storm, WeatherCodes.sky(95))
        assertEquals("Overcast", WeatherCodes.describe(3))
        assertEquals("-2°", formatTemp(-1.6))
        assertEquals("8°", formatTemp(8.4))
    }
}

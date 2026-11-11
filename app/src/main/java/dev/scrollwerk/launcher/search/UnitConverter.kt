package dev.scrollwerk.launcher.search

import java.math.BigDecimal
import java.math.MathContext

/** "5 km to mi" → 3.10686 mi. Length, mass, temperature, volume, speed, data and time. */
object UnitConverter {
    /** [number] is what gets copied; [text] is shown. */
    data class Conversion(val number: String, val unit: String) {
        val text get() = "$number $unit"
    }

    private enum class Dim { Length, Mass, Temperature, Volume, Speed, Data, Time }

    /** Value in the base unit = (x + offset) × factor. */
    private class Measure(val symbol: String, val dim: Dim, val factor: Double, val offset: Double = 0.0, val aliases: List<String> = emptyList()) {
        fun toBase(x: Double) = (x + offset) * factor
        fun fromBase(b: Double) = b / factor - offset
    }

    private val units = listOf(
        Measure("mm", Dim.Length, 0.001, aliases = listOf("millimeter", "millimeters", "millimetre", "millimetres")),
        Measure("cm", Dim.Length, 0.01, aliases = listOf("centimeter", "centimeters", "centimetre", "centimetres")),
        Measure("dm", Dim.Length, 0.1),
        Measure("m", Dim.Length, 1.0, aliases = listOf("meter", "meters", "metre", "metres")),
        Measure("km", Dim.Length, 1000.0, aliases = listOf("kilometer", "kilometers", "kilometre", "kilometres")),
        Measure("in", Dim.Length, 0.0254, aliases = listOf("inch", "inches", "\"")),
        Measure("ft", Dim.Length, 0.3048, aliases = listOf("foot", "feet", "'")),
        Measure("yd", Dim.Length, 0.9144, aliases = listOf("yard", "yards")),
        Measure("mi", Dim.Length, 1609.344, aliases = listOf("mile", "miles")),
        Measure("nmi", Dim.Length, 1852.0, aliases = listOf("nautical mile", "nautical miles")),

        Measure("mg", Dim.Mass, 1e-6, aliases = listOf("milligram", "milligrams")),
        Measure("g", Dim.Mass, 0.001, aliases = listOf("gram", "grams")),
        Measure("kg", Dim.Mass, 1.0, aliases = listOf("kilo", "kilos", "kilogram", "kilograms")),
        Measure("t", Dim.Mass, 1000.0, aliases = listOf("tonne", "tonnes", "ton", "tons")),
        Measure("oz", Dim.Mass, 0.028349523125, aliases = listOf("ounce", "ounces")),
        Measure("lb", Dim.Mass, 0.45359237, aliases = listOf("lbs", "pound", "pounds")),
        Measure("st", Dim.Mass, 6.35029318, aliases = listOf("stone", "stones")),

        Measure("°C", Dim.Temperature, 1.0, 273.15, listOf("c", "°c", "celsius")),
        Measure("°F", Dim.Temperature, 5.0 / 9, 459.67, listOf("f", "°f", "fahrenheit")),
        Measure("K", Dim.Temperature, 1.0, aliases = listOf("k", "kelvin")),

        Measure("ml", Dim.Volume, 0.001, aliases = listOf("milliliter", "milliliters", "millilitre", "millilitres")),
        Measure("cl", Dim.Volume, 0.01),
        Measure("dl", Dim.Volume, 0.1, aliases = listOf("deciliter", "deciliters")),
        Measure("l", Dim.Volume, 1.0, aliases = listOf("liter", "liters", "litre", "litres")),
        Measure("tsp", Dim.Volume, 0.00492892159375, aliases = listOf("teaspoon", "teaspoons", "tsk")),
        Measure("tbsp", Dim.Volume, 0.01478676478125, aliases = listOf("tablespoon", "tablespoons", "msk")),
        Measure("fl oz", Dim.Volume, 0.0295735295625, aliases = listOf("floz", "fluid ounce", "fluid ounces")),
        Measure("cup", Dim.Volume, 0.2365882365, aliases = listOf("cups")),
        Measure("pt", Dim.Volume, 0.473176473, aliases = listOf("pint", "pints")),
        Measure("qt", Dim.Volume, 0.946352946, aliases = listOf("quart", "quarts")),
        Measure("gal", Dim.Volume, 3.785411784, aliases = listOf("gallon", "gallons")),

        Measure("m/s", Dim.Speed, 1.0, aliases = listOf("mps")),
        Measure("km/h", Dim.Speed, 1 / 3.6, aliases = listOf("kmh", "kph", "kmph")),
        Measure("mph", Dim.Speed, 0.44704),
        Measure("kn", Dim.Speed, 1852 / 3600.0, aliases = listOf("knot", "knots", "kt")),

        Measure("B", Dim.Data, 1.0, aliases = listOf("b", "byte", "bytes")),
        Measure("kB", Dim.Data, 1e3, aliases = listOf("kb")),
        Measure("MB", Dim.Data, 1e6, aliases = listOf("mb")),
        Measure("GB", Dim.Data, 1e9, aliases = listOf("gb")),
        Measure("TB", Dim.Data, 1e12, aliases = listOf("tb")),
        Measure("KiB", Dim.Data, 1024.0, aliases = listOf("kib")),
        Measure("MiB", Dim.Data, 1024.0 * 1024, aliases = listOf("mib")),
        Measure("GiB", Dim.Data, 1024.0 * 1024 * 1024, aliases = listOf("gib")),
        Measure("TiB", Dim.Data, 1024.0 * 1024 * 1024 * 1024, aliases = listOf("tib")),

        Measure("ms", Dim.Time, 0.001, aliases = listOf("millisecond", "milliseconds")),
        Measure("s", Dim.Time, 1.0, aliases = listOf("sec", "secs", "second", "seconds")),
        Measure("min", Dim.Time, 60.0, aliases = listOf("mins", "minute", "minutes")),
        Measure("h", Dim.Time, 3600.0, aliases = listOf("hr", "hrs", "hour", "hours")),
        Measure("d", Dim.Time, 86400.0, aliases = listOf("day", "days")),
        Measure("wk", Dim.Time, 604800.0, aliases = listOf("week", "weeks")),
    )

    /** Lower-cased symbol or alias → unit; the case-sensitive symbols (MB, °C) are listed as aliases too. */
    private val byName: Map<String, Measure> = buildMap {
        units.forEach { u -> (listOf(u.symbol) + u.aliases).forEach { put(it.lowercase(), u) } }
    }

    private val pattern = Regex("""^\s*(-?\d+(?:[.,]\d+)?)\s*(.+?)\s+(?:to|in|as|till|i|=|->)\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)

    fun evaluate(query: String): Conversion? {
        val m = pattern.matchEntire(query) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = byName[m.groupValues[2].trim().lowercase()] ?: return null
        val to = byName[m.groupValues[3].trim().lowercase()] ?: return null
        if (from.dim != to.dim || from === to) return null
        return Conversion(format(to.fromBase(from.toBase(value))), to.symbol)
    }

    /** Six significant digits, no trailing zeros, no exponent for everyday sizes. */
    internal fun format(x: Double): String {
        if (x.isNaN() || x.isInfinite()) return x.toString()
        val rounded = BigDecimal(x).round(MathContext(6)).stripTrailingZeros()
        return if (rounded.compareTo(BigDecimal.ZERO) == 0) "0"
        else if (rounded.abs() >= BigDecimal("1e15") || rounded.abs() < BigDecimal("1e-6")) rounded.toString()
        else rounded.toPlainString()
    }
}

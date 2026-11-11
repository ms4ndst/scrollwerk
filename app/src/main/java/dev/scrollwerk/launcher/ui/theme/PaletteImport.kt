package dev.scrollwerk.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Colour schemes from files in the `palettes/` folder:
 * - Noctalia colour schemes: JSON with `dark` / `light` objects of `mPrimary`, `mSurface`, …
 *   (plain `primary`, `surfaceContainer`, … work too; a file without `dark` / `light` is used for both);
 * - base16 / base24 schemes (tinted-theming YAML or JSON with `base00`…`base0F`).
 */
object PaletteImport {
    private val EXTENSIONS = setOf("json", "yaml", "yml")
    private const val MAX_BYTES = 256 * 1024

    fun accepts(fileName: String) = fileName.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /** Every palette file in [dir], A–Z by file name, and one message per file that didn't load. */
    fun loadFolder(dir: java.io.File): Pair<List<NamedPalette>, List<String>> {
        val files = dir.listFiles { f -> f.isFile && accepts(f.name) }.orEmpty().sortedBy { it.name.lowercase() }
        val palettes = mutableListOf<NamedPalette>()
        val errors = mutableListOf<String>()
        files.forEach { f ->
            val result = if (f.length() > MAX_BYTES) Result.Error("larger than 256 KB")
            else runCatching { parse(f.name, f.readText()) }.getOrElse { Result.Error(it.message ?: "can't read it") }
            when (result) {
                is Result.Ok -> palettes += result.palette
                is Result.Error -> errors += "palettes/${f.name}: ${result.message}"
            }
        }
        return palettes to errors
    }

    sealed interface Result {
        data class Ok(val palette: NamedPalette) : Result
        data class Error(val message: String) : Result
    }

    /** [fileName] names the palette when the file doesn't. */
    fun parse(fileName: String, text: String): Result {
        val fallbackName = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
        return runCatching {
            if (BASE16.containsMatchIn(text)) base16(fallbackName, text) else noctalia(fallbackName, text)
        }.getOrElse { Result.Error(it.message ?: "can't read it") }
    }

    // ------------------------------------------------------------ Noctalia JSON

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; allowComments = true; allowTrailingComma = true }

    private fun noctalia(fallbackName: String, text: String): Result {
        val root = json.parseToJsonElement(text).jsonObject
        val name = (root["name"] as? JsonPrimitive)?.contentOrNull ?: fallbackName
        val darkObj = root.objectAt("dark")
        val lightObj = root.objectAt("light")
        val dark = darkObj?.let(::roles) ?: lightObj?.let(::roles) ?: roles(root)
        val light = lightObj?.let(::roles) ?: dark
        val darkColors = dark?.let(::fill) ?: return Result.Error("no colours found (expected mPrimary, mSurface, … or base00…base0F)")
        val lightColors = light?.let(::fill) ?: darkColors
        return Result.Ok(NamedPalette(name, darkColors, lightColors))
    }

    private fun JsonObject.objectAt(key: String) = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value as? JsonObject

    /** Role → colour, with keys normalised: "mOnSurface", "on_surface" and "onSurface" are all "onsurface". */
    private fun roles(obj: JsonObject): Map<String, Color>? {
        val out = HashMap<String, Color>()
        obj.forEach { (k, v) ->
            // Noctalia's "m" prefix only when it's followed by a capital: "mPrimary", not "magenta".
            val bare = if (k.length > 1 && k[0] == 'm' && k[1].isUpperCase()) k.drop(1) else k
            val key = bare.lowercase().replace("_", "").replace("-", "")
            val color = (v as? JsonPrimitive)?.contentOrNull?.let(::hex) ?: return@forEach
            out[ALIASES[key] ?: key] = color
        }
        return out.takeIf { "primary" in it && "surface" in it }
    }

    private val ALIASES = mapOf(
        "surfacevariant" to "surfacecontainer",
        "outline" to "outlinevariant",
    )

    /** Fills in any role the file left out, the way Noctalia derives them. */
    private fun fill(r: Map<String, Color>): NoctaliaColors {
        val surface = r.getValue("surface")
        val onSurface = r["onsurface"] ?: if (surface.luminance() > 0.5f) Color(0xFF1B1B1F) else Color(0xFFE6E1E5)
        val primary = r.getValue("primary")
        val secondary = r["secondary"] ?: primary
        val tertiary = r["tertiary"] ?: secondary
        val onAccent = r["onprimary"] ?: surface
        return NoctaliaColors(
            primary = primary, onPrimary = onAccent,
            secondary = secondary, onSecondary = r["onsecondary"] ?: onAccent,
            tertiary = tertiary, onTertiary = r["ontertiary"] ?: onAccent,
            error = r["error"] ?: Color(0xFFE5534B), onError = r["onerror"] ?: onAccent,
            surface = surface, onSurface = onSurface,
            surfaceContainer = r["surfacecontainer"] ?: lerp(surface, onSurface, 0.08f),
            onSurfaceVariant = r["onsurfacevariant"] ?: lerp(onSurface, surface, 0.35f),
            outlineVariant = r["outlinevariant"] ?: lerp(surface, onSurface, 0.2f),
            shadow = r["shadow"] ?: Color.Black,
            hover = r["hover"] ?: tertiary,
            onHover = r["onhover"] ?: r["ontertiary"] ?: onAccent,
        )
    }

    // ------------------------------------------------------------ base16 / base24

    private val BASE16 = Regex("""\bbase0[0-9A-Fa-f]\b\s*"?\s*:""")
    private val BASE_ENTRY = Regex("""\b(base[0-9A-Fa-f]{2})\b"?\s*:\s*["']?#?([0-9A-Fa-f]{6})\b""")
    // Quoted names may hold commas ("Gruvbox dark, medium"); bare ones end at a comma or comment.
    private val NAME = Regex("""^\s*"?(?:name|scheme)"?\s*:\s*(?:"([^"\n]+)"|'([^'\n]+)'|([^"'\s][^\n,#]*))""", RegexOption.MULTILINE)
    private val VARIANT = Regex("""^\s*"?variant"?\s*:\s*["']?(light|dark)""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    private fun base16(fallbackName: String, text: String): Result {
        val b = BASE_ENTRY.findAll(text).associate { it.groupValues[1].lowercase() to hex(it.groupValues[2])!! }
        val missing = (0..15).map { "base0%x".format(it) }.filter { it !in b }
        if (missing.isNotEmpty()) return Result.Error("base16 scheme is missing ${missing.joinToString()}")
        fun c(i: Int) = b.getValue("base0%x".format(i))
        val name = NAME.find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.trim() ?: fallbackName
        val isLight = VARIANT.find(text)?.groupValues?.get(1)?.equals("light", true) ?: (c(0).luminance() > 0.5f)

        // base00…07 runs background → foreground; flip it for the opposite mode.
        fun shade(i: Int, flip: Boolean) = c(if (flip) 7 - i else i)
        fun variant(flip: Boolean): NoctaliaColors {
            val bg = shade(0, flip)
            return NoctaliaColors(
                primary = c(0xD), onPrimary = bg,          // blue: functions
                secondary = c(0xE), onSecondary = bg,      // purple: keywords
                tertiary = c(0xC), onTertiary = bg,        // cyan: support
                error = c(0x8), onError = bg,              // red: variables / errors
                surface = bg, onSurface = shade(5, flip),
                surfaceContainer = shade(1, flip),
                onSurfaceVariant = shade(4, flip),
                outlineVariant = shade(2, flip),
                shadow = if (flip == isLight) Color.Black else bg,
                hover = c(0xC), onHover = bg,
            )
        }
        val own = variant(flip = false)
        val other = variant(flip = true)
        return Result.Ok(if (isLight) NamedPalette(name, dark = other, light = own) else NamedPalette(name, dark = own, light = other))
    }

    private fun hex(s: String): Color? {
        val h = s.trim().removePrefix("#")
        val v = h.toLongOrNull(16) ?: return null
        return when (h.length) {
            6 -> Color(0xFF000000 or v)
            8 -> Color(v) // AARRGGBB
            else -> null
        }
    }
}

package dev.scrollwerk.launcher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.scrollwerk.launcher.R
import java.io.File

/** Where one weight of a font comes from: the app's resources, or a file in `fonts/`. */
sealed interface FontRef {
    data class Res(val id: Int) : FontRef
    data class FileRef(val file: File) : FontRef
}

/** A font people can pick: [weights] maps 400 / 500 / 700 … to its files. */
class FontOption(val label: String, val weights: Map<Int, FontRef>) {
    private fun font(ref: FontRef, weight: Int) = when (ref) {
        is FontRef.Res -> Font(ref.id, FontWeight(weight))
        is FontRef.FileRef -> Font(ref.file, FontWeight(weight))
    }

    /** Its weights below [below] (all of them by default), for running text. */
    fun textFonts(below: Int = 1000): List<Font> = weights.filterKeys { it < below }.map { (w, ref) -> font(ref, w) }

    /** Its boldest file standing in for SemiBold and Bold, so headings use it as drawn (no fake bold). */
    fun headingFonts(): List<Font> {
        val ref = weights[700] ?: weights.maxBy { it.key }.value
        return listOf(font(ref, 600), font(ref, 700))
    }

    /** The font on its own, e.g. to show its name in itself. */
    val family: FontFamily by lazy { FontFamily(textFonts()) }
}

/**
 * Fonts that suit the themes, from Google Fonts (SIL OFL 1.1; licenses in assets/licenses/fonts).
 * Built by tools/gen_fonts.py as Latin-only Regular / Medium / Bold, or Regular only for
 * single-weight fonts; other characters fall back to the phone's fonts.
 */
enum class ThemeFont(label: String, regular: Int, medium: Int? = null, bold: Int? = null) {
    ZenKakuGothicNew("Zen Kaku Gothic New", R.font.zen_kaku_gothic_new_regular, R.font.zen_kaku_gothic_new_medium, R.font.zen_kaku_gothic_new_bold),
    ZcoolQingKeHuangYou("ZCOOL QingKe HuangYou", R.font.zcool_qingke_huangyou_regular),
    PixelifySans("Pixelify Sans", R.font.pixelify_sans_regular, R.font.pixelify_sans_medium, R.font.pixelify_sans_bold),
    DotGothic16("DotGothic16", R.font.dotgothic16_regular),
    JetBrainsMono("JetBrains Mono", R.font.jetbrains_mono_regular, R.font.jetbrains_mono_medium, R.font.jetbrains_mono_bold),
    Inter("Inter", R.font.inter_regular, R.font.inter_medium, R.font.inter_bold),
    Nunito("Nunito", R.font.nunito_regular, R.font.nunito_medium, R.font.nunito_bold),
    JosefinSans("Josefin Sans", R.font.josefin_sans_regular, R.font.josefin_sans_medium, R.font.josefin_sans_bold),
    Lora("Lora", R.font.lora_regular, R.font.lora_medium, R.font.lora_bold),
    Rubik("Rubik", R.font.rubik_regular, R.font.rubik_medium, R.font.rubik_bold),
    SpaceGrotesk("Space Grotesk", R.font.space_grotesk_regular, R.font.space_grotesk_medium, R.font.space_grotesk_bold),
    Quicksand("Quicksand", R.font.quicksand_regular, R.font.quicksand_medium, R.font.quicksand_bold),
    SourceSans3("Source Sans 3", R.font.source_sans_3_regular, R.font.source_sans_3_medium, R.font.source_sans_3_bold),
    FiraSans("Fira Sans", R.font.fira_sans_regular, R.font.fira_sans_medium, R.font.fira_sans_bold),
    FiraCode("Fira Code", R.font.fira_code_regular, R.font.fira_code_medium, R.font.fira_code_bold),
    Exo2("Exo 2", R.font.exo_2_regular, R.font.exo_2_medium, R.font.exo_2_bold),
    IbmPlexSans("IBM Plex Sans", R.font.ibm_plex_sans_regular, R.font.ibm_plex_sans_medium, R.font.ibm_plex_sans_bold),

    // Display fonts to pick by hand, mostly for headings. tools/gen_fonts.py scales each so its capitals stand as
    // tall as the text fonts', so they sit at the size the app asks for.
    Shojumaru("Shojumaru", R.font.shojumaru_regular),
    ZcoolKuaiLe("ZCOOL KuaiLe", R.font.zcool_kuaile_regular),
    Gugi("Gugi", R.font.gugi_regular),
    DoHyeon("Do Hyeon", R.font.do_hyeon_regular),
    BlackHanSans("Black Han Sans", R.font.black_han_sans_regular),
    KdamThmorPro("Kdam Thmor Pro", R.font.kdam_thmor_pro_regular),
    Tektur("Tektur", R.font.tektur_regular, R.font.tektur_medium, R.font.tektur_bold),
    ChakraPetch("Chakra Petch", R.font.chakra_petch_regular, R.font.chakra_petch_medium, R.font.chakra_petch_bold),
    Goldman("Goldman", R.font.goldman_regular, bold = R.font.goldman_bold),
    RussoOne("Russo One", R.font.russo_one_regular),
    BlackOpsOne("Black Ops One", R.font.black_ops_one_regular),
    Sarpanch("Sarpanch", R.font.sarpanch_regular, R.font.sarpanch_medium, R.font.sarpanch_bold),
    Oxanium("Oxanium", R.font.oxanium_regular, R.font.oxanium_medium, R.font.oxanium_bold),
    SquadaOne("Squada One", R.font.squada_one_regular),
    TurretRoad("Turret Road", R.font.turret_road_regular, R.font.turret_road_medium, R.font.turret_road_bold),
    Wallpoet("Wallpoet", R.font.wallpoet_regular),
    ;

    val option = FontOption(
        label,
        buildMap {
            put(400, FontRef.Res(regular))
            medium?.let { put(500, FontRef.Res(it)) }
            bold?.let { put(700, FontRef.Res(it)) }
        },
    )
}

/** A theme's fonts: [text] for running text, [heading] (if any) for SemiBold / Bold. null = the phone's. */
data class FontPair(val text: FontOption?, val heading: FontOption? = null)

object ThemeFonts {
    /** config: the palette's own choice. */
    const val THEME = "theme"
    /** config `theme.font`: the phone's font. */
    const val SYSTEM = "system"
    /** config `theme.headingFont`: headings in the text font's own bold. */
    const val SAME = "text"

    /** Fonts from the `fonts/` folder; a name shared with a bundled font replaces it. */
    var user: List<FontOption> by mutableStateOf(emptyList())

    /** Bundled fonts, then the user's, A–Z within each. */
    val all: List<FontOption>
        get() = ThemeFont.entries.map { it.option }.filter { b -> user.none { it.label.equals(b.label, true) } }
            .sortedBy { it.label.lowercase() } + user

    fun option(label: String?): FontOption? = label?.trim()?.let { l ->
        user.lastOrNull { it.label.equals(l, ignoreCase = true) }
            ?: ThemeFont.entries.firstOrNull { it.option.label.equals(l, ignoreCase = true) }?.option
    }

    /** Each built-in palette's fonts; Noctalia and the wallpaper palette keep the phone's. */
    private val byPalette = mapOf(
        // Narrow East Asian shop-sign letters for headings, a calm Japanese gothic for text.
        "Kanagawa" to FontPair(ThemeFont.ZenKakuGothicNew.option, ThemeFont.ZcoolQingKeHuangYou.option),
        "Tokyo-Night" to FontPair(ThemeFont.PixelifySans.option), // pixel letters of arcades and neon signs
        "Gruvbox" to FontPair(ThemeFont.JetBrainsMono.option),    // retro terminal
        "Nord" to FontPair(ThemeFont.Inter.option),               // clean Nordic sans
        "Catppuccin" to FontPair(ThemeFont.Nunito.option),        // soft and rounded
        "Dracula" to FontPair(ThemeFont.JosefinSans.option),      // vintage elegance
        "Rosé Pine" to FontPair(ThemeFont.Lora.option),           // soft serif
        "Ayu" to FontPair(ThemeFont.Rubik.option),
        "Eldritch" to FontPair(ThemeFont.SpaceGrotesk.option),
        "Everforest" to FontPair(ThemeFont.Quicksand.option),
        "Solarized" to FontPair(ThemeFont.SourceSans3.option),
        "One" to FontPair(ThemeFont.FiraSans.option),
        "Monokai Pro" to FontPair(ThemeFont.FiraCode.option),
        "Synthwave '84" to FontPair(ThemeFont.Exo2.option),
        "Oxocarbon" to FontPair(ThemeFont.IbmPlexSans.option),    // IBM Carbon
    )

    /** The palette's fonts: what its file names (imported palettes), else the built-in pairing. */
    fun forPalette(palette: NamedPalette?): FontPair {
        if (palette == null) return FontPair(null)
        val builtIn = byPalette.entries.firstOrNull { it.key.equals(palette.name, ignoreCase = true) }?.value ?: FontPair(null)
        return FontPair(
            text = option(palette.font) ?: builtIn.text,
            heading = option(palette.headingFont) ?: builtIn.heading,
        )
    }

    /** What `theme.font` and `theme.headingFont` resolve to for [palette]. */
    fun resolve(text: String, heading: String, palette: NamedPalette?): FontPair {
        val theme = forPalette(palette)
        val t = when {
            text.equals(SYSTEM, ignoreCase = true) -> null
            text.equals(THEME, ignoreCase = true) -> theme.text
            else -> option(text) ?: theme.text
        }
        val h = when {
            heading.equals(SAME, ignoreCase = true) -> null
            heading.equals(THEME, ignoreCase = true) -> theme.heading
            else -> option(heading) ?: theme.heading
        }
        return FontPair(t, h)
    }

    /** One family from a pair: the text font below SemiBold, the heading font from SemiBold up; null = all the phone's. */
    fun family(pair: FontPair): FontFamily? {
        if (pair.text == null && pair.heading == null) return null
        val text = pair.text?.textFonts(below = if (pair.heading != null) 600 else 1000)
            ?: listOf(400, 500).map { Font(DeviceFontFamilyName("sans-serif"), FontWeight(it)) }
        return FontFamily(text + pair.heading?.headingFonts().orEmpty())
    }
}

package dev.scrollwerk.launcher.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.random.Random

/**
 * Draws a wallpaper from a palette: a base fading from the container colour (top) to the
 * surface colour (bottom), a large glow in the secondary accent (top right), a smaller
 * tertiary one (left), a low primary glow, and a soft vignette.
 * Deterministic: the same palette and size always give the same image.
 */
object WallpaperGenerator {

    fun render(colors: NoctaliaColors, width: Int, height: Int, seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val m = max(w, h)
        val rnd = Random(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(0f, 0f, 0f, h, colors.surfaceContainer.toArgb(), colors.surface.toArgb(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        glow(canvas, paint, colors.secondary, w * (0.7f + rnd.nextFloat() * 0.25f), h * (0.08f + rnd.nextFloat() * 0.1f), m * 0.62f, 0.42f)
        glow(canvas, paint, colors.tertiary, w * (0.05f + rnd.nextFloat() * 0.2f), h * (0.42f + rnd.nextFloat() * 0.1f), m * 0.45f, 0.30f)

        // A low, soft primary glow anchors the bottom of the screen.
        glow(canvas, paint, colors.primary, w * (0.25f + rnd.nextFloat() * 0.5f), h * (0.8f + rnd.nextFloat() * 0.08f), m * 0.38f, 0.18f)
        vignette(canvas, paint, colors.surface, w, h)
        return bitmap
    }

    /**
     * The lock-screen version: calmer and darker so the lock clock and notifications stay readable.
     * The base runs the other way (surface on top), a soft primary glow sits behind the clock, and
     * the accent glows gather low on the screen. No shapes: on a lock screen they read as empty boxes.
     */
    fun renderLock(colors: NoctaliaColors, width: Int, height: Int, seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val m = max(w, h)
        val rnd = Random(seed xor 0x10C4)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(0f, 0f, 0f, h, colors.surface.toArgb(), colors.surfaceContainer.toArgb(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        glow(canvas, paint, colors.primary, w * 0.5f, h * (0.22f + rnd.nextFloat() * 0.06f), m * 0.42f, 0.16f)
        glow(canvas, paint, colors.secondary, w * (0.02f + rnd.nextFloat() * 0.15f), h * (0.86f + rnd.nextFloat() * 0.08f), m * 0.5f, 0.26f)
        glow(canvas, paint, colors.tertiary, w * (0.85f + rnd.nextFloat() * 0.12f), h * (0.66f + rnd.nextFloat() * 0.08f), m * 0.36f, 0.20f)
        glow(canvas, paint, colors.primary, w * (0.4f + rnd.nextFloat() * 0.2f), h * 1.02f, m * 0.4f, 0.18f)

        vignette(canvas, paint, colors.surface, w, h)
        return bitmap
    }

    /** Soft glow whose alpha falls off as (1 - d)^1.6, approximated with gradient stops. */
    private fun glow(canvas: Canvas, paint: Paint, color: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
        val stops = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val falloff = floatArrayOf(1f, 0.63f, 0.33f, 0.11f, 0f)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, r,
            IntArray(stops.size) { color.copy(alpha = alpha * falloff[it]).toArgb() },
            stops,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    /** Elliptical vignette towards the surface colour, following the screen's aspect ratio. */
    private fun vignette(canvas: Canvas, paint: Paint, surface: Color, w: Float, h: Float) {
        val cx = w / 2
        val cy = h * 0.45f
        val r = h * 0.75f
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(Color.Transparent.toArgb(), Color.Transparent.toArgb(), surface.copy(alpha = 0.4f).toArgb()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.save()
        canvas.scale(w / h, 1f, cx, cy) // circle → ellipse with the screen's proportions
        canvas.drawRect(cx - h * 2, -h, cx + h * 2, h * 2, paint)
        canvas.restore()
        paint.shader = null
    }

    /** Stable seed per palette, so each theme always gets "its" wallpaper. */
    fun seedFor(paletteName: String): Int = paletteName.lowercase().hashCode()
}

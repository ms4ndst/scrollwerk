package dev.scrollwerk.launcher.data

import kotlin.math.abs

/**
 * Themed icons for apps without a monochrome layer: pulls the glyph out of a full-colour icon
 * so it can be drawn in the theme's colour. The icon's dominant luminance is taken to be its
 * background; each pixel's alpha becomes how much it stands out from that.
 */
object IconGlyph {
    private const val BUCKETS = 16
    /** Below this much contrast the icon is one colour, so its shape is the glyph. */
    private const val MIN_CONTRAST = 0.15f
    /** Mid-contrast detail (outlines, secondary shapes) still comes out solid. */
    private const val BOOST = 1.3f
    /** Shading this close to the background (gradients, anti-aliasing noise) stays transparent. */
    private const val DEAD_ZONE = 0.04f

    /** [argb] pixels in, glyph alpha (0–255) per pixel out. */
    fun alphaMask(argb: IntArray): IntArray {
        val n = argb.size
        val alpha = IntArray(n) { argb[it] ushr 24 }
        val lum = FloatArray(n) { luminance(argb[it]) }

        // Dominant luminance, weighted by opacity: the exact mean of the fullest bucket.
        fun bucket(l: Float) = (l * BUCKETS).toInt().coerceAtMost(BUCKETS - 1)
        val weight = FloatArray(BUCKETS)
        val sum = FloatArray(BUCKETS)
        for (i in 0 until n) {
            val b = bucket(lum[i])
            weight[b] += alpha[i] / 255f
            sum[b] += lum[i] * alpha[i] / 255f
        }
        val top = weight.indices.maxByOrNull { weight[it] } ?: return alpha
        if (weight[top] == 0f) return alpha
        val ref = sum[top] / weight[top]

        var maxDiff = 0f
        for (i in 0 until n) if (alpha[i] > 32) maxDiff = maxOf(maxDiff, abs(lum[i] - ref))
        if (maxDiff < MIN_CONTRAST) return alpha // single colour: use the silhouette

        return IntArray(n) { i ->
            val strength = ((abs(lum[i] - ref) - DEAD_ZONE) / (maxDiff - DEAD_ZONE) * BOOST).coerceIn(0f, 1f)
            (alpha[i] * strength).toInt()
        }
    }

    private fun luminance(c: Int): Float {
        val r = (c shr 16 and 0xFF) / 255f
        val g = (c shr 8 and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}

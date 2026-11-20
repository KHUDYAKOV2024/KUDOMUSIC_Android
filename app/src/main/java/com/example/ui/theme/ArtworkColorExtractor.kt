package com.example.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class CoverColorPalette(
    val softTop: Color,
    val softMid: Color,
    val surfaceTint: Color,
    val accent: Color
)

object ArtworkColorExtractor {
    private val cache = ConcurrentHashMap<String, Color>()
    private val defaultBase = Color(0xFF1F3B33)

    suspend fun extractHarmoniousColor(
        context: Context,
        coverUrl: String?,
        localArtPath: String?,
        authHeaders: Map<String, String> = emptyMap()
    ): Color = withContext(Dispatchers.IO) {
        val cacheKey = localArtPath?.takeIf { it.isNotBlank() }
            ?: coverUrl?.takeIf { it.isNotBlank() }
            ?: return@withContext defaultBase

        cache[cacheKey]?.let { return@withContext it }

        val bitmap: Bitmap? = when {
            !localArtPath.isNullOrBlank() -> {
                val file = File(localArtPath)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
                } else null
            }
            !coverUrl.isNullOrBlank() -> {
                runCatching {
                    val loader = ImageLoader(context)
                    val req = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .size(96)
                        .allowHardware(false)
                        .apply { authHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .build()
                    val result = loader.execute(req)
                    if (result is SuccessResult) {
                        (result.drawable as? BitmapDrawable)?.bitmap
                    } else null
                }.getOrNull()
            }
            else -> null
        }

        val extracted = if (bitmap != null) computeSoftColor(bitmap) else defaultBase
        cache[cacheKey] = extracted
        extracted
    }

    private fun computeSoftColor(bitmap: Bitmap): Color {
        val small = Bitmap.createScaledBitmap(bitmap, 20, 20, true)
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        var totalWeight = 0.0
        val hsv = FloatArray(3)

        for (y in 0 until small.height) {
            for (x in 0 until small.width) {
                val px = small.getPixel(x, y)
                val r = AndroidColor.red(px)
                val g = AndroidColor.green(px)
                val b = AndroidColor.blue(px)
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val sat = hsv[1]
                val value = hsv[2]

                // Skip near-black and blown-out white pixels; prefer colorful mid-tones
                if (value < 0.12f || value > 0.96f) continue
                val weight = 0.35 + (sat * sat * 2.4)
                rSum += r * weight
                gSum += g * weight
                bSum += b * weight
                totalWeight += weight
            }
        }

        if (totalWeight <= 0.0) return defaultBase
        val avgR = (rSum / totalWeight).toInt().coerceIn(0, 255)
        val avgG = (gSum / totalWeight).toInt().coerceIn(0, 255)
        val avgB = (bSum / totalWeight).toInt().coerceIn(0, 255)

        // Refine HSV into a gentle, breathable, atmospheric dark-mode compatible hue
        AndroidColor.RGBToHSV(avgR, avgG, avgB, hsv)
        hsv[1] = hsv[1].coerceIn(0.24f, 0.62f)
        hsv[2] = hsv[2].coerceIn(0.32f, 0.56f)
        return Color(AndroidColor.HSVToColor(hsv))
    }

    fun toPalette(baseColor: Color): CoverColorPalette {
        return CoverColorPalette(
            softTop = baseColor.copy(alpha = 0.52f),
            softMid = baseColor.copy(alpha = 0.18f),
            surfaceTint = baseColor.copy(alpha = 0.24f),
            accent = baseColor
        )
    }
}

@Composable
fun rememberCoverColorPalette(
    coverUrl: String?,
    localArtPath: String?,
    authHeaders: Map<String, String> = emptyMap()
): CoverColorPalette {
    val context = LocalContext.current
    val key = localArtPath ?: coverUrl ?: "default"
    var targetBase by remember { mutableStateOf(Color(0xFF1F3B33)) }

    LaunchedEffect(key) {
        targetBase = ArtworkColorExtractor.extractHarmoniousColor(
            context = context,
            coverUrl = coverUrl,
            localArtPath = localArtPath,
            authHeaders = authHeaders
        )
    }

    val animatedBase by animateColorAsState(
        targetValue = targetBase,
        animationSpec = tween(durationMillis = 550),
        label = "cover_palette_color"
    )

    return ArtworkColorExtractor.toPalette(animatedBase)
}

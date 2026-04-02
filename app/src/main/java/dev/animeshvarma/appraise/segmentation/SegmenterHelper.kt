package dev.animeshvarma.appraise.segmentation

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import dev.animeshvarma.appraise.model.ScannerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SegmenterHelper {

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()

    private val segmenter = SubjectSegmentation.getClient(options)

    suspend fun segmentImage(originalBitmap: Bitmap): ScannerState.Segmented = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(originalBitmap, 0)
        val result = segmenter.process(image).await()

        val rawForeground = result.foregroundBitmap
            ?: throw IllegalStateException("Could not extract object.")

        val foregroundBitmap = isolatePrimarySubject(rawForeground)

        val maskPaint = Paint().apply { maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL) }
        val offset = IntArray(2)
        val alphaMask = foregroundBitmap.extractAlpha(maskPaint, offset)

        val darkMaskedBitmap = createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(darkMaskedBitmap)
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)
        canvas.drawColor(Color.argb(220, 0, 0, 0))
        canvas.saveLayer(0f, 0f, originalBitmap.width.toFloat(), originalBitmap.height.toFloat(), null)
        val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(alphaMask, offset[0].toFloat(), offset[1].toFloat(), blendPaint)
        blendPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(originalBitmap, 0f, 0f, blendPaint)
        canvas.restore()

        alphaMask.recycle()
        return@withContext ScannerState.Segmented(originalBitmap, darkMaskedBitmap, foregroundBitmap)
    }

    suspend fun frameImage(original: Bitmap, foreground: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = original.width
        val height = original.height

        val pixels = IntArray((width * 0.5f).toInt() * (height * 0.5f).toInt())
        val coreW = (width * 0.5f).toInt()
        val coreH = (height * 0.5f).toInt()
        val coreBitmap = Bitmap.createBitmap(foreground, (width - coreW) / 2, (height - coreH) / 2, coreW, coreH)
        coreBitmap.getPixels(pixels, 0, coreW, 0, 0, coreW, coreH)

        var totalLum = 0f
        var count = 0
        for (pixel in pixels) {
            if (Color.alpha(pixel) > 128) {
                totalLum += ColorUtils.calculateLuminance(pixel).toFloat()
                count++
            }
        }
        coreBitmap.recycle()

        val rarityColor = Color.CYAN
        val rarityHsl = FloatArray(3)
        ColorUtils.colorToHSL(rarityColor, rarityHsl)

        val bgSaturation = 0.5f
        val bgLightness = 0.57f

        val bgColor = ColorUtils.HSLToColor(floatArrayOf(rarityHsl[0], bgSaturation, bgLightness))

        val spotLightness = minOf(0.9f, bgLightness + 0.15f)
        val spotSaturation = 0.35f
        val spotlightColor = ColorUtils.HSLToColor(floatArrayOf(rarityHsl[0], spotSaturation, spotLightness))

        val outputBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        canvas.drawColor(bgColor)

        val centerX = width / 2f
        val centerY = height / 2f
        val auraPaint = Paint().apply {
            shader = RadialGradient(
                centerX, centerY, maxOf(width, height) * 0.8f,
                spotlightColor,
                Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), auraPaint)

        val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rarityColor
            maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)
        }
        val outerOffset = IntArray(2)
        val outerGlowAlpha = foreground.extractAlpha(outerGlowPaint, outerOffset)
        canvas.drawBitmap(outerGlowAlpha, outerOffset[0].toFloat(), outerOffset[1].toFloat(), outerGlowPaint)
        outerGlowAlpha.recycle()

        val aaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
        }
        val aaOffset = IntArray(2)
        val aaAlphaMask = foreground.extractAlpha(aaPaint, aaOffset)

        canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(aaAlphaMask, aaOffset[0].toFloat(), aaOffset[1].toFloat(), blendPaint)

        blendPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(original, 0f, 0f, blendPaint)

        canvas.restore()
        aaAlphaMask.recycle()

        return@withContext outputBitmap
    }


    private fun isolatePrimarySubject(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        var maxBlobSize = 0
        var maxBlobStart = -1

        for (i in pixels.indices) {
            if (!visited[i] && Color.alpha(pixels[i]) > 0) {
                var head = 0
                var tail = 0
                queue[tail++] = i
                visited[i] = true
                var currentBlobSize = 0

                while (head < tail) {
                    val curr = queue[head++]
                    currentBlobSize++
                    val x = curr % width
                    val y = curr / width

                    if (x > 0 && !visited[curr - 1] && Color.alpha(pixels[curr - 1]) > 0) {
                        visited[curr - 1] = true
                        queue[tail++] = curr - 1
                    }
                    if (x < width - 1 && !visited[curr + 1] && Color.alpha(pixels[curr + 1]) > 0) {
                        visited[curr + 1] = true
                        queue[tail++] = curr + 1
                    }
                    if (y > 0 && !visited[curr - width] && Color.alpha(pixels[curr - width]) > 0) {
                        visited[curr - width] = true
                        queue[tail++] = curr - width
                    }
                    if (y < height - 1 && !visited[curr + width] && Color.alpha(pixels[curr + width]) > 0) {
                        visited[curr + width] = true
                        queue[tail++] = curr + width
                    }
                }

                if (currentBlobSize > maxBlobSize) {
                    maxBlobSize = currentBlobSize
                    maxBlobStart = i
                }
            }
        }

        val keep = BooleanArray(width * height)
        if (maxBlobStart != -1) {
            var head = 0
            var tail = 0
            queue[tail++] = maxBlobStart
            keep[maxBlobStart] = true

            while (head < tail) {
                val curr = queue[head++]
                val x = curr % width
                val y = curr / width

                if (x > 0 && !keep[curr - 1] && Color.alpha(pixels[curr - 1]) > 0) {
                    keep[curr - 1] = true
                    queue[tail++] = curr - 1
                }
                if (x < width - 1 && !keep[curr + 1] && Color.alpha(pixels[curr + 1]) > 0) {
                    keep[curr + 1] = true
                    queue[tail++] = curr + 1
                }
                if (y > 0 && !keep[curr - width] && Color.alpha(pixels[curr - width]) > 0) {
                    keep[curr - width] = true
                    queue[tail++] = curr - width
                }
                if (y < height - 1 && !keep[curr + width] && Color.alpha(pixels[curr + width]) > 0) {
                    keep[curr + width] = true
                    queue[tail++] = curr + width
                }
            }
        }

        for (i in pixels.indices) {
            if (!keep[i]) {
                pixels[i] = Color.TRANSPARENT
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
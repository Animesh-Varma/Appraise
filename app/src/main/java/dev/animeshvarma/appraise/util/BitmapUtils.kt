package dev.animeshvarma.appraise.util

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer

object BitmapUtils {
    /** Takes the original bitmap and the mask, darkens everything that is background */
    fun applyDarkBackgroundMask(original: Bitmap, maskBuffer: ByteBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        val resultBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(original.width * original.height)
        original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)

        maskBuffer.rewind()
        // DeepLab returns a float array or byte array depending on model.
        // For standard deeplabv3, 0 is background, >0 are various object classes.
        for (i in pixels.indices) {
            val maskValue = maskBuffer.get().toInt()
            if (maskValue == 0) {
                val color = pixels[i]
                val r = (Color.red(color) * 0.2).toInt()
                val g = (Color.green(color) * 0.2).toInt()
                val b = (Color.blue(color) * 0.2).toInt()
                pixels[i] = Color.rgb(r, g, b)
            }
        }

        resultBitmap.setPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        return resultBitmap
    }
}
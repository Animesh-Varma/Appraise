package dev.animeshvarma.appraise.model

import android.graphics.Bitmap

sealed interface ScannerState {
    object Initializing : ScannerState
    object CameraActive : ScannerState
    object Processing : ScannerState

    data class Segmented(
        val originalScaled: Bitmap,
        val darkMasked: Bitmap,
        val foreground: Bitmap,
        val framed: Bitmap? = null
    ) : ScannerState

    data class Error(val message: String) : ScannerState
}
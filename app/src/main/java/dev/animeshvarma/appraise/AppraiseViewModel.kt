package dev.animeshvarma.appraise

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.MlKitException
import dev.animeshvarma.appraise.model.ScannerState
import dev.animeshvarma.appraise.segmentation.SegmenterHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppraiseViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ScannerState>(ScannerState.CameraActive)
    val uiState: StateFlow<ScannerState> = _uiState.asStateFlow()

    private val segmenterHelper = SegmenterHelper()

    fun processImage(bitmap: Bitmap) {
        _uiState.value = ScannerState.Processing
        viewModelScope.launch {
            try {
                val maxDimension = 1024f
                val scale = minOf(maxDimension / bitmap.width, maxDimension / bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

                _uiState.value = segmenterHelper.segmentImage(scaledBitmap)

            } catch (e: Exception) {
                _uiState.value = ScannerState.Error(e.message ?: "Could not appraise object")
            }
        }
    }

    fun applyFrame() {
        val currentState = _uiState.value
        if (currentState is ScannerState.Segmented) {
            _uiState.value = ScannerState.Processing
            viewModelScope.launch {
                try {
                    val framedImg = segmenterHelper.frameImage(currentState.originalScaled, currentState.foreground)
                    _uiState.value = currentState.copy(framed = framedImg)
                } catch (e: Exception) {
                    _uiState.value = ScannerState.Error("Failed to frame: ${e.message}")
                }
            }
        }
    }

    fun resetCamera() {
        _uiState.value = ScannerState.CameraActive
    }
}
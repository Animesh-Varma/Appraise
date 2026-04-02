package dev.animeshvarma.appraise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.animeshvarma.appraise.ui.screens.ScannerScreen
import dev.animeshvarma.appraise.ui.theme.AppraiseTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppraiseViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            hasCameraPermission = true
        }
    }

    private var hasCameraPermission by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkCameraPermission()

        setContent {
            AppraiseTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                if (hasCameraPermission) {
                    ScannerScreen(
                        uiState = uiState,
                        onCapture = { previewView ->
                            val bitmap = previewView.bitmap
                            if (bitmap != null) {
                                viewModel.processImage(bitmap)
                            }
                        },
                        onFrame = { viewModel.applyFrame() },
                        onReset = { viewModel.resetCamera() }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(onClick = { checkCameraPermission() }) {
                            Text("Grant Camera Permission to Appraise!")
                        }
                    }
                }
            }
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
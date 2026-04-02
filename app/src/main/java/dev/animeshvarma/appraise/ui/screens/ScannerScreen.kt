package dev.animeshvarma.appraise.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.animeshvarma.appraise.model.ScannerState
import dev.animeshvarma.appraise.ui.components.CameraPreview
import dev.animeshvarma.appraise.ui.components.FunCaptureButton

@Composable
fun ScannerScreen(
    uiState: ScannerState,
    onCapture: (PreviewView) -> Unit,
    onFrame: () -> Unit,
    onReset: () -> Unit
) {
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (uiState) {
            is ScannerState.CameraActive -> {
                CameraPreview(
                    onPreviewViewCreated = { previewViewRef = it }
                )

                // Fun Bottom Bar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp)
                ) {
                    FunCaptureButton(onClick = {
                        previewViewRef?.let { onCapture(it) }
                    })
                }
            }

            is ScannerState.Processing -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Appraising Object...",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            is ScannerState.Segmented -> {
                val imageToShow = uiState.framed ?: uiState.darkMasked

                Image(
                    bitmap = imageToShow.asImageBitmap(),
                    contentDescription = "Appraised Object",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )

                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Discard",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = if (uiState.framed != null) "Entity Catalogued!" else "Entity Appraised",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // Bottom Action Bar (Only show if NOT framed yet)
                if (uiState.framed == null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 32.dp)
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = onFrame, // FIXED: Now it actually triggers the frame logic!
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text("Frame Entity")
                        }
                    }
                }
            }

            is ScannerState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error: ${uiState.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onReset) {
                        Text("Go Back")
                    }
                }
            }
            else -> {}
        }
    }
}
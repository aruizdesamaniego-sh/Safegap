package com.safegap.ui.screen

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.camera.view.PreviewView
import com.safegap.ui.components.CameraPreviewSurface

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HudScreen(
    onPreviewViewReady: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreviewSurface(
                modifier = Modifier.fillMaxSize(),
                onPreviewViewReady = onPreviewViewReady,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val text = if (cameraPermissionState.status.shouldShowRationale) {
                    "SafeGap necesita acceso a la camara para detectar objetos y medir distancias. Concede el permiso para continuar."
                } else {
                    "Permiso de camara requerido"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(32.dp),
                )
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                cameraPermissionState.launchPermissionRequest()
            }
        }
    }
}

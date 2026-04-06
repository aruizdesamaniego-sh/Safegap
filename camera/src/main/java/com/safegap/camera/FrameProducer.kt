package com.safegap.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class CameraFrame(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val rotationDegrees: Int,
)

@Singleton
class FrameProducer @Inject constructor() {

    /** Pre-allocated bitmap pool to avoid per-frame allocation. */
    val bitmapPool = BitmapPool(width = 640, height = 480, poolSize = 3)

    private val _frames = MutableSharedFlow<CameraFrame>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<CameraFrame> = _frames.asSharedFlow()

    suspend fun emit(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val frame = CameraFrame(
            bitmap = bitmap,
            timestampMs = imageProxy.imageInfo.timestamp / 1_000_000,
            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
        )
        _frames.emit(frame)
    }
}

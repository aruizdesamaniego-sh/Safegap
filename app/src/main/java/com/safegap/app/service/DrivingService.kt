package com.safegap.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.safegap.app.R
import com.safegap.camera.FrameProducer
import com.safegap.core.Constants
import com.safegap.detection.DetectionPipeline
import com.safegap.detection.ObjectDetector
import com.safegap.estimation.DistanceEstimator
import com.safegap.estimation.SpeedTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DrivingService : LifecycleService() {

    companion object {
        private const val TAG = "SafeGap.Service"
    }

    @Inject lateinit var objectDetector: ObjectDetector
    @Inject lateinit var detectionPipeline: DetectionPipeline
    @Inject lateinit var frameProducer: FrameProducer
    @Inject lateinit var distanceEstimator: DistanceEstimator
    @Inject lateinit var speedTracker: SpeedTracker

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "DrivingService started")

        startPipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        objectDetector.close()
        super.onDestroy()
        Log.i(TAG, "DrivingService stopped")
    }

    private fun startPipeline() {
        // Initialize detector (GPU delegate ~500ms)
        lifecycleScope.launch {
            objectDetector.initialize()
        }

        // Collect detection results and run estimation
        lifecycleScope.launch {
            detectionPipeline.process(frameProducer.frames).collect { result ->
                val enriched = result.trackedObjects.map { obj ->
                    val rawDist = distanceEstimator.estimate(
                        obj.detection,
                        result.imageHeightPx,
                    )
                    if (rawDist != null) {
                        val estimate = speedTracker.update(
                            obj.trackId,
                            rawDist,
                            obj.detection.timestampMs,
                        )
                        obj.copy(
                            distanceMeters = estimate.filteredDistanceM,
                            speedMps = estimate.speedMps,
                            ttcSeconds = estimate.ttcSeconds,
                        )
                    } else {
                        obj
                    }
                }

                // Prune speed tracker for disappeared objects
                speedTracker.pruneExcept(
                    enriched.map { it.trackId }.toSet(),
                )

                for (obj in enriched) {
                    if (obj.distanceMeters != null) {
                        Log.d(
                            TAG,
                            "[${obj.trackId}] ${obj.detection.className} " +
                                "dist=${"%.1f".format(obj.distanceMeters)}m " +
                                "speed=${"%.1f".format(obj.speedMps)}m/s " +
                                "ttc=${obj.ttcSeconds?.let { "%.1f".format(it) } ?: "-"}s",
                        )
                    }
                }
                // TODO: Phase 4 — Feed into AlertEngine → HudRepository
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

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
import com.safegap.alert.AlertEngine
import com.safegap.alert.AudioAlertPlayer
import com.safegap.app.R
import com.safegap.camera.FrameProducer
import com.safegap.core.Constants
import com.safegap.core.HudRepository
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
    @Inject lateinit var alertEngine: AlertEngine
    @Inject lateinit var audioAlertPlayer: AudioAlertPlayer
    @Inject lateinit var hudRepository: HudRepository

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

        audioAlertPlayer.initialize()
        startPipeline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        objectDetector.close()
        audioAlertPlayer.release()
        hudRepository.reset()
        super.onDestroy()
        Log.i(TAG, "DrivingService stopped")
    }

    private fun startPipeline() {
        lifecycleScope.launch {
            objectDetector.initialize()
        }

        lifecycleScope.launch {
            detectionPipeline.process(frameProducer.frames).collect { result ->
                // Estimation
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

                speedTracker.pruneExcept(enriched.map { it.trackId }.toSet())

                // Alert evaluation
                val alertResult = alertEngine.evaluate(enriched)

                // Audio
                audioAlertPlayer.play(alertResult.level)

                // Push to UI
                hudRepository.update(
                    alertLevel = alertResult.level,
                    trackedObjects = enriched,
                    closestThreat = alertResult.closestThreat,
                )

                // Debug logging
                if (alertResult.closestThreat != null) {
                    val t = alertResult.closestThreat!!
                    Log.d(
                        TAG,
                        "[${alertResult.level}] ${t.detection.className} " +
                            "dist=${"%.1f".format(t.distanceMeters)}m " +
                            "speed=${"%.1f".format(t.speedMps)}m/s " +
                            "ttc=${t.ttcSeconds?.let { "%.1f".format(it) } ?: "-"}s",
                    )
                }
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

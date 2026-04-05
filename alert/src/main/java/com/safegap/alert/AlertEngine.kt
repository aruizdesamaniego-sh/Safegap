package com.safegap.alert

import com.safegap.core.model.AlertLevel
import com.safegap.core.model.TrackedObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates tracked objects and determines the overall alert level.
 *
 * Thresholds (from PLAN.md):
 * - CRITICAL: TTC < 2.0s OR distance < 5m (for person: TTC < 4.0s)
 * - WARNING:  TTC < 4.0s OR distance < 15m
 * - SAFE:     everything else
 *
 * Debounce: level rises immediately, drops only after [DEBOUNCE_FRAMES]
 * consecutive frames at a lower level.
 */
@Singleton
class AlertEngine @Inject constructor() {

    companion object {
        private const val DEBOUNCE_FRAMES = 3

        private const val CRITICAL_TTC_S = 2.0f
        private const val CRITICAL_DISTANCE_M = 5.0f
        private const val CRITICAL_TTC_PERSON_S = 4.0f

        private const val WARNING_TTC_S = 4.0f
        private const val WARNING_DISTANCE_M = 15.0f
    }

    private var currentLevel = AlertLevel.SAFE
    private var lowerFrameCount = 0

    /**
     * Evaluate a frame of tracked objects and return the debounced alert level,
     * the most threatening object (if any), and its per-object level.
     */
    fun evaluate(objects: List<TrackedObject>): AlertResult {
        var worstLevel = AlertLevel.SAFE
        var closestThreat: TrackedObject? = null

        for (obj in objects) {
            val objLevel = classifyObject(obj)
            if (objLevel > worstLevel) {
                worstLevel = objLevel
                closestThreat = obj
            } else if (objLevel == worstLevel && closestThreat != null) {
                // Prefer the closer object at the same level
                val objDist = obj.distanceMeters ?: Float.MAX_VALUE
                val currentDist = closestThreat.distanceMeters ?: Float.MAX_VALUE
                if (objDist < currentDist) {
                    closestThreat = obj
                }
            }
        }

        currentLevel = debounce(worstLevel)

        return AlertResult(
            level = currentLevel,
            closestThreat = closestThreat,
        )
    }

    fun reset() {
        currentLevel = AlertLevel.SAFE
        lowerFrameCount = 0
    }

    private fun classifyObject(obj: TrackedObject): AlertLevel {
        val distance = obj.distanceMeters ?: return AlertLevel.SAFE
        val ttc = obj.ttcSeconds
        val isPerson = obj.detection.className == "person"

        // CRITICAL check
        val criticalTtc = if (isPerson) CRITICAL_TTC_PERSON_S else CRITICAL_TTC_S
        if ((ttc != null && ttc < criticalTtc) || distance < CRITICAL_DISTANCE_M) {
            return AlertLevel.CRITICAL
        }

        // WARNING check
        if ((ttc != null && ttc < WARNING_TTC_S) || distance < WARNING_DISTANCE_M) {
            return AlertLevel.WARNING
        }

        return AlertLevel.SAFE
    }

    /**
     * Level rises immediately; drops only after [DEBOUNCE_FRAMES] consecutive
     * frames at a lower level. Prevents flickering alerts.
     */
    private fun debounce(rawLevel: AlertLevel): AlertLevel {
        if (rawLevel >= currentLevel) {
            lowerFrameCount = 0
            return rawLevel
        }

        lowerFrameCount++
        return if (lowerFrameCount >= DEBOUNCE_FRAMES) {
            lowerFrameCount = 0
            rawLevel
        } else {
            currentLevel
        }
    }
}

data class AlertResult(
    val level: AlertLevel,
    val closestThreat: TrackedObject?,
)

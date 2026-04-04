package com.safegap.ui

import com.safegap.core.model.AlertLevel

data class HudState(
    val isCameraActive: Boolean = false,
    val alertLevel: AlertLevel = AlertLevel.SAFE,
    val closestObjectLabel: String? = null,
    val closestDistanceMeters: Float? = null,
    val fps: Float = 0f,
)

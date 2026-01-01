package com.homecontrol.sensors.ui.screens.cameras

import com.homecontrol.sensors.data.model.Camera

data class CamerasUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val cameras: List<Camera> = emptyList(),
    val primaryCameraIndex: Int = 0, // Index of camera shown in large view
    val selectedCamera: Camera? = null, // For full-screen dialog (if needed)
    val isRecordingAudio: Boolean = false,
    val refreshTrigger: Int = 0 // Increment to trigger snapshot refresh
)

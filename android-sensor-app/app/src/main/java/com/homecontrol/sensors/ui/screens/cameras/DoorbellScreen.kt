package com.homecontrol.sensors.ui.screens.cameras

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Doorbell
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.homecontrol.sensors.ui.theme.HomeControlColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Dedicated doorbell screen that shows only the front_door camera
 * with a large Push-to-Talk button for two-way audio communication.
 */
@Composable
fun DoorbellScreen(
    cameraName: String,
    onClose: () -> Unit,
    viewModel: CamerasViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Audio recording state
    var audioRecord: AudioRecord? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    var audioBuffer by remember { mutableStateOf<ByteArrayOutputStream?>(null) }

    // Microphone permission
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    // Load cameras to get the stream URL
    LaunchedEffect(Unit) {
        viewModel.loadCameras()
    }

    // Get stream URL for the specified camera
    val streamUrl = remember(cameraName) { viewModel.getStreamUrl(cameraName) }

    // Start recording function
    fun startRecording() {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val sampleRate = 8000
                    val channelConfig = AudioFormat.CHANNEL_IN_MONO
                    val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
                    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)

                    @Suppress("MissingPermission")
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioEncoding,
                        bufferSize * 2
                    ).also { record ->
                        if (record.state == AudioRecord.STATE_INITIALIZED) {
                            audioBuffer = ByteArrayOutputStream()
                            record.startRecording()
                            isRecording = true
                            viewModel.startRecording()

                            val buffer = ByteArray(bufferSize)
                            while (isRecording && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                val read = record.read(buffer, 0, bufferSize)
                                if (read > 0) {
                                    audioBuffer?.write(buffer, 0, read)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Recording failed
                    isRecording = false
                }
            }
        }
    }

    // Stop recording and send audio
    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioBuffer?.toByteArray()?.let { pcmData ->
            if (pcmData.isNotEmpty()) {
                viewModel.stopRecordingAndSend(cameraName, pcmData)
            }
        }
        audioBuffer = null
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            audioRecord?.release()
            audioRecord = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen camera stream
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp), // Leave space for PTT button
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Live stream using WebView for MJPEG
                key(cameraName) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewClient = WebViewClient()
                                settings.apply {
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = false
                                    displayZoomControls = false
                                }
                                setBackgroundColor(android.graphics.Color.BLACK)
                                loadUrl(streamUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Doorbell indicator overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE65100).copy(alpha = 0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Doorbell,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Doorbell",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Close button
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Close", color = Color.White)
                }

                // LIVE indicator
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Red.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "● LIVE",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // PTT (Push-to-Talk) button at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(100.dp),
            color = HomeControlColors.cardBackgroundSolid()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PTT Button
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    startRecording()
                                    tryAwaitRelease()
                                    stopRecording()
                                }
                            )
                        },
                    shape = CircleShape,
                    color = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                    border = BorderStroke(
                        width = 3.dp,
                        color = if (isRecording) Color.Red.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    shadowElevation = 8.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Push to talk",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (isRecording) "Recording..." else "Hold to Talk",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRecording) "Release to send" else "Press and hold the button",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

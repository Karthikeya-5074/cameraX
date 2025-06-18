// MainActivity.kt

package com.shabeer.camerax

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.FileOutputOptions
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraApp(cameraExecutor)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

enum class Mode {
    PHOTO, VIDEO
}

fun createFile(context: Context, extension: String): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "CAMERA_${timeStamp}.$extension"
    val outputDir = context.cacheDir
    return File(outputDir, fileName)
}

@Composable
fun CameraApp(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recording: Recording? by remember { mutableStateOf(null) }
    var mode by remember { mutableStateOf(Mode.PHOTO) }
    var selectedFlash by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var selectedIso by remember { mutableStateOf(400) }
    var selectedExposure by remember { mutableStateOf(0) }
    var selectedWhiteBalance by remember { mutableStateOf(CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var isManualISOEnabled by remember { mutableStateOf(false) }
    var isManualExposureEnabled by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera(
                context, lifecycleOwner, previewView, cameraProviderFuture,
                selectedFlash,
                if (isManualISOEnabled) selectedIso else 0,
                if (isManualExposureEnabled) selectedExposure else 0,
                selectedWhiteBalance, isFrontCamera
            ) { capture, video, control ->
                imageCapture = capture
                videoCapture = video
                cameraControl = control
            }
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(selectedFlash, selectedIso, selectedExposure, selectedWhiteBalance, isFrontCamera, isManualISOEnabled, isManualExposureEnabled) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera(
                context, lifecycleOwner, previewView, cameraProviderFuture,
                selectedFlash,
                if (isManualISOEnabled) selectedIso else 0,
                if (isManualExposureEnabled) selectedExposure else 0,
                selectedWhiteBalance, isFrontCamera
            ) { capture, video, control ->
                imageCapture = capture
                videoCapture = video
                cameraControl = control
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Smart Camera", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Color.DarkGray, shape = RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Photo" to Mode.PHOTO, "Video" to Mode.VIDEO).forEach { (label, value) ->
                Button(onClick = { mode = value },
                    colors = ButtonDefaults.buttonColors(if (mode == value) Color.Cyan else Color.Gray)) {
                    Text(label)
                }
            }
            Button(onClick = { isFrontCamera = !isFrontCamera }) { Text("Switch Camera") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(
                "Flash ON" to ImageCapture.FLASH_MODE_ON,
                "Flash OFF" to ImageCapture.FLASH_MODE_OFF
            ).forEach { (label, value) ->
                Button(onClick = { selectedFlash = value },
                    colors = ButtonDefaults.buttonColors(if (selectedFlash == value) Color.Yellow else Color.Gray)) {
                    Text(label)
                }
            }
            Button(onClick = {
                isTorchOn = !isTorchOn
                cameraControl?.enableTorch(isTorchOn)
            }, colors = ButtonDefaults.buttonColors(if (isTorchOn) Color.Red else Color.Gray)) {
                Text(if (isTorchOn) "Torch ON" else "Torch OFF")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { isManualISOEnabled = !isManualISOEnabled },
                colors = ButtonDefaults.buttonColors(if (isManualISOEnabled) Color.Magenta else Color.Gray)
            ) {
                Text(if (isManualISOEnabled) "Manual ISO ON" else "Manual ISO OFF")
            }
            Button(
                onClick = { isManualExposureEnabled = !isManualExposureEnabled },
                colors = ButtonDefaults.buttonColors(if (isManualExposureEnabled) Color.Magenta else Color.Gray)
            ) {
                Text(if (isManualExposureEnabled) "Manual EXP ON" else "Manual EXP OFF")
            }
        }

        if (isManualISOEnabled) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("ISO: $selectedIso", color = Color.White)
                Slider(value = selectedIso.toFloat(), onValueChange = { selectedIso = it.toInt() }, valueRange = 100f..1600f, steps = 5)
            }
        }

        if (isManualExposureEnabled) {
            Text("Exposure: $selectedExposure", color = Color.White)
            Slider(value = selectedExposure.toFloat(), onValueChange = { selectedExposure = it.toInt() }, valueRange = -3f..3f, steps = 6)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("White Balance", color = Color.White)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    "Daylight" to CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                    "Cloudy" to CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                ).forEach { (label, mode) ->
                    Button(onClick = { selectedWhiteBalance = mode }) {
                        Text(label)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    "Fluorescent" to CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
                    "Incandescent" to CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                ).forEach { (label, mode) ->
                    Button(onClick = { selectedWhiteBalance = mode }) {
                        Text(label)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (mode == Mode.PHOTO) {
                    imageCapture?.let { capture ->
                        val resolver = context.contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}")
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/SmartCameraX")
                        }
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(
                            resolver,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        ).build()

                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    val savedUri = outputFileResults.savedUri
                                    Toast.makeText(context, "Photo saved: ${savedUri?.path}", Toast.LENGTH_LONG).show()
                                    Log.d("CameraX", "Photo saved to: ${savedUri?.path}")
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    Log.e("CameraX", "Photo capture failed", exception)
                                }
                            }
                        )
                    }
                } else {
                    videoCapture?.let { capture ->
                        if (!isRecording) {
                            val videoFile = createFile(context, "mp4")
                            val mediaStoreOutput = FileOutputOptions.Builder(videoFile).build()
                            recording = capture.output.prepareRecording(context, mediaStoreOutput)
                                .start(ContextCompat.getMainExecutor(context)) {
                                    when (it) {
                                        is VideoRecordEvent.Start -> {
                                            isRecording = true
                                            Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show()
                                        }
                                        is VideoRecordEvent.Finalize -> {
                                            isRecording = false
                                            Toast.makeText(context, "Recording saved", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        } else {
                            recording?.stop()
                            recording = null
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                if (mode == Mode.PHOTO || !isRecording) Color.Green else Color.Red
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = if (mode == Mode.PHOTO) "Capture Photo" else if (isRecording) "Stop Recording" else "Start Recording",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
fun startCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    flashMode: Int,
    isoValue: Int,
    exposureValue: Int,
    whiteBalanceMode: Int,
    useFrontCamera: Boolean,
    onUseCasesReady: (ImageCapture, VideoCapture<Recorder>, CameraControl) -> Unit
) {
    val cameraProvider = cameraProviderFuture.get()
    val previewBuilder = Preview.Builder()
    val previewInterop = Camera2Interop.Extender(previewBuilder)
    if (isoValue > 0) {
        previewInterop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        previewInterop.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
    } else {
        previewInterop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
    }
    previewInterop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureValue)
    previewInterop.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, whiteBalanceMode)
    val preview = previewBuilder.build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }

    val builder = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setFlashMode(flashMode)
    val interop = Camera2Interop.Extender(builder)
    if (isoValue > 0) {
        interop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        interop.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
    } else {
        interop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
    }
    interop.setCaptureRequestOption(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureValue)
    interop.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, whiteBalanceMode)
    val imageCapture = builder.build()

    val recorder = Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.HD))
        .build()
    val videoCapture = VideoCapture.withOutput(recorder)

    val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

    try {
        cameraProvider.unbindAll()
        val camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            videoCapture
        )
        onUseCasesReady(imageCapture, videoCapture, camera.cameraControl)
    } catch (e: Exception) {
        Log.e("CameraX", "Use case binding failed", e)
    }
}

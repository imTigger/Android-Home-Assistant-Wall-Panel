package com.tigerworkshop.homepanel.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner overlay. Returns the decoded text via [onResult].
 * Used to read the Home Assistant long-lived token QR code.
 */
@Composable
fun QrScannerScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var denied by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        denied = !granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraScanner(onResult = onResult)
            // Framing reticle + hint
            Column(
                Modifier.fillMaxSize().padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(260.dp)
                        .border(3.dp, Accent, RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Point at the Home Assistant token QR code",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(40.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (denied) "Camera permission was denied. Enable it in system settings to scan."
                    else "Requesting camera permission…",
                    color = Color.White,
                    fontSize = 18.sp,
                )
                if (denied) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF1A1205)),
                    ) { Text("Try again", fontWeight = FontWeight.Bold) }
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun CameraScanner(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val handled = remember { AtomicBoolean(false) }
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView-backed preview; SurfaceView (PERFORMANCE) often renders
            // black inside a Compose AndroidView on older devices.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val handler = Handler(Looper.getMainLooper())
        var running = true
        var mirror = false
        var busy = false

        // Scan by snapshotting the preview every ~350 ms instead of running a second
        // ImageAnalysis stream. A single camera stream keeps legacy camera HALs
        // stable (older devices error/reopen when Preview + ImageAnalysis run at once).
        val poll = object : Runnable {
            override fun run() {
                if (!running) return
                val bmp = previewView.bitmap
                if (bmp != null && !busy && !handled.get()) {
                    busy = true
                    val src = if (mirror) mirrorBitmap(bmp) else bmp
                    scanner.process(InputImage.fromBitmap(src, 0))
                        .addOnSuccessListener { codes ->
                            codes.firstOrNull()?.rawValue?.let { v ->
                                if (handled.compareAndSet(false, true)) onResult(v)
                            }
                        }
                        .addOnCompleteListener { busy = false }
                }
                handler.postDelayed(this, 350)
            }
        }

        cameraProviderFuture.addListener({
            val p = cameraProviderFuture.get()
            provider = p
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            // Prefer the back camera, but fall back to the front (e.g. tablets with
            // no rear camera) or any available camera.
            val selector = when {
                p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }
            // The front camera preview is mirrored for display; un-mirror snapshots
            // before decoding (a mirrored QR code won't decode).
            mirror = selector == CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview)
                handler.postDelayed(poll, 600)
            } catch (e: Exception) {
                android.util.Log.e("QrScanner", "camera bind failed", e)
            }
        }, mainExecutor)

        onDispose {
            running = false
            handler.removeCallbacks(poll)
            provider?.unbindAll()
            scanner.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun mirrorBitmap(src: Bitmap): Bitmap {
    val m = Matrix().apply { preScale(-1f, 1f) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
}

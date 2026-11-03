package com.xcluice.chromecatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class CaptureService : Service() {

    companion object {
        const val ACTION_CHROME_OPENED = "chrome_opened"
        const val CHANNEL_ID = "chromecatch_monitor"
        const val NOTIF_ID = 1
        const val SCREENSHOT_DELAY_MS = 3 * 60 * 1000L
        private const val TAG = "ChromeCatch"

        fun ensureRunning(context: Context) {
            // Called once after projection consent is granted, just to warm things up.
        }
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var selfieBitmap: Bitmap? = null
    private var selfieTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        if (intent?.action == ACTION_CHROME_OPENED) {
            runCatching { takeSelfie() }
                .onFailure { Log.e(TAG, "selfie failed", it) }
            mainHandler.postDelayed({
                runCatching { takeScreenshotThenCollage() }
                    .onFailure { Log.e(TAG, "screenshot failed", it) }
            }, SCREENSHOT_DELAY_MS)
        }
        return START_NOT_STICKY
    }

    // ---------- Front camera selfie, no preview UI ----------

    private fun takeSelfie() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    ProcessLifecycleHolder.owner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "bind failed", e)
                return@addListener
            }

            imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    selfieBitmap = imageProxyToBitmap(image)
                    selfieTime = System.currentTimeMillis()
                    image.close()
                    provider.unbindAll()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "capture error", exception)
                    provider.unbindAll()
                }
            })
        }, ContextCompat_mainExecutor())
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun ContextCompat_mainExecutor() = androidx.core.content.ContextCompat.getMainExecutor(this)

    // ---------- Screenshot via MediaProjection (uses the one-time cached token) ----------

    private fun takeScreenshotThenCollage() {
        val resultCode = ProjectionHolder.resultCode
        val data = ProjectionHolder.data
        if (resultCode == null || data == null) {
            Log.w(TAG, "no projection consent cached; open the app and grant step 3 again")
            return
        }

        val mgr = getSystemService(MediaProjectionManager::class.java)
        val projection = mgr.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val rowPadding = rowStride - pixelStride * width

            val bmp = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)
            image.close()

            val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
            val screenshotTime = System.currentTimeMillis()

            CollageMaker.makeAndSave(
                applicationContext,
                selfieBitmap,
                selfieTime,
                cropped,
                screenshotTime
            )

            virtualDisplay?.release()
            reader.close()
            projection.stop()
            stopSelf()
        }, mainHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "chromecatch-shot",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, mainHandler
        )
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN
            )
            mgr.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(false)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

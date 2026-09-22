package com.example.automation.core.executor.media

import com.example.automation.core.executor.*
import com.example.automation.core.model.*
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

class TakeScreenshotExecutor(
    private val mediaProjectionManager: MediaProjectionManagerWrapper,
    private val saveDirectory: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AutomationApp/Screenshots")
) : ActionExecutor {
    override val supportedType = ActionType.TAKE_SCREENSHOT
    override val executionDispatcher = Dispatchers.IO

    init {
        saveDirectory.mkdirs()
    }

    override suspend fun execute(
        action: Action,
        variables: MutableMap<String, Any>,
        accessibility: AccessibilityController
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val projection = mediaProjectionManager.getActiveProjection()
            ?: return@withContext ExecutionResult.Failure("No active MediaProjection. Start screen capture first.")

        val format = when (action.getString("format")?.uppercase()) {
            "JPEG", "JPG" -> Bitmap.CompressFormat.JPEG
            "WEBP" -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.PNG
        }
        val quality = action.getInt("quality") ?: 90
        val fileName = action.getString("fileName") ?: generateFileName(format)
        val includeStatusBar = action.getBoolean("includeStatusBar") ?: false
        val includeNavBar = action.getBoolean("includeNavBar") ?: false

        val bitmap = projection.captureScreen(includeStatusBar, includeNavBar)
            ?: return@withContext ExecutionResult.Failure("Failed to capture screen")

        val file = File(saveDirectory, fileName)
        try {
            FileOutputStream(file).use { stream ->
                bitmap.compress(format, quality, stream)
            }
            
            // Scan for gallery visibility
            MediaScannerConnection.scanFile(
                accessibility.context,
                arrayOf(file.absolutePath),
                null
            ) { path, uri -> }

            val uri = Uri.fromFile(file)
            variables["last_screenshot"] = mapOf(
                "path" to file.absolutePath,
                "uri" to uri.toString(),
                "width" to bitmap.width,
                "height" to bitmap.height,
                "size" to file.length(),
                "timestamp" to Instant.now().toString()
            )

            ExecutionResult.Success(mapOf(
                "path" to file.absolutePath,
                "uri" to uri.toString(),
                "width" to bitmap.width,
                "height" to bitmap.height
            ))
        } catch (e: Exception) {
            ExecutionResult.Failure("Failed to save screenshot: ${e.message}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun generateFileName(format: Bitmap.CompressFormat): String {
        val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-')
        val ext = when (format) {
            Bitmap.CompressFormat.PNG -> "png"
            Bitmap.CompressFormat.JPEG -> "jpg"
            Bitmap.CompressFormat.WEBP -> "webp"
            else -> "png"
        }
        return "screenshot_$timestamp.$ext"
    }

    override fun estimateDuration(action: Action): Long = 1000
}

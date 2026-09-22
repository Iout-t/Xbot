package com.example.automation.core.executor.media

import com.example.automation.core.executor.*
import com.example.automation.core.model.*
import android.graphics.*
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.media3.transformer.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

class PhotoEditExecutor(
    private val saveDirectory: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AutomationApp/Edited")
) : ActionExecutor {
    override val supportedType = ActionType.EditPhoto(emptyList())
    override val executionDispatcher = Dispatchers.IO

    init {
        saveDirectory.mkdirs()
    }

    override suspend fun execute(
        action: Action,
        variables: MutableMap<String, Any>,
        accessibility: AccessibilityController
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val inputSource = action.getString("input") 
            ?: variables["last_screenshot"]?.let { (it as Map<*, *>)["path"] as String? }
            ?: return ExecutionResult.Failure("No input image specified")

        val inputFile = File(inputSource)
        if (!inputFile.exists()) {
            return ExecutionResult.Failure("Input file not found: $inputSource")
        }

        val operations = action.parameters["operations"] as? List<*>
            ?: return@withContext ExecutionResult.Failure("No edit operations specified")

        var bitmap = loadBitmap(inputFile)
            ?: return ExecutionResult.Failure("Failed to load input image")

        try {
            for (opMap in operations) {
                val op = opMap as? Map<String, Any>
                    ?: continue
                val type = op["type"] as String? ?: continue
                
                bitmap = when (type) {
                    "crop" -> applyCrop(bitmap, op)
                    "rotate" -> applyRotate(bitmap, op)
                    "resize" -> applyResize(bitmap, op)
                    "filter" -> applyFilter(bitmap, op)
                    "adjust" -> applyAdjustments(bitmap, op)
                    "overlay" -> applyOverlay(bitmap, op, accessibility.context)
                    "text" -> applyText(bitmap, op)
                    "blur" -> applyBlur(bitmap, op)
                    else -> bitmap
                }
            }

            val outputFile = File(saveDirectory, "edited_${DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-')}.jpg")
            FileOutputStream(outputFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }

            MediaScannerConnection.scanFile(accessibility.context, arrayOf(outputFile.absolutePath), null) { _, _ -> }

            val result = mapOf(
                "path" to outputFile.absolutePath,
                "width" to bitmap.width,
                "height" to bitmap.height
            )
            variables["last_edited_photo"] = result
            ExecutionResult.Success(result)
        } catch (e: Exception) {
            ExecutionResult.Failure("Photo editing failed: ${e.message}")
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadBitmap(file: File): Bitmap? {
        val options = BitmapFactory.Options().apply { inMutable = true }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun applyCrop(bitmap: Bitmap, params: Map<String, Any>): Bitmap {
        val x = (params["x"] as Number?)?.intValue() ?: 0
        val y = (params["y"] as Number?)?.intValue() ?: 0
        val width = (params["width"] as Number?)?.intValue() ?: bitmap.width - x
        val height = (params["height"] as Number?)?.intValue() ?: bitmap.height - y
        return Bitmap.createBitmap(bitmap, x, y, width.coerceAtMost(bitmap.width - x), height.coerceAtMost(bitmap.height - y))
    }

    private fun applyRotate(bitmap: Bitmap, params: Map<String, Any>): Bitmap {
        val degrees = (params["degrees"] as Number?)?.floatValue() ?: 90f
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyResize(bitmap: Bitmap, params: Map<String, Any>): Bitmap {
        val width = (params["width"] as Number?)?.intValue() ?: bitmap.width
        val height = (params["height"] as Number?)?.intValue() ?: bitmap.height
        val maintainAspect = (params["maintainAspect"] as Boolean?) ?: true
        
        var targetWidth = width
        var targetHeight = height
        if (maintainAspect) {
            val aspectRatio = bitmap.width.toFloat() / bitmap.height
            if (width.toFloat() / height > aspectRatio) {
                targetWidth = (height * aspectRatio).toInt()
            } else {
                targetHeight = (width / aspectRatio).toInt()
            }
        }
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun applyFilter(bitmap: Bitmap, params: Map<String, Any>): Bitmap {
        val filterName = params["filter"] as String? ?: "none"
        val intensity = (params["intensity"] as Number?)?.floatValue() ?: 1.0f
        
        val colorMatrix = when (filterName.lowercase()) {
            "grayscale" -> ColorMatrix().apply { setSaturation(0f) }
            "sepia" -> ColorMatrix().apply {
                setScale(1.0f, 1.0f, 1.0f, 1.0f)
                // Sepia matrix approximation
            }
            "invert" -> ColorMatrix().apply { 
                setScale(-1f, -1f, -1f, 1f)
                postTranslate(255f, 255f, 255f, 0f)
            }
            "vintage" -> createVintageMatrix()
            else -> return bitmap
        }
        
        // Blend with original based on intensity
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Canvas(result).apply {
            drawBitmap(bitmap, 0f, 0f, null)
            // For intensity blending, we'd need more complex logic
            // This is simplified - full implementation would blend two bitmaps
        }
        return result
    }

    private fun createVintageMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        matrix.setScale(1.1f, 1.1f, 0.9f, 1.0f)
        return matrix
    }

    private fun applyAdjustments(bitmap: Bitmap, params: Map<String, Any>): Bitmap {
        val brightness = (params["brightness"] as Number?)?.floatValue() ?: 0f // -1 to 1
        val contrast = (params["contrast"] as Number?)?.floatValue() ?: 1f // 0 to 3
        val saturation = (params["saturation"] as Number?)?.floatValue() ?: 1f // 0 to 3
        val temperature = (params["temperature"] as Number?)?.floatValue() ?: 0f // -1 to 1

        val cm = ColorMatrix()
        
        // Brightness
        if (brightness != 0f) {
            val bm = ColorMatrix()
            val offset = brightness * 255
            bm.setTranslate(offset, offset, offset, 0f)
            cm.postConcat(bm)
        }
        
        // Contrast
        if (contrast != 1f) {
            val cm2 = ColorMatrix()
            cm2.setScale(contrast, contrast, contrast, 1f)
            cm.postConcat(cm2)
        }
        
        // Saturation
        if (saturation != 1f) {
            val cm3 = ColorMatrix()
            cm3.setSaturation(saturation)
            cm.postConcat(cm3)
        }

        // Temperature (simplified)
        if (temperature != 0f) {
            val cm4 = ColorMatrix()
            val r = 1f + temperature * 0.5f
            val b = 1f - temperature * 0.5f
            cm4.setScale(r, 1f, b, 1f)
            cm.postConcat(cm4)
        }

        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    private fun applyOverlay(bitmap: Bitmap, params: Map<String, Any>, context: android.content.Context): Bitmap {
        val overlayPath = params["overlayPath"] as String? ?: return bitmap
        val x = (params["x"] as Number?)?.intValue() ?: 0
        val y = (params["y"] as Number?)?.intValue() ?: 0
        val scale = (params["scale"] as Number?)?.floatValue() ?: 1f
        val alpha = (params["alpha"] as Number?)?.floatValue() ?: 1f

        val overlay = BitmapFactory.decodeFile(overlayPath) ?: return bitmap
        val scaledOverlay = if (scale != 1f) {
            Bitmap.createScaledBitmap(overlay, (overlay.width * scale).toInt(), (overlay.height * scale).toInt(), true)
        } else overlay

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Canvas(result).apply {
            drawBitmap(bitmap, 0f, 0f, null)
            val paint = Paint().apply { alpha = (alpha * 255).toInt() }
            drawBitmap(scaledOverlay, x.toFloat(), y.toFloat(), paint)
        }
        return result
    }

    private fun applyText(bitmap: Bitmap, params: Map<String, Any>): Bitmap {
        val text = params["text"] as String? ?: return bitmap
        val x = (params["x"] as Number?)?.floatValue() ?: 0f
        val y = (params["y"] as Number?)?.floatValue() ?: 0f
        val size = (params["size"] as Number?)?.floatValue() ?: 48f
        val color = (params["color"] as Int?) ?: Color.WHITE
        val fontPath = params["fontPath"] as String?

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = color
            typeface = fontPath?.let { Typeface.createFromFile(it) } ?: Typeface.DEFAULT_BOLD
        }

        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        Canvas(result).apply {
            drawBitmap(bitmap, 0f, 0f, null)
            drawText(text, x, y, paint)
        }
        return result
    }

    private fun applyBlur(bitmap: Bitmap, params: Map<String, Any>): Bitmap {
        val radius = (params["radius"] as Number?)?.floatValue() ?: 10f
        val downsample = (params["downsample"] as Number?)?.intValue() ?: 4
        
        // Fast blur via downsample + upsample
        val small = Bitmap.createScaledBitmap(
            bitmap, 
            bitmap.width / downsample, 
            bitmap.height / downsample, 
            true
        )
        val blurred = small.copy(small.config, true)
        val canvas = Canvas(blurred)
        val paint = Paint().apply { 
            isAntiAlias = true
            // Note: Real blur would use RenderScript or GPUImage
            // This is a placeholder - use RenderScript Intrinsics or Media3 for production
        }
        canvas.drawBitmap(small, 0f, 0f, paint)
        return Bitmap.createScaledBitmap(blurred, bitmap.width, bitmap.height, true)
    }
}

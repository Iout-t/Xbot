package com.example.automation.core.executor.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Environment
import com.example.automation.core.executor.AccessibilityController
import com.example.automation.core.executor.ActionExecutor
import com.example.automation.core.model.Action
import com.example.automation.core.model.ActionType
import com.example.automation.core.model.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PhotoEditExecutor(
    private val saveDirectory: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
        "AutomationApp/Edited"
    )
) : ActionExecutor {
    override val supportedType = ActionType.EditPhoto(emptyList())
    override val executionDispatcher = Dispatchers.IO

    override suspend fun execute(
        action: Action,
        variables: MutableMap<String, Any>,
        accessibility: AccessibilityController
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val source = action.getString("inputPath")?.let(::decodeFile)
            ?: return@withContext ExecutionResult.Failure("Missing or unreadable inputPath")
        saveDirectory.mkdirs()
        val brightness = (action.parameters["brightness"] as? Number)?.toFloat() ?: 0f
        val contrast = (action.parameters["contrast"] as? Number)?.toFloat() ?: 1f
        val edited = transform(source, brightness, contrast)
        val name = action.getString("outputFile") ?: "edited_${System.currentTimeMillis()}.png"
        val output = File(saveDirectory, name)
        FileOutputStream(output).use { edited.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()
        if (edited !== source) edited.recycle()
        ExecutionResult.Success(mapOf("path" to output.absolutePath))
    }

    private fun decodeFile(path: String): Bitmap? = android.graphics.BitmapFactory.decodeFile(path)

    private fun transform(source: Bitmap, brightness: Float, contrast: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val matrix = ColorMatrix().apply {
            val translate = brightness * 255f
            set(floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(source, null, RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()), paint)
        return output
    }
}

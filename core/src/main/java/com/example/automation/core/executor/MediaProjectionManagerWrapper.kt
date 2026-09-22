package com.example.automation.core.executor

import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection

interface MediaProjectionManagerWrapper {
    fun getActiveProjection(): MediaProjection?
    fun requestPermission(): Intent?
    fun setResult(resultCode: Int, data: Intent?)
    suspend fun captureScreen(includeStatusBar: Boolean = false, includeNavBar: Boolean = false): Bitmap?
}

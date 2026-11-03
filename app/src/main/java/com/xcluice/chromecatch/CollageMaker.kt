package com.xcluice.chromecatch

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stacks the selfie and screenshot top-to-bottom at their own native sizes
 * (neither is cropped or scaled to match the other), each labeled with its
 * own capture timestamp, and saves the result to the Pictures/ChromeCatch
 * folder via MediaStore.
 */
object CollageMaker {

    private const val PADDING = 24
    private const val LABEL_HEIGHT = 56
    private const val GAP = 16

    fun makeAndSave(
        context: Context,
        selfie: Bitmap?,
        selfieTime: Long,
        screenshot: Bitmap,
        screenshotTime: Long
    ) {
        val placeholder = selfie ?: Bitmap.createBitmap(screenshot.width / 3, screenshot.height / 3, Bitmap.Config.ARGB_8888)
        val collage = buildCollage(placeholder, selfieTime, screenshot, screenshotTime)
        save(context, collage)
    }

    private fun buildCollage(top: Bitmap, topTime: Long, bottom: Bitmap, bottomTime: Long): Bitmap {
        val width = maxOf(top.width, bottom.width) + PADDING * 2
        val height = PADDING + LABEL_HEIGHT + top.height +
            GAP + LABEL_HEIGHT + bottom.height + PADDING

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.parseColor("#121318"))

        val labelPaint = Paint().apply {
            color = Color.parseColor("#A8B0C0")
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        var y = PADDING
        canvas.drawText("Front camera — ${formatTime(topTime)}", PADDING.toFloat(), (y + 38).toFloat(), labelPaint)
        y += LABEL_HEIGHT
        canvas.drawBitmap(top, PADDING.toFloat(), y.toFloat(), null)
        y += top.height + GAP

        canvas.drawText("Screen — ${formatTime(bottomTime)}", PADDING.toFloat(), (y + 38).toFloat(), labelPaint)
        y += LABEL_HEIGHT
        canvas.drawBitmap(bottom, PADDING.toFloat(), y.toFloat(), null)

        return result
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()).format(Date(ms))

    private fun save(context: Context, bitmap: Bitmap) {
        val name = "chromecatch_${System.currentTimeMillis()}.png"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ChromeCatch")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }
}

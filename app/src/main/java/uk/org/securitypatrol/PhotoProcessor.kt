package uk.org.securitypatrol

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

object PhotoProcessor {
    private const val OUTPUT_LONG_EDGE = 1920

    fun process(context: Context, photo: PatrolPhoto, company: String): Pair<String, String> {
        val input = photo.originalPath?.let { File(context.filesDir, it) }
            ?: error("Missing original path")
        require(input.isFile) { "Original JPEG missing" }
        val bitmap = readRotatedDownsampled(input)
        val baseRel = "patrols/" + photo.shiftId + "/base/" + photo.id + ".jpg"
        val smallRel = "patrols/" + photo.shiftId + "/small/" + photo.id + ".jpg"
        val base = File(context.filesDir, baseRel)
        val small = File(context.filesDir, smallRel)
        base.parentFile?.mkdirs()
        small.parentFile?.mkdirs()
        FileOutputStream(base).use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)) }
        stampAndSave(bitmap, photo, small, company)
        bitmap.recycle()
        require(isValidJpeg(small) && isValidJpeg(base)) { "Compressed photo validation failed" }
        return baseRel to smallRel
    }

    fun restamp(context: Context, photo: PatrolPhoto, newPlace: String, company: String) {
        val base = photo.basePath?.let { File(context.filesDir, it) }
            ?: error("Original compressed base missing")
        val small = photo.smallPath?.let { File(context.filesDir, it) }
            ?: error("Share copy missing")
        require(isValidJpeg(base)) { "Base image is not readable" }
        val bitmap = BitmapFactory.decodeFile(base.absolutePath) ?: error("Cannot decode base JPEG")
        val revised = photo.copy(place = newPlace)
        val replacement = File(small.parentFile, small.name + ".tmp")
        stampAndSave(bitmap, revised, replacement, company)
        bitmap.recycle()
        require(isValidJpeg(replacement)) { "Revised JPEG is invalid" }
        // Replacing the small file never removes the preserved un-stamped base.
        if (!replacement.renameTo(small)) {
            replacement.delete()
            error("Could not replace compressed image")
        }
    }

    fun isValidJpeg(file: File): Boolean {
        if (!file.isFile || file.length() < 1024) return false
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opt)
        return opt.outWidth > 0 && opt.outHeight > 0
    }

    private fun readRotatedDownsampled(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid JPEG" }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > OUTPUT_LONG_EDGE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val raw = BitmapFactory.decodeFile(file.absolutePath, options) ?: error("JPEG decode failed")
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180f
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> 0f
        }
        if (degrees == 0f) return raw
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    private fun stampAndSave(source: Bitmap, photo: PatrolPhoto, destination: File, company: String) {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val textSize = max(19f, output.width / 39f)
        val line = textSize * 1.33f
        val padding = textSize * 0.65f
        val areaHeight = line * 4.0f + padding * 2.0f
        val areaTop = output.height - areaHeight
        val bg = Paint().apply { color = Color.argb(202, 6, 15, 25) }
        canvas.drawRect(0f, areaTop, output.width.toFloat(), output.height.toFloat(), bg)
        val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            isFakeBoldText = true
            setShadowLayer(1.5f, 1f, 1f, Color.BLACK)
        }
        val formatter = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("Europe/London")
        }
        val gps = if (photo.lat != null && photo.lon != null) {
            String.format(Locale.UK, "GPS: %.6f, %.6f  (±%.0f m)", photo.lat, photo.lon, photo.accuracyMetres ?: 0f)
        } else "GPS: unavailable"
        // Keep company and editable site separate, so neither is lost or
        // confused with GPS coordinates. Use the shift's saved company name.
        val lines = listOf(
            "Company: " + company.trim().ifBlank { "Not entered" },
            "Site: " + photo.place.ifBlank { "Not entered" },
            formatter.format(Date(photo.timeMs)),
            gps
        )
        val left = padding
        val maxWidth = output.width - 2f * padding
        lines.forEachIndexed { index, value ->
            canvas.drawText(fit(value, pen, maxWidth), left, areaTop + padding + line * (index + 0.83f), pen)
        }
        FileOutputStream(destination).use { check(output.compress(Bitmap.CompressFormat.JPEG, 79, it)) }
        output.recycle()
    }

    private fun fit(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var prefix = text
        while (prefix.isNotEmpty() && paint.measureText(prefix + "…") > width) {
            prefix = prefix.dropLast(1)
        }
        return prefix + "…"
    }
}

package uk.org.securitypatrol

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Explicit, user-initiated export to an Android document destination.
 *
 * A ZIP is not encrypted. Do not upload it automatically: the officer decides
 * where to keep it. The ZIP is a human-readable archive, NOT yet an app import.
 */
object ShiftArchive {
    data class Result(val photos: Int, val originals: Int)

    fun write(context: Context, shift: PatrolShift, destination: Uri, includeOriginals: Boolean): Result {
        // Check integrity BEFORE opening a document stream, so we do not call a
        // missing/incomplete export a backup. This runs on a background thread.
        val sorted = shift.photos.sortedBy { it.timeMs }
        val smallFiles = sorted.map { photo ->
            val relative = photo.smallPath ?: error(
                "Photo " + photo.id + " is still processing. Wait before exporting."
            )
            val file = privateFile(context, relative)
            require(PhotoProcessor.isValidJpeg(file)) {
                "A compressed photo is missing or unreadable. Export cancelled."
            }
            file
        }
        val originalFiles = sorted.map { photo ->
            if (!includeOriginals) return@map null
            val relative = photo.originalPath ?: return@map null
            val file = privateFile(context, relative)
            if (PhotoProcessor.isValidJpeg(file)) file else null
        }

        val manifest = JSONObject()
            .put("format", "security-patrol-export-v1")
            .put("warning", "Private unencrypted ZIP; does not currently import into the app")
            .put("shiftId", shift.id)
            .put("company", shift.company)
            .put("site", shift.place)
            .put("startedMs", shift.startedMs)
            .put("endedMs", shift.endedMs ?: JSONObject.NULL)
            .put("includeOriginals", includeOriginals)

        val checkpoints = JSONArray()
        shift.checkpoints.forEach { checkpoint ->
            checkpoints.put(
                JSONObject().put("id", checkpoint.id).put("name", checkpoint.name)
            )
        }
        manifest.put("checkpoints", checkpoints)

        val rounds = JSONArray()
        shift.rounds.forEachIndexed { index, round ->
            val checks = JSONArray()
            round.checkedCheckpoints.forEach { (id, visitedAt) ->
                checks.put(JSONObject().put("checkpointId", id).put("visitedAtMs", visitedAt))
            }
            rounds.put(
                JSONObject()
                    .put("id", round.id)
                    .put("number", index + 1)
                    .put("startedMs", round.startedMs)
                    .put("endedMs", round.endedMs ?: JSONObject.NULL)
                    .put("notes", round.notes)
                    .put("checks", checks)
            )
        }
        manifest.put("rounds", rounds)

        val photoList = JSONArray()
        val paths = sorted.map { photo ->
            val roundNumber = shift.rounds.indexOfFirst { it.id == photo.roundId } + 1
            val folder = String.format(Locale.UK, "Patrol_%02d", roundNumber.coerceAtLeast(1))
            val filename = photo.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            folder + "/photos/" + filename + ".jpg"
        }
        sorted.forEachIndexed { index, photo ->
            val originalName = if (originalFiles[index] == null) JSONObject.NULL else
                paths[index].replace("/photos/", "/originals/")
            photoList.put(
                JSONObject()
                    .put("id", photo.id)
                    .put("roundId", photo.roundId ?: JSONObject.NULL)
                    .put("capturedMs", photo.timeMs)
                    .put("siteLabel", photo.place)
                    .put("latitude", photo.lat ?: JSONObject.NULL)
                    .put("longitude", photo.lon ?: JSONObject.NULL)
                    .put("gpsAccuracyMetres", photo.accuracyMetres ?: JSONObject.NULL)
                    .put("gpsAgeSeconds", photo.gpsAgeSeconds ?: JSONObject.NULL)
                    .put("incidentFlag", photo.incidentFlag)
                    .put("manuallyConfirmedSent", photo.confirmedSent)
                    .put("compressedImage", paths[index])
                    .put("originalImage", originalName)
            )
        }
        manifest.put("photos", photoList)

        val stream = context.contentResolver.openOutputStream(destination, "w")
            ?: error("The selected location could not be opened for writing.")
        var originalsWritten = 0
        ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
            putText(zip, "README.txt",
                "Security Patrol shift export\n" +
                    "Company: " + shift.company + "\n" +
                    "Site: " + shift.place + "\n" +
                    "This archive is NOT encrypted: it may contain confidential photos,\n" +
                    "location records, incidents and officer notes.\n" +
                    "Do not share it without your employer's permission.\n" +
                    "ZIP export is not yet a restore/import feature in the app.\n" +
                    "The archive contains the smaller stamped photographs and a shift report.\n" +
                    "Large originals are included only where requested and still available.\n"
            )
            putText(zip, "shift_report.txt", ShiftReport.build(shift))
            putText(zip, "metadata.json", manifest.toString(2))
            smallFiles.forEachIndexed { index, file ->
                putFile(zip, paths[index], file)
                val original = originalFiles[index]
                if (original != null) {
                    putFile(zip, paths[index].replace("/photos/", "/originals/"), original)
                    originalsWritten++
                }
            }
        }
        return Result(sorted.size, originalsWritten)
    }

    private fun putText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun putFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun privateFile(context: Context, relative: String): File {
        val root = context.filesDir.canonicalFile
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(root.path + File.separator)) {
            "Unsafe photo path"
        }
        return file
    }

    fun suggestedFilename(shift: PatrolShift): String {
        val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("Europe/London")
        }.format(Date(shift.startedMs))
        return "Security-Patrol-" + date + ".zip"
    }
}

package uk.org.securitypatrol

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.ArrayList

object ShareHelper {
    fun share(activity: Activity, store: PatrolStore, photos: List<PatrolPhoto>) {
        val ready = photos.filter { p ->
            p.smallPath?.let { PhotoProcessor.isValidJpeg(File(activity.filesDir, it)) } == true
        }
        if (ready.isEmpty()) {
            Toast.makeText(activity, "No processed photos are ready yet", Toast.LENGTH_LONG).show()
            return
        }
        val ids = ready.map { it.id }
        val uris = ArrayList<Uri>()
        ready.forEach { p ->
            val image = File(activity.filesDir, requireNotNull(p.smallPath))
            uris.add(FileProvider.getUriForFile(activity, "uk.org.securitypatrol.fileprovider", image))
        }
        // setMessage() and setItems() are mutually exclusive in Android's
        // AlertDialog layout: using both hid all three sharing choices.
        val options = arrayOf(
            "WhatsApp — select group there",
            "WhatsApp Business — select group there",
            "Other apps"
        )
        AlertDialog.Builder(activity)
            .setTitle("Share " + ready.size + " patrol photos")
            .setItems(options) { _, which ->
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/jpeg"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    clipData = ClipData.newUri(activity.contentResolver, "Patrol photos", uris.first()).also { clip ->
                        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (which == 0) setPackage("com.whatsapp")
                    if (which == 1) setPackage("com.whatsapp.w4b")
                }
                try {
                    if (which == 2) activity.startActivity(Intent.createChooser(intent, "Send patrol photos"))
                    else activity.startActivity(intent)
                    store.noteShareAttempt(ids)
                } catch (ex: ActivityNotFoundException) {
                    Toast.makeText(activity, "WhatsApp option not installed; choose Other apps", Toast.LENGTH_LONG).show()
                } catch (ex: Exception) {
                    Toast.makeText(activity, "Could not open sharing: " + ex.message, Toast.LENGTH_LONG).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }
}

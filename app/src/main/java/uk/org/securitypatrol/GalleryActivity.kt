package uk.org.securitypatrol

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GalleryActivity : AppCompatActivity() {
    private lateinit var store: PatrolStore
    private lateinit var container: LinearLayout
    private lateinit var heading: TextView
    private lateinit var spinner: Spinner
    private val selectedIds = mutableSetOf<String>()
    private var currentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PatrolStore(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && ::container.isInitialized) {
            store = PatrolStore(this)
            renderPhotos()
        }
    }

    private fun buildUi() {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(6))
        }
        val title = TextView(this).apply {
            text = "Patrol Photos & Shift Records"
            textSize = 23f
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(9))
        }
        screen.addView(title)
        spinner = Spinner(this)
        screen.addView(spinner)
        val shifts = store.shifts
        val labels = shifts.map { it.company + " • " + it.place + " • " + shortDate(it.startedMs) }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels.ifEmpty { listOf("No shifts yet") })
        currentId = store.activeShift()?.id ?: shifts.firstOrNull()?.id
        if (shifts.isNotEmpty()) spinner.setSelection(shifts.indexOfFirst { it.id == currentId }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = store.shifts.getOrNull(position)?.id
                if (next != currentId) { selectedIds.clear(); currentId = next }
                renderPhotos()
            }
        }

        heading = TextView(this).apply { textSize = 13f; setPadding(0, dp(8), 0, dp(6)) }
        screen.addView(heading)

        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        firstRow.addView(action("Send selected") { sendSelected() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        firstRow.addView(action("Send unsent") { sendUnsent() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        screen.addView(firstRow)

        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondRow.addView(action("Confirm batch sent") { confirmLastBatch() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        secondRow.addView(action("Delete LARGE originals") { askDeleteOriginals() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        screen.addView(secondRow)

        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(container) }
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(screen)
        renderPhotos()
    }

    private fun currentShift(): PatrolShift? = store.shifts.firstOrNull { it.id == currentId }

    private fun renderPhotos() {
        if (!::container.isInitialized) return
        container.removeAllViews()
        val shift = currentShift()
        if (shift == null) {
            heading.text = "Start a shift from the camera to collect photos."
            return
        }
        val photos = shift.photos.sortedByDescending { it.timeMs }
        val sent = photos.count { it.confirmedSent }
        val ready = photos.count { it.smallPath != null }
        val originals = photos.count { it.originalPath != null }
        heading.text = photos.size.toString() + " photos • " + ready + " ready • " + sent +
            " confirmed sent • " + originals + " originals\n" +
            (if (shift.endedMs == null) "Shift active" else "Shift completed") +
            " • " + shortDate(shift.startedMs)
        photos.forEach { photo ->
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(7), dp(4), dp(7))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val check = CheckBox(this).apply {
                isChecked = selectedIds.contains(photo.id)
                isEnabled = photo.smallPath != null
                setOnCheckedChangeListener { _, value ->
                    if (value) selectedIds.add(photo.id) else selectedIds.remove(photo.id)
                }
            }
            row.addView(check)
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                val path = photo.smallPath?.let { File(filesDir, it) }
                if (path != null && path.exists()) {
                    val opt = BitmapFactory.Options().apply { inSampleSize = 8 }
                    setImageBitmap(BitmapFactory.decodeFile(path.absolutePath, opt))
                } else setImageResource(android.R.drawable.ic_menu_gallery)
            }
            row.addView(image, LinearLayout.LayoutParams(dp(82), dp(82)))
            val info = TextView(this).apply {
                text = shortDate(photo.timeMs) + "\n" + photo.place + "\n" +
                    when {
                        photo.confirmedSent -> "Marked sent"
                        photo.smallPath == null -> "Processing photo..."
                        else -> "Ready to share"
                    }
                textSize = 13f
                setPadding(dp(9), 0, 0, 0)
            }
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            panel.addView(row)
            val edit = action("Edit this photo's place name") { editPhotoPlace(photo) }
            panel.addView(edit)
            container.addView(panel)
            val rule = View(this).apply { setBackgroundColor(Color.DKGRAY) }
            container.addView(rule, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun sendSelected() {
        val shift = currentShift() ?: return
        val photos = shift.photos.filter { selectedIds.contains(it.id) }
        ShareHelper.share(this, store, photos)
    }

    private fun sendUnsent() {
        val shift = currentShift() ?: return
        ShareHelper.share(this, store, shift.photos.filter { !it.confirmedSent })
    }

    private fun confirmLastBatch() {
        val count = store.pendingShareIds.size
        if (count == 0) { message("No recent batch is awaiting confirmation"); return }
        AlertDialog.Builder(this).setTitle("Did you send this batch?")
            .setMessage("Confirm only if you actually pressed Send in WhatsApp. " +
                count + " photographs were handed to the sharing screen.")
            .setPositiveButton("Yes, sent") { _, _ ->
                store.confirmShare()
                renderPhotos()
            }
            .setNegativeButton("Not sent") { _, _ ->
                store.clearPendingShare()
                renderPhotos()
            }
            .setNeutralButton("Keep pending", null).show()
    }

    private fun askDeleteOriginals() {
        val shift = currentShift() ?: return
        val candidates = shift.photos.filter { p ->
            p.originalPath != null &&
                p.basePath?.let { PhotoProcessor.isValidJpeg(File(filesDir, it)) } == true &&
                p.smallPath?.let { PhotoProcessor.isValidJpeg(File(filesDir, it)) } == true
        }
        if (candidates.isEmpty()) { message("No verified originals are eligible for deletion"); return }
        val total = candidates.sumOf { File(filesDir, requireNotNull(it.originalPath)).length() }
        val megabytes = String.format(Locale.UK, "%.1f", total / 1024.0 / 1024.0)
        AlertDialog.Builder(this).setTitle("Delete " + candidates.size + " large originals?")
            .setMessage("This can reclaim approximately " + megabytes +
                " MB for this shift. Small photos, GPS, captions and shift records stay inside the app. " +
                "Deleted originals cannot be recovered; they may contain important evidence details.")
            .setPositiveButton("Delete originals") { _, _ ->
                var removed = 0
                candidates.forEach { p ->
                    val file = File(filesDir, requireNotNull(p.originalPath))
                    val base = File(filesDir, requireNotNull(p.basePath))
                    val small = File(filesDir, requireNotNull(p.smallPath))
                    if (PhotoProcessor.isValidJpeg(base) && PhotoProcessor.isValidJpeg(small) &&
                        file.isFile && file.delete()
                    ) {
                        p.originalPath = null
                        removed++
                    }
                }
                store.save()
                message("Deleted " + removed + " large originals. Small photos were kept.")
                renderPhotos()
            }
            .setNegativeButton("Keep originals", null).show()
    }

    private fun editPhotoPlace(photo: PatrolPhoto) {
        if (photo.basePath == null || photo.smallPath == null) {
            message("This photo is still processing")
            return
        }
        val field = EditText(this).apply {
            setText(photo.place)
            selectAll()
            setPadding(dp(15), dp(8), dp(15), dp(8))
        }
        AlertDialog.Builder(this).setTitle("Edit photo's place name")
            .setMessage("Updates the small stamped photo; the actual GPS and capture time stay unchanged.")
            .setView(field)
            .setPositiveButton("Update") { _, _ ->
                val newPlace = field.text.toString().trim()
                if (newPlace.isEmpty()) return@setPositiveButton
                Thread {
                    try {
                        PhotoProcessor.restamp(this, photo, newPlace)
                        store.updatePlace(photo.id, newPlace)
                        runOnUiThread { renderPhotos(); message("Place name updated") }
                    } catch (e: Exception) {
                        runOnUiThread { message("Could not update photograph: " + e.message) }
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun action(text: String, clicked: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 11f
        setOnClickListener { clicked() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun shortDate(time: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.UK).apply {
        timeZone = TimeZone.getTimeZone("Europe/London")
    }.format(Date(time))
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

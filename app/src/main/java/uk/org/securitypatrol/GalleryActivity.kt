package uk.org.securitypatrol

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Screen 2 of 3: simple photo gallery. Stored shift metadata remains unchanged.
 * A selected patrol is the default; "All patrols" is an explicit action.
 */
class GalleryActivity : AppCompatActivity() {
    private lateinit var store: PatrolStore
    private lateinit var shiftPicker: Spinner
    private lateinit var roundPicker: Spinner
    private lateinit var gallery: LinearLayout
    private lateinit var details: TextView
    private lateinit var selectionLabel: TextView
    private lateinit var confirmButton: Button
    private val selectedIds = mutableSetOf<String>()
    private var shiftId: String? = null
    private var roundId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        store = PatrolStore(this)
        shiftId = store.activeShift()?.id ?: store.shifts.firstOrNull()?.id
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::gallery.isInitialized) {
            // Re-read the last WhatsApp share attempt and updated camera shots.
            store = PatrolStore(this)
            renderPhotos()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(5))
        }
        titleRow.addView(heading("PHOTOS", 22f), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(button("Camera") { finish() }, LinearLayout.LayoutParams(dp(105), dp(44)))
        root.addView(titleRow)

        val filters = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), dp(4))
        }
        shiftPicker = Spinner(this)
        roundPicker = Spinner(this)
        filters.addView(shiftPicker, LinearLayout.LayoutParams(-1, dp(44)))
        filters.addView(roundPicker, LinearLayout.LayoutParams(-1, dp(44)))
        details = TextView(this).apply {
            setTextColor(Color.BLACK)
            textSize = 13f
            setPadding(dp(4), dp(3), dp(4), dp(6))
        }
        selectionLabel = TextView(this).apply {
            setTextColor(Color.DKGRAY)
            textSize = 12f
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        filters.addView(details)
        filters.addView(selectionLabel)
        val selectionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selectionRow.addView(button("Select all") { chooseAll() }, LinearLayout.LayoutParams(0, dp(43), 1f))
        selectionRow.addView(button("Unsent") { chooseUnsent() }, LinearLayout.LayoutParams(0, dp(43), 1f))
        selectionRow.addView(button("Clear") { selectedIds.clear(); renderPhotos() }, LinearLayout.LayoutParams(0, dp(43), 1f))
        filters.addView(selectionRow)
        root.addView(filters)

        gallery = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val scroll = ScrollView(this).apply { addView(gallery) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        val send = button("SEND SELECTED TO WHATSAPP") { shareSelected() }.apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }
        footer.addView(send, LinearLayout.LayoutParams(-1, dp(49)))
        val other = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        other.addView(button("Delete selected") { askDeleteSelected() },
            LinearLayout.LayoutParams(0, dp(45), 1f))
        confirmButton = button("Confirm sent") { confirmLastBatch() }
        other.addView(confirmButton, LinearLayout.LayoutParams(0, dp(45), 1f))
        footer.addView(other)
        root.addView(footer)
        setContentView(root)

        val labels = store.shifts.map { it.company + " • " + it.place + " • " + ukDate(it.startedMs) }
        shiftPicker.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            labels.ifEmpty { listOf("No shifts saved yet") }
        )
        val chosen = store.shifts.indexOfFirst { it.id == shiftId }.coerceAtLeast(0)
        shiftPicker.setSelection(chosen)
        shiftPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newId = store.shifts.getOrNull(position)?.id
                if (newId != shiftId) {
                    shiftId = newId
                    setPatrolOptions()
                }
            }
        }
        roundPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = if (position == 0) null else currentShift()?.rounds?.getOrNull(position - 1)?.id
                if (next != roundId) {
                    roundId = next
                    chooseUnsent()
                }
            }
        }
        setPatrolOptions()
    }

    private fun currentShift(): PatrolShift? = store.shifts.firstOrNull { it.id == shiftId }

    private fun currentPhotos(): List<PatrolPhoto> = currentShift()?.photos.orEmpty()
        .filter { roundId == null || it.roundId == roundId }

    private fun shareReady(photo: PatrolPhoto): Boolean =
        photo.smallPath?.let { PhotoProcessor.isValidJpeg(File(filesDir, it)) } == true

    private fun setPatrolOptions() {
        val shift = currentShift()
        val options = mutableListOf("All patrols • entire shift")
        shift?.rounds?.forEachIndexed { index, round ->
            val amount = shift.photos.count { it.roundId == round.id }
            options.add("Patrol ${index + 1} • ${ukTime(round.startedMs)} • $amount photos")
        }
        roundPicker.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, options
        )
        // Prefer active patrol; if shift is over, show the latest patrol.
        roundId = shift?.activeRoundId ?: shift?.rounds?.lastOrNull()?.id
        val index = shift?.rounds?.indexOfFirst { it.id == roundId } ?: -1
        roundPicker.setSelection((index + 1).coerceAtLeast(0))
        chooseUnsent()
    }

    private fun chooseAll() {
        selectedIds.clear()
        currentPhotos().filter(::shareReady).forEach { selectedIds.add(it.id) }
        renderPhotos()
    }

    private fun chooseUnsent() {
        selectedIds.clear()
        currentPhotos().filter { !it.confirmedSent && shareReady(it) }.forEach {
            selectedIds.add(it.id)
        }
        renderPhotos()
    }

    private fun renderPhotos() {
        if (!::gallery.isInitialized) return
        gallery.removeAllViews()
        val shift = currentShift()
        if (shift == null) {
            details.text = "Start your first shift from the Camera screen."
            selectionLabel.text = ""
            confirmButton.visibility = if (store.pendingShareIds.isEmpty()) View.GONE else View.VISIBLE
            return
        }
        val photos = currentPhotos().sortedByDescending { it.timeMs }
        selectedIds.retainAll(photos.filter(::shareReady).map { it.id }.toSet())
        val roundIndex = shift.rounds.indexOfFirst { it.id == roundId }
        val scope = if (roundId == null) "Entire shift" else "Patrol " + (roundIndex + 1)
        val ready = photos.count(::shareReady)
        details.text = "$scope  •  ${photos.size} photos  •  $ready ready"
        updateSelectionLabel()
        confirmButton.visibility = if (store.pendingShareIds.isEmpty()) View.GONE else View.VISIBLE

        photos.chunked(3).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            group.forEach { photo ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(4), dp(4), dp(4), dp(8))
                }
                val image = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Open photo taken " + ukTime(photo.timeMs)
                    val path = photo.smallPath?.let { File(filesDir, it) }
                    val thumb = if (path != null && path.isFile) {
                        try {
                            BitmapFactory.decodeFile(path.absolutePath, BitmapFactory.Options().apply {
                                inSampleSize = 8
                            })
                        } catch (_: Exception) { null }
                    } else null
                    if (thumb != null) setImageBitmap(thumb)
                    else setImageResource(android.R.drawable.ic_menu_gallery)
                    setOnClickListener { openPhoto(photo) }
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        setStroke(dp(1), Color.LTGRAY)
                    }
                }
                item.addView(image, LinearLayout.LayoutParams(-1, dp(115)))
                val tick = CheckBox(this).apply {
                    text = ukTime(photo.timeMs)
                    textSize = 11f
                    setTextColor(Color.BLACK)
                    isEnabled = shareReady(photo)
                    isChecked = selectedIds.contains(photo.id)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedIds.add(photo.id) else selectedIds.remove(photo.id)
                        updateSelectionLabel()
                    }
                }
                item.addView(tick, LinearLayout.LayoutParams(-1, dp(42)))
                val state = TextView(this).apply {
                    text = when {
                        photo.smallPath == null -> "Processing"
                        photo.confirmedSent -> "Marked sent"
                        else -> "Ready"
                    }
                    setTextColor(Color.DKGRAY)
                    textSize = 10f
                }
                item.addView(state)
                row.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(3 - group.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, dp(8), 1f))
            }
            gallery.addView(row)
        }
        if (photos.isEmpty()) {
            gallery.addView(note("No photos in this patrol yet. Open Camera to take some."))
        }
    }

    private fun updateSelectionLabel() {
        if (!::selectionLabel.isInitialized) return
        val photos = currentPhotos()
        val selected = photos.count { it.id in selectedIds }
        selectionLabel.text = "$selected selected  •  ${photos.count { !it.confirmedSent }} not marked sent"
    }

    private fun shareSelected() {
        val photos = currentPhotos().filter { it.id in selectedIds && shareReady(it) }
        if (photos.isEmpty()) { toast("No photos selected. Tap Select all."); return }
        val repeatCount = photos.count { it.confirmedSent }
        if (repeatCount > 0) {
            AlertDialog.Builder(this)
                .setTitle("Send some photos again?")
                .setMessage("$repeatCount photos were previously marked sent.")
                .setPositiveButton("Send again") { _, _ -> ShareHelper.share(this, store, photos) }
                .setNegativeButton("Cancel", null).show()
        } else {
            ShareHelper.share(this, store, photos)
        }
    }

    private fun confirmLastBatch() {
        val amount = store.pendingShareIds.size
        if (amount == 0) return
        AlertDialog.Builder(this).setTitle("Were the photos sent?")
            .setMessage("Confirm only if you actually pressed Send in WhatsApp. $amount photos were handed to its sharing screen.")
            .setPositiveButton("Yes, sent") { _, _ ->
                val ids = store.pendingShareIds.toSet()
                store.confirmShare()
                selectedIds.removeAll(ids)
                renderPhotos()
            }
            .setNegativeButton("Not sent") { _, _ ->
                store.clearPendingShare()
                renderPhotos()
            }
            .setNeutralButton("Later", null).show()
    }

    private fun askDeleteSelected() {
        val shift = currentShift() ?: return
        val chosen = currentPhotos().filter { it.id in selectedIds && shareReady(it) }
        if (chosen.isEmpty()) { toast("Select the photos to delete"); return }
        AlertDialog.Builder(this)
            .setTitle("Delete ${chosen.size} photographs permanently?")
            .setMessage("All copies of these selected photographs will be deleted from the app, including large originals. This cannot be undone. Other patrols are untouched.")
            .setPositiveButton("Delete") { _, _ ->
                val deleted = store.removePhotos(shift.id, chosen.map { it.id }.toSet())
                deleted.forEach { photo ->
                    listOf(photo.originalPath, photo.basePath, photo.smallPath).forEach { relative ->
                        if (relative != null) deletePrivatePhoto(relative)
                    }
                }
                selectedIds.clear()
                renderPhotos()
                toast("Deleted ${deleted.size} photos")
            }
            .setNegativeButton("Keep photos", null).show()
    }

    private fun deletePrivatePhoto(relative: String) {
        try {
            val root = filesDir.canonicalFile
            val file = File(root, relative).canonicalFile
            if (file.path.startsWith(root.path + File.separator + "patrols" + File.separator)) {
                file.delete()
            }
        } catch (e: Exception) {
            android.util.Log.w("PatrolPhotos", "Unable to clean up photo file", e)
        }
    }

    private fun openPhoto(photo: PatrolPhoto) {
        val relative = photo.smallPath ?: run { toast("Photo is still processing"); return }
        val file = File(filesDir, relative)
        if (!PhotoProcessor.isValidJpeg(file)) { toast("Photo is unavailable"); return }
        val bitmap = try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
        if (bitmap == null) { toast("Could not open the photo"); return }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            setImageBitmap(bitmap)
        }
        val frame = FrameLayout(this).apply {
            addView(image, FrameLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.60f).toInt()))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(ukDate(photo.timeMs) + "  " + ukTime(photo.timeMs))
            .setView(frame)
            .setPositiveButton("Close", null)
            .setNeutralButton("Edit site") { _, _ -> editPhotoSite(photo) }
            .create()
        dialog.setOnDismissListener {
            image.setImageDrawable(null)
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        dialog.show()
    }

    private fun editPhotoSite(photo: PatrolPhoto) {
        val shift = currentShift() ?: return
        val edit = EditText(this).apply {
            setText(photo.place)
            selectAll()
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this).setTitle("Edit this photo's site label")
            .setView(edit)
            .setMessage("The recorded time and GPS coordinates will stay unchanged.")
            .setPositiveButton("Save") { _, _ ->
                val newSite = edit.text.toString().trim()
                if (newSite.isEmpty()) return@setPositiveButton
                Thread {
                    try {
                        PhotoProcessor.restamp(this, photo, newSite, shift.company)
                        store.updatePlace(photo.id, newSite)
                        runOnUiThread { renderPhotos(); toast("Photo label updated") }
                    } catch (e: Exception) {
                        runOnUiThread { toast("Could not update photo: " + e.message) }
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun heading(value: String, size: Float): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.BLACK)
        setTypeface(null, Typeface.BOLD)
    }
    private fun note(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(Color.DKGRAY)
        setPadding(dp(8), dp(14), dp(8), dp(14))
    }
    private fun button(value: String, clicked: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.BLACK)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), Color.BLACK)
            cornerRadius = dp(8).toFloat()
        }
        setOnClickListener { clicked() }
    }
    private fun ukDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply {
        timeZone = TimeZone.getTimeZone("Europe/London")
    }.format(Date(ms))
    private fun ukTime(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.UK).apply {
        timeZone = TimeZone.getTimeZone("Europe/London")
    }.format(Date(ms))
    private fun dp(i: Int) = (i * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

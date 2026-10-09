package uk.org.securitypatrol

import android.content.DialogInterface
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GalleryActivity : AppCompatActivity() {
    private lateinit var store: PatrolStore
    private lateinit var container: LinearLayout
    private lateinit var heading: TextView
    private lateinit var selectionStatus: TextView
    private lateinit var spinner: Spinner
    private lateinit var patrolSpinner: Spinner
    private var selectedRoundId: String? = null // null = deliberately view all rounds in this shift
    private val selectedIds = mutableSetOf<String>()
    private var currentId: String? = null
    private var exportShiftId: String? = null
    private var exportOriginals = false
    private var exportInProgress = false

    // Android's document picker lets the officer save the ZIP wherever they
    // choose. No storage permission or automatic internet/cloud upload.
    private val createArchive = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val selectedShiftId = exportShiftId
        exportShiftId = null
        if (uri == null || selectedShiftId == null) return@registerForActivityResult
        if (exportInProgress) {
            message("An export is already running")
            return@registerForActivityResult
        }
        // Re-read from private storage after returning from the document picker.
        val snapshot = PatrolStore(this).shifts.firstOrNull { it.id == selectedShiftId }
        if (snapshot == null) {
            message("The selected shift was not found")
            return@registerForActivityResult
        }
        val includeOriginals = exportOriginals
        exportInProgress = true
        message("Exporting ZIP. Keep the app open until finished.")
        Thread {
            try {
                val result = ShiftArchive.write(applicationContext, snapshot, uri, includeOriginals)
                runOnUiThread {
                    message("Export complete: " + result.photos + " small photos, " +
                        result.originals + " large originals")
                }
            } catch (e: Exception) {
                android.util.Log.e("PatrolGallery", "Shift export failed", e)
                runOnUiThread {
                    message("Export failed or incomplete. Try again: " + e.message)
                }
            } finally {
                runOnUiThread { exportInProgress = false }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        exportShiftId = savedInstanceState?.getString("exportShiftId")
        exportOriginals = savedInstanceState?.getBoolean("exportOriginals") ?: false
        store = PatrolStore(this)
        buildUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportShiftId", exportShiftId)
        outState.putBoolean("exportOriginals", exportOriginals)
        super.onSaveInstanceState(outState)
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
        }
        // Prevent shift controls and the final photograph row from overlapping
        // the status / navigation bars on edge-to-edge Android devices.
        ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                dp(12) + bars.left, dp(12) + bars.top,
                dp(12) + bars.right, dp(6) + bars.bottom
            )
            insets
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
        patrolSpinner = Spinner(this)
        screen.addView(patrolSpinner)
        patrolSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val requested = if (position == 0) null else currentShift()?.rounds?.getOrNull(position - 1)?.id
                if (requested != selectedRoundId) {
                    selectedRoundId = requested
                    selectedIds.clear()
                    autoSelectUnsent()
                }
                renderPhotos()
            }
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = store.shifts.getOrNull(position)?.id
                if (next != currentId) {
                    selectedIds.clear()
                    currentId = next
                    populatePatrols()
                }
                renderPhotos()
            }
        }

        heading = TextView(this).apply { textSize = 13f; setPadding(0, dp(8), 0, dp(6)) }
        screen.addView(heading)
        selectionStatus = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(3), 0, dp(6))
        }
        screen.addView(selectionStatus)

        val selectionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selectionRow.addView(action("Select all") { selectAllReady() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        selectionRow.addView(action("Unsent") { selectUnsent() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        selectionRow.addView(action("Flagged") { selectFlagged() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        selectionRow.addView(action("Clear") { clearSelection() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        screen.addView(selectionRow)

        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        firstRow.addView(action("Send selected") { sendSelected() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        firstRow.addView(action("Send unsent") { sendUnsent() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        screen.addView(firstRow)

        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondRow.addView(action("Confirm batch sent") { confirmLastBatch() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        secondRow.addView(action("Delete originals (shift)") { askDeleteOriginals() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        screen.addView(secondRow)

        // Existing compressed photos can be updated without changing the
        // originals, timestamps, recorded GPS or previously shared messages.
        screen.addView(
            action("Update company + site stamps on existing photos") { refreshVisibleStamps() },
            LinearLayout.LayoutParams(-1, dp(48))
        )

        val patrolUtilities = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        patrolUtilities.addView(
            action("Patrol notes") { editPatrolNotes() },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )
        patrolUtilities.addView(
            action("Shift report") { showShiftReport() },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )
        screen.addView(patrolUtilities)

        val moreTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        moreTools.addView(
            action("Checkpoints") { showCheckpoints() },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )
        moreTools.addView(
            action("Export shift ZIP") { askExportShift() },
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )
        screen.addView(moreTools)

        container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(container) }
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(screen)
        populatePatrols()
        renderPhotos()
    }

    private fun currentShift(): PatrolShift? = store.shifts.firstOrNull { it.id == currentId }

    private fun currentPhotos(): List<PatrolPhoto> = currentShift()?.photos.orEmpty().filter {
        selectedRoundId == null || it.roundId == selectedRoundId
    }

    private fun isShareReady(photo: PatrolPhoto): Boolean =
        photo.smallPath?.let { File(filesDir, it).isFile } == true

    private fun populatePatrols() {
        val shift = currentShift()
        val labels = mutableListOf("All patrols (whole shift)")
        shift?.rounds?.forEachIndexed { index, round ->
            val count = shift.photos.count { it.roundId == round.id }
            labels.add("Patrol " + (index + 1) + " — " + shortTime(round.startedMs) +
                " (" + count + " photos)")
        }
        patrolSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, labels
        )
        // Start on the CURRENT round rather than all shifts' photos; if a shift
        // ended, show its latest round. All rounds is always an explicit choice.
        selectedRoundId = if (shift?.activeRoundId != null) {
            shift.activeRoundId
        } else {
            shift?.rounds?.lastOrNull()?.id
        }
        selectedIds.clear()
        autoSelectUnsent()
        val selectedIndex = shift?.rounds?.indexOfFirst { it.id == selectedRoundId } ?: -1
        patrolSpinner.setSelection((selectedIndex + 1).coerceAtLeast(0))
    }

    private fun autoSelectUnsent() {
        currentPhotos().filter { !it.confirmedSent && isShareReady(it) }
            .forEach { selectedIds.add(it.id) }
    }

    private fun selectAllReady() {
        selectedIds.clear()
        currentPhotos().filter(::isShareReady).forEach { selectedIds.add(it.id) }
        renderPhotos()
    }

    private fun selectUnsent() {
        selectedIds.clear()
        autoSelectUnsent()
        renderPhotos()
    }

    private fun selectFlagged() {
        selectedIds.clear()
        currentPhotos().filter { it.incidentFlag && isShareReady(it) }
            .forEach { selectedIds.add(it.id) }
        renderPhotos()
    }

    private fun clearSelection() {
        selectedIds.clear()
        renderPhotos()
    }

    private fun updateSelectionStatus() {
        val photos = currentPhotos()
        val selected = photos.filter { selectedIds.contains(it.id) }
        val resent = selected.count { it.confirmedSent }
        selectionStatus.text = "${selected.size} selected / ${photos.count(::isShareReady)} ready" +
            if (resent > 0) " — ${resent} already marked sent" else ""
    }

    private fun renderPhotos() {
        if (!::container.isInitialized) return
        container.removeAllViews()
        val shift = currentShift()
        if (shift == null) {
            selectedIds.clear()
            heading.text = "Start a shift from the camera to collect photos."
            updateSelectionStatus()
            return
        }
        val photos = currentPhotos().sortedByDescending { it.timeMs }
        selectedIds.retainAll(photos.filter(::isShareReady).map { it.id }.toSet())
        val sent = photos.count { it.confirmedSent }
        val ready = photos.count(::isShareReady)
        val originals = photos.count { it.originalPath != null }
        val flagged = photos.count { it.incidentFlag }
        val scopeLabel = if (selectedRoundId == null) "Whole shift" else {
            "Patrol " + (shift.rounds.indexOfFirst { it.id == selectedRoundId } + 1)
        }
        heading.text = scopeLabel + " • " + photos.size + " photos • " + ready + " ready\n" +
            flagged + " flagged • " + sent + " marked sent • " + originals + " originals\n" +
            shortDate(shift.startedMs)
        updateSelectionStatus()
        var previousRoundId: String? = ""
        photos.forEach { photo ->
            if (photo.roundId != previousRoundId) {
                val roundNumber = shift.rounds.indexOfFirst { it.id == photo.roundId } + 1
                val round = shift.rounds.firstOrNull { it.id == photo.roundId }
                val headingText = TextView(this).apply {
                    text = "PATROL " + roundNumber.coerceAtLeast(1) +
                        "  •  " + (round?.let { shortTime(it.startedMs) } ?: shortTime(photo.timeMs))
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setPadding(dp(7), dp(12), dp(7), dp(7))
                }
                container.addView(headingText)
                previousRoundId = photo.roundId
            }
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
                isEnabled = isShareReady(photo)
                setOnCheckedChangeListener { _, value ->
                    if (value) selectedIds.add(photo.id) else selectedIds.remove(photo.id)
                    updateSelectionStatus()
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
            image.contentDescription = "Open larger view of photo taken " + shortDate(photo.timeMs)
            image.setOnClickListener { showPhotoPreview(photo) }
            row.addView(image, LinearLayout.LayoutParams(dp(82), dp(82)))
            val info = TextView(this).apply {
                text = (if (photo.incidentFlag) "⚑ FLAGGED INCIDENT\n" else "") +
                    shortDate(photo.timeMs) + "\n" + photo.place + "\n" +
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
            val photoActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            photoActions.addView(
                action("Edit place") { editPhotoPlace(photo) },
                LinearLayout.LayoutParams(0, dp(46), 1f)
            )
            photoActions.addView(
                action(if (photo.incidentFlag) "Unflag incident" else "Flag incident") {
                    try {
                        store.flagIncident(photo.id, !photo.incidentFlag)
                        renderPhotos()
                    } catch (e: Exception) {
                        message("Could not update incident flag: " + e.message)
                    }
                },
                LinearLayout.LayoutParams(0, dp(46), 1f)
            )
            panel.addView(photoActions)
            container.addView(panel)
            val rule = View(this).apply { setBackgroundColor(Color.DKGRAY) }
            container.addView(rule, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }

    private fun sendSelected() {
        val photos = currentPhotos().filter { selectedIds.contains(it.id) && isShareReady(it) }
        if (photos.isEmpty()) {
            message("No photos selected. Use Select all or Unsent only.")
            return
        }
        val previouslySent = photos.count { it.confirmedSent }
        if (previouslySent > 0) {
            AlertDialog.Builder(this)
                .setTitle("Some photos were already sent")
                .setMessage("This selection includes $previouslySent photos marked sent. Share them again?")
                .setPositiveButton("Send again") { _, _ -> ShareHelper.share(this, store, photos) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            ShareHelper.share(this, store, photos)
        }
    }

    private fun sendUnsent() {
        val photos = currentPhotos().filter { !it.confirmedSent && isShareReady(it) }
        if (photos.isEmpty()) {
            message("No unconfirmed photos ready in this patrol")
            return
        }
        ShareHelper.share(this, store, photos)
    }

    private fun confirmLastBatch() {
        val count = store.pendingShareIds.size
        if (count == 0) { message("No recent batch is awaiting confirmation"); return }
        AlertDialog.Builder(this).setTitle("Did you send this batch?")
            .setMessage("Confirm only if you actually pressed Send in WhatsApp. " +
                count + " photographs were handed to the sharing screen.")
            .setPositiveButton("Yes, sent") { _, _ ->
                val confirmedIds = store.pendingShareIds.toSet()
                store.confirmShare()
                selectedIds.removeAll(confirmedIds)
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

    private fun askExportShift() {
        val shift = currentShift() ?: run { message("No shift to export"); return }
        if (exportInProgress) {
            message("Wait for the previous export to finish")
            return
        }
        val choices = arrayOf(
            "Small stamped photos + report + records",
            "Small photos + any available large originals"
        )
        AlertDialog.Builder(this)
            .setTitle("Export private shift ZIP (not encrypted)")
            .setItems(choices) { _, selected ->
                exportShiftId = shift.id
                exportOriginals = selected == 1
                createArchive.launch(ShiftArchive.suggestedFilename(shift))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCheckpoints() {
        val shift = currentShift() ?: run { message("No shift selected"); return }
        val round = shift.rounds.firstOrNull { it.id == selectedRoundId } ?: run {
            message("Choose a single patrol from the dropdown first")
            return
        }
        val editable = shift.endedMs == null && round.endedMs == null &&
            shift.activeRoundId == round.id
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        val scroll = ScrollView(this).apply { addView(layout) }
        fun refreshRows() {
            layout.removeAllViews()
            val eligible = shift.checkpoints.filter {
                round.endedMs == null || it.addedAtMs <= round.endedMs!!
            }
            val visits = eligible.count { round.checkedCheckpoints.containsKey(it.id) }
            val summary = TextView(this).apply {
                text = visits.toString() + "/" + eligible.size +
                    " checkpoints marked visited. Ticks are manual, not GPS proof."
                textSize = 13f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(5), 0, dp(12))
            }
            layout.addView(summary)
            if (eligible.isEmpty()) {
                layout.addView(TextView(this).apply {
                    text = if (editable) "No checkpoints configured yet. Tap Add checkpoint." else
                        "No checkpoints were configured during this patrol."
                    setTextColor(Color.WHITE)
                })
            }
            eligible.forEach { checkpoint ->
                val timestamp = round.checkedCheckpoints[checkpoint.id]
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(5), 0, dp(8))
                }
                val tick = CheckBox(this).apply {
                    text = checkpoint.name
                    setTextColor(Color.WHITE)
                    isChecked = timestamp != null
                    isEnabled = editable
                }
                row.addView(tick)
                if (timestamp != null) row.addView(TextView(this).apply {
                    text = "Checked " + shortDate(timestamp) + " (manual)"
                    textSize = 12f
                    setTextColor(Color.LTGRAY)
                })
                layout.addView(row)
                if (editable) tick.setOnCheckedChangeListener { _, checked ->
                    if (!checked && timestamp != null) {
                        AlertDialog.Builder(this)
                            .setTitle("Remove visit check?")
                            .setMessage("This clears the recorded check time for " + checkpoint.name + ".")
                            .setPositiveButton("Remove check") { _, _ ->
                                try {
                                    store.markCheckpoint(shift.id, round.id, checkpoint.id, false)
                                    refreshRows()
                                } catch (e: Exception) {
                                    message("Unable to clear check: " + e.message)
                                    refreshRows()
                                }
                            }
                            .setNegativeButton("Keep checked") { _, _ -> refreshRows() }
                            .show()
                    } else {
                        try {
                            store.markCheckpoint(shift.id, round.id, checkpoint.id, checked)
                        } catch (e: Exception) {
                            message("Unable to save visit: " + e.message)
                        }
                        refreshRows()
                    }
                }
            }
        }
        refreshRows()
        val number = shift.rounds.indexOfFirst { it.id == round.id } + 1
        val builder = AlertDialog.Builder(this)
            .setTitle("Patrol " + number + " — checkpoints")
            .setView(scroll)
            .setPositiveButton("Done", null)
        if (editable) builder.setNeutralButton("Add checkpoint", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            if (editable) dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                val field = EditText(this).apply {
                    hint = "e.g. Main Gate, Fire Exit A"
                    setSingleLine(true)
                    setPadding(dp(16), dp(15), dp(16), dp(15))
                    filters = arrayOf(InputFilter.LengthFilter(100))
                }
                AlertDialog.Builder(this)
                    .setTitle("Add a site checkpoint")
                    .setView(field)
                    .setPositiveButton("Add") { _, _ ->
                        try {
                            store.addCheckpoint(shift.id, field.text.toString())
                            refreshRows()
                        } catch (e: Exception) {
                            message(e.message ?: "Cannot add checkpoint")
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        dialog.show()
    }

    private fun showShiftReport() {
        val shift = currentShift() ?: run {
            message("No shift to report on")
            return
        }
        ShiftReport.show(this, shift)
    }

    private fun editPatrolNotes() {
        val shift = currentShift() ?: run {
            message("Open a shift first")
            return
        }
        val round = shift.rounds.firstOrNull { it.id == selectedRoundId }
        if (round == null) {
            message("Choose one patrol, rather than All patrols, to edit its notes")
            return
        }
        val editor = EditText(this).apply {
            setText(round.notes)
            hint = "Observations, doors checked, issues reported..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            maxLines = 7
            filters = arrayOf(InputFilter.LengthFilter(4000))
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val number = shift.rounds.indexOfFirst { it.id == round.id } + 1
        AlertDialog.Builder(this)
            .setTitle("Patrol " + number + " notes")
            .setMessage("Private notes stored in this shift. Include them in the shift report only if you choose to share it.")
            .setView(editor)
            .setPositiveButton("Save notes") { _, _ ->
                try {
                    store.updateRoundNotes(shift.id, round.id, editor.text.toString().trim())
                    message("Patrol " + number + " notes saved")
                } catch (e: Exception) {
                    message("Could not save notes: " + e.message)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPhotoPreview(photo: PatrolPhoto) {
        val relative = photo.smallPath ?: run {
            message("This photograph is still processing")
            return
        }
        val file = File(filesDir, relative)
        if (!PhotoProcessor.isValidJpeg(file)) {
            message("The compressed photograph is unavailable")
            return
        }
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
        if (bitmap == null) {
            message("Unable to open the photograph")
            return
        }
        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
            setBackgroundColor(Color.BLACK)
            contentDescription = "Patrol photo showing the company, site, date and GPS stamp"
        }
        val height = (resources.displayMetrics.heightPixels * 0.64f).toInt()
        val wrapper = FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            ))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Photo " + shortDate(photo.timeMs))
            .setView(wrapper)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            preview.setImageDrawable(null)
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        dialog.show()
    }

    private fun refreshVisibleStamps() {
        val shift = currentShift() ?: return
        val scope = if (selectedRoundId == null) "the entire shift" else "this patrol"
        val candidates = currentPhotos().filter { photo ->
            photo.basePath?.let { PhotoProcessor.isValidJpeg(File(filesDir, it)) } == true &&
                photo.smallPath?.let { PhotoProcessor.isValidJpeg(File(filesDir, it)) } == true
        }
        if (candidates.isEmpty()) {
            message("No completed photos available for re-stamping")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Update " + candidates.size + " photo stamps?")
            .setMessage(
                "Adds Company: " + shift.company + " and each photo's saved site " +
                    "to the small photos in " + scope + ". Original files, GPS, " +
                    "timestamps and sent flags stay unchanged. Photos already sent " +
                    "through WhatsApp cannot be changed there."
            )
            .setPositiveButton("Update stamps") { _, _ ->
                Thread {
                    var updated = 0
                    var failed = 0
                    candidates.forEach { photo ->
                        try {
                            PhotoProcessor.restamp(this, photo, photo.place, shift.company)
                            updated++
                        } catch (e: Exception) {
                            failed++
                            android.util.Log.e("PatrolGallery", "Could not re-stamp photo " + photo.id, e)
                        }
                    }
                    runOnUiThread {
                        renderPhotos()
                        message("Updated " + updated + " photos" +
                            if (failed > 0) "; " + failed + " could not be updated" else "")
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                        val company = store.shifts.firstOrNull { it.id == photo.shiftId }?.company.orEmpty()
                        PhotoProcessor.restamp(this, photo, newPlace, company)
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
    private fun shortTime(time: Long): String = SimpleDateFormat("HH:mm", Locale.UK).apply {
        timeZone = TimeZone.getTimeZone("Europe/London")
    }.format(Date(time))
    private fun shortDate(time: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.UK).apply {
        timeZone = TimeZone.getTimeZone("Europe/London")
    }.format(Date(time))
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

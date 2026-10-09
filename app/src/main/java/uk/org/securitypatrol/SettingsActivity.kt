package uk.org.securitypatrol

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One settings screen. Nothing is ever automatically sent or deleted. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var companyField: EditText
    private lateinit var siteField: EditText
    private lateinit var gpsCheck: CheckBox
    private lateinit var storageText: TextView
    private var exportShiftId: String? = null
    private var includeOriginals = false
    private var exporting = false

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.none { it }) {
            PatrolPreferences.setGpsEnabled(this, false)
            gpsCheck.isChecked = false
            toast("Location permission not granted; GPS is off")
        }
    }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val id = exportShiftId
        exportShiftId = null
        if (uri == null || id == null || exporting) return@registerForActivityResult
        val shift = PatrolStore(this).shifts.firstOrNull { it.id == id } ?: run {
            toast("Shift unavailable")
            return@registerForActivityResult
        }
        exporting = true
        val originals = includeOriginals
        toast("Saving ZIP. Keep this screen open.")
        Thread {
            try {
                val result = ShiftArchive.write(applicationContext, shift, uri, originals)
                runOnUiThread {
                    toast("Saved ${result.photos} small photos and ${result.originals} originals")
                }
            } catch (e: Exception) {
                android.util.Log.e("PatrolSettings", "ZIP export failed", e)
                runOnUiThread { toast("ZIP export failed: " + e.message) }
            } finally {
                runOnUiThread { exporting = false; refreshStorage() }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        exportShiftId = savedInstanceState?.getString("exportShift")
        includeOriginals = savedInstanceState?.getBoolean("originals") ?: false
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        ViewCompat.setOnApplyWindowInsetsListener(outer) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            ins
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        top.addView(heading("SETTINGS", 22f), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(button("Camera") { finish() }, LinearLayout.LayoutParams(dp(100), dp(44)))
        outer.addView(top)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(28))
        }
        val latest = PatrolStore(this).shifts.firstOrNull()
        body.addView(section("Company and site"))
        body.addView(note("Used for the next shift and stamped on each new photograph."))
        companyField = EditText(this).apply {
            hint = "Security company"
            setSingleLine(true)
            setText(PatrolPreferences.company(this@SettingsActivity).ifBlank { latest?.company.orEmpty() })
        }
        siteField = EditText(this).apply {
            hint = "Site name / address"
            setText(PatrolPreferences.site(this@SettingsActivity).ifBlank { latest?.place.orEmpty() })
            maxLines = 3
        }
        body.addView(companyField)
        body.addView(siteField)
        body.addView(button("Previously used company / site") { choosePreviousSite() })
        body.addView(button("Save company and site") { saveCompanyAndSite() })
        body.addView(note("These labels change future shifts, not existing photographs."))
        body.addView(button("End current shift") { confirmEndShift() })

        body.addView(section("Camera"))
        gpsCheck = CheckBox(this).apply {
            text = "Record GPS on new photos"
            setTextColor(Color.BLACK)
            isChecked = PatrolPreferences.gpsEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, enabled ->
                PatrolPreferences.setGpsEnabled(this@SettingsActivity, enabled)
                if (enabled && !hasLocationPermission()) {
                    locationPermission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }
        }
        body.addView(gpsCheck)
        body.addView(CheckBox(this).apply {
            text = "Group hourly patrol photographs"
            setTextColor(Color.BLACK)
            isChecked = PatrolPreferences.autoHourly(this@SettingsActivity)
            setOnCheckedChangeListener { _, enabled ->
                PatrolPreferences.setAutoHourly(this@SettingsActivity, enabled)
            }
        })
        body.addView(note("UK timestamps are always printed on photos. Hourly grouping applies to a new shift."))
        body.addView(button("Lock camera controls") { lockCameraOnReturn() })
        body.addView(note("Photo-only lock hides every control except TAKE PHOTO. Hold the photo button for 2 seconds to unlock."))

        body.addView(section("Compressed photo size"))
        val qualityGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        listOf(
            PatrolPreferences.SMALL to "Small",
            PatrolPreferences.STANDARD to "Balanced (recommended)",
            PatrolPreferences.HIGH to "High quality"
        ).forEach { (quality, label) ->
            qualityGroup.addView(RadioButton(this).apply {
                id = quality
                text = label
                setTextColor(Color.BLACK)
                isChecked = PatrolPreferences.jpegQuality(this@SettingsActivity) == quality
                setOnClickListener { PatrolPreferences.setJpegQuality(this@SettingsActivity, quality) }
            })
        }
        body.addView(qualityGroup)
        body.addView(note("Size choice affects future compressed WhatsApp copies. Large originals are not automatically deleted."))

        body.addView(section("Storage and backup"))
        storageText = note("")
        body.addView(storageText)
        body.addView(button("Export shift photos to ZIP") { chooseExport() })
        body.addView(button("Delete verified large originals") { chooseCleanup() })
        body.addView(note("ZIP exports are not encrypted and cannot yet be imported into the app. Uninstalling deletes photos still stored privately inside the app."))

        val scroll = ScrollView(this).apply { addView(body) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)
        refreshStorage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportShift", exportShiftId)
        outState.putBoolean("originals", includeOriginals)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::storageText.isInitialized) refreshStorage()
    }

    private fun confirmEndShift() {
        val fresh = PatrolStore(this)
        val active = fresh.activeShift() ?: run {
            toast("No shift is currently active")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("End the current shift?")
            .setMessage(active.company + " — " + active.place +
                "\nYour photographs and patrol history will remain saved.")
            .setPositiveButton("End shift") { _, _ ->
                fresh.endShift()
                toast("Shift ended and saved")
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun lockCameraOnReturn() {
        if (PatrolStore(this).activeShift() == null) {
            toast("Start a shift on the Camera screen first")
            return
        }
        getSharedPreferences("camera_settings", MODE_PRIVATE)
            .edit().putBoolean("lock_when_returning", true).apply()
        finish()
    }

    private fun saveCompanyAndSite() {
        val company = companyField.text.toString().trim()
        val site = siteField.text.toString().trim()
        if (company.isEmpty() || site.isEmpty()) {
            toast("Enter a company and a site")
            return
        }
        PatrolPreferences.saveProfile(this, company, site)
        toast("Saved for your next shift")
    }

    private fun choosePreviousSite() {
        val sites = PatrolStore(this).shifts
            .map { it.company to it.place }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .distinct()
        if (sites.isEmpty()) {
            toast("No previous sites saved")
            return
        }
        AlertDialog.Builder(this).setTitle("Previous sites")
            .setItems(sites.map { it.first + " — " + it.second }.toTypedArray()) { _, i ->
                companyField.setText(sites[i].first)
                siteField.setText(sites[i].second)
                toast("Tap Save company and site to confirm")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun chooseExport() {
        if (exporting) { toast("An export is already running"); return }
        chooseShift("Export which shift?") { shift ->
            AlertDialog.Builder(this)
                .setTitle("Private ZIP export")
                .setMessage("The ZIP may contain confidential photographs and GPS coordinates. It is not encrypted.")
                .setPositiveButton("Small photos") { _, _ -> launchExport(shift, false) }
                .setNeutralButton("Include large originals") { _, _ -> launchExport(shift, true) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun launchExport(shift: PatrolShift, originals: Boolean) {
        exportShiftId = shift.id
        includeOriginals = originals
        createDocument.launch(ShiftArchive.suggestedFilename(shift))
    }

    private fun chooseCleanup() {
        chooseShift("Delete originals from which shift?") { shift ->
            if (shift.endedMs == null) {
                toast("End the shift before deleting its large originals")
                return@chooseShift
            }
            val candidates = shift.photos.filter { p ->
                p.originalPath != null &&
                    p.basePath?.let { PhotoProcessor.isValidJpeg(File(filesDir, it)) } == true &&
                    p.smallPath?.let { PhotoProcessor.isValidJpeg(File(filesDir, it)) } == true
            }
            if (candidates.isEmpty()) {
                toast("No verified large originals available")
                return@chooseShift
            }
            val bytes = candidates.sumOf { p ->
                File(filesDir, requireNotNull(p.originalPath)).length()
            }
            val mb = String.format(Locale.UK, "%.1f", bytes / 1_048_576.0)
            AlertDialog.Builder(this)
                .setTitle("Delete ${candidates.size} large originals?")
                .setMessage("Free approximately $mb MB. Compressed photos remain, but original detail will be irretrievably lost.")
                .setPositiveButton("Delete originals") { _, _ ->
                    val fresh = PatrolStore(this)
                    val target = fresh.shifts.firstOrNull { it.id == shift.id && it.endedMs != null }
                    if (target == null) {
                        toast("Shift changed. No deletion performed.")
                        return@setPositiveButton
                    }
                    var deleted = 0
                    target.photos.forEach { photo ->
                        val original = photo.originalPath ?: return@forEach
                        val base = photo.basePath ?: return@forEach
                        val small = photo.smallPath ?: return@forEach
                        val root = filesDir.canonicalFile
                        val file = File(root, original).canonicalFile
                        if (file.path.startsWith(root.path + File.separator + "patrols" + File.separator) &&
                            PhotoProcessor.isValidJpeg(File(root, base)) &&
                            PhotoProcessor.isValidJpeg(File(root, small)) &&
                            file.isFile && file.delete()
                        ) {
                            photo.originalPath = null
                            deleted++
                        }
                    }
                    fresh.save()
                    toast("Deleted $deleted originals; compressed photos are retained")
                    refreshStorage()
                }
                .setNegativeButton("Keep originals", null)
                .show()
        }
    }

    private fun chooseShift(title: String, onSelect: (PatrolShift) -> Unit) {
        val shifts = PatrolStore(this).shifts
        if (shifts.isEmpty()) { toast("No saved shifts yet"); return }
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("Europe/London")
        }
        AlertDialog.Builder(this).setTitle(title)
            .setItems(shifts.map { fmt.format(Date(it.startedMs)) + " — " + it.company + " / " + it.place }.toTypedArray()) { _, which ->
                onSelect(shifts[which])
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun refreshStorage() {
        if (!::storageText.isInitialized) return
        val shifts = PatrolStore(this).shifts
        val photos = shifts.flatMap { it.photos }
        val small = photos.sumOf { p -> p.smallPath?.let { File(filesDir, it).length() } ?: 0L }
        val large = photos.sumOf { p -> p.originalPath?.let { File(filesDir, it).length() } ?: 0L }
        val a = String.format(Locale.UK, "%.1f", small / 1_048_576.0)
        val b = String.format(Locale.UK, "%.1f", large / 1_048_576.0)
        storageText.text = "${photos.size} photos in ${shifts.size} shifts\nSmall copies: $a MB\nLarge originals: $b MB"
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun heading(label: String, size: Float): TextView = TextView(this).apply {
        text = label
        textSize = size
        setTextColor(Color.BLACK)
        setTypeface(null, Typeface.BOLD)
    }
    private fun section(label: String): TextView = heading(label, 16f).apply {
        setPadding(0, dp(20), 0, dp(6))
    }
    private fun note(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 13f
        setTextColor(Color.DKGRAY)
        setPadding(0, dp(5), 0, dp(10))
    }
    private fun button(label: String, clicked: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.BLACK)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), Color.BLACK)
            cornerRadius = dp(9).toFloat()
        }
        setOnClickListener { clicked() }
    }
    private fun dp(i: Int) = (i * resources.displayMetrics.density + 0.5f).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}

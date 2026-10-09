package uk.org.securitypatrol

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaActionSound
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * One-screen, low-friction patrol capture. The preview never closes between photos.
 * Compression and stamping run outside the camera callback thread.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var store: PatrolStore
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var gpsText: TextView
    private lateinit var shiftButton: Button
    private lateinit var nextPatrolButton: Button
    private lateinit var flashButton: Button
    private lateinit var torchButton: Button
    private lateinit var cameraTopBar: LinearLayout
    private lateinit var cameraShortcuts: LinearLayout
    private lateinit var shutterButton: Button
    private lateinit var lockedHint: TextView
    private var photoOnlyLock = false
    private var lockTouchInProgress = false
    private val unlockHandler = Handler(Looper.getMainLooper())
    private val unlockAction = Runnable {
        if (photoOnlyLock && lockTouchInProgress) {
            setPhotoOnlyLock(false)
            message("Camera controls unlocked")
        }
    }
    private var imageCapture: ImageCapture? = null
    private var boundCamera: Camera? = null
    private var torchEnabled = false
    private var torchChanging = false
    private var hasFlashUnit = false
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val imageProcessor = Executors.newSingleThreadExecutor()
    private lateinit var locationManager: LocationManager
    private var latestFix: Location? = null
    private var cameraStarted = false
    // Preloaded Android shutter sound; the camera stays on screen during capture.
    private val shutterSound = MediaActionSound()

    private val locationListener = LocationListener { location ->
        // Keep only the newest location update, across GPS and network providers.
        val previous = latestFix
        if (previous == null || location.elapsedRealtimeNanos >= previous.elapsedRealtimeNanos) {
            latestFix = location
            displayState()
        }
    }

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasCameraPermission()) startCamera() else message("Camera permission is needed for photography")
        startLocation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        shutterSound.load(MediaActionSound.SHUTTER_CLICK)
        // Remember the flash setting across app restarts and shifts. Start
        // from OFF on a new installation: predictable rapid photography.
        val savedFlash = getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
            .getInt("flash_mode", ImageCapture.FLASH_MODE_OFF)
        flashMode = when (savedFlash) {
            ImageCapture.FLASH_MODE_OFF,
            ImageCapture.FLASH_MODE_ON,
            ImageCapture.FLASH_MODE_AUTO -> savedFlash
            else -> ImageCapture.FLASH_MODE_OFF
        }
        store = PatrolStore(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        buildCameraScreen()
        displayState()
        if (savedInstanceState?.getBoolean("photo_only_lock") == true &&
            store.activeShift() != null
        ) {
            setPhotoOnlyLock(true)
        }
        // In this mode, the Android back gesture must not leave the camera.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (photoOnlyLock) {
                    message("Camera locked — hold TAKE PHOTO for 2 seconds to unlock")
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        if (hasCameraPermission()) {
            startCamera()
            startLocation()
        } else {
            permissions.launch(arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("photo_only_lock", photoOnlyLock)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            // Gallery may have changed the same private shift index.
            store = PatrolStore(this)
            displayState()
        }
        if (::locationManager.isInitialized) startLocation()
    }

    override fun onPause() {
        // An officer might switch to WhatsApp or lock the phone: do not leave
        // the phone torch running in the background.
        if (torchEnabled || torchChanging) {
            boundCamera?.cameraControl?.enableTorch(false)
            torchEnabled = false
            torchChanging = false
            updateTorchButton()
        }
        if (::locationManager.isInitialized) {
            try { locationManager.removeUpdates(locationListener) } catch (_: Exception) { }
        }
        super.onPause()
    }

    override fun onDestroy() {
        unlockHandler.removeCallbacks(unlockAction)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraExecutor.shutdown()
        imageProcessor.shutdown()
        shutterSound.release()
        super.onDestroy()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!PatrolPreferences.gpsEnabled(this)) {
            try { locationManager.removeUpdates(locationListener) } catch (_: Exception) {}
            latestFix = null
            if (::gpsText.isInitialized) displayState()
            return
        }
        if (!hasLocationPermission()) return
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 1500L, 1f, locationListener, Looper.getMainLooper())
                }
            } catch (_: Exception) {
                // Camera capture works even when location is unavailable or approximate.
            }
        }
    }

    private fun startCamera() {
        if (cameraStarted || !hasCameraPermission()) return
        cameraStarted = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flashMode)
                    .build()
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
                )
                imageCapture = capture
                boundCamera = camera
                hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                torchEnabled = false
                updateFlashButton()
                updateTorchButton()
                displayState()
            } catch (e: Exception) {
                cameraStarted = false
                boundCamera = null
                hasFlashUnit = false
                updateFlashButton()
                updateTorchButton()
                message("Camera could not start: " + e.message)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun buildCameraScreen() {
        val root = FrameLayout(this)
        // Android 15+ draws apps edge-to-edge: keep all controls above the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        cameraTopBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        val top = cameraTopBar
        status = TextView(this).apply {
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            maxLines = 2
        }
        gpsText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
        }
        top.addView(status)
        top.addView(gpsText)
        // Only the two essentials on the camera header: shift and patrol.
        val shiftRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shiftButton = button("Start shift") { chooseShiftAction() }
        nextPatrolButton = button("Next patrol") { nextPatrol() }.apply {
            textSize = 12f
        }
        shiftRow.addView(shiftButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        shiftRow.addView(nextPatrolButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        top.addView(shiftRow)
        val cameraOptions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        flashButton = button("Flash: Off") { chooseFlashMode() }.apply {
            textSize = 12f
            isEnabled = false // enabled once CameraX confirms a flash unit
        }
        torchButton = button("Torch: Off") { toggleTorch() }.apply {
            textSize = 12f
            isEnabled = false
        }
        cameraOptions.addView(
            flashButton, LinearLayout.LayoutParams(0, dp(42), 1f)
        )
        cameraOptions.addView(
            torchButton, LinearLayout.LayoutParams(0, dp(42), 1f)
        )
        top.addView(cameraOptions)
        root.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(14))
            setBackgroundColor(Color.WHITE)
        }
        lockedHint = TextView(this).apply {
            text = "CAMERA LOCKED  •  Tap to photograph  •  Hold PHOTO for 2 seconds to unlock"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.BLACK)
            visibility = View.GONE
            setPadding(dp(6), dp(4), dp(6), dp(8))
        }
        bottom.addView(lockedHint, LinearLayout.LayoutParams(-1, -2))
        // Small monochrome shutter; photo capture still happens without a
        // full-screen review or photo-processing delay.
        shutterButton = button("TAKE PHOTO") { takePhoto() }.apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.BLACK)
                cornerRadius = dp(12).toFloat()
            }
            contentDescription = "Take photo; in lock mode hold for two seconds to unlock controls"
        }
        shutterButton.setOnTouchListener { _, event ->
            if (!photoOnlyLock && !lockTouchInProgress) {
                false
            } else {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lockTouchInProgress = true
                        unlockHandler.removeCallbacks(unlockAction)
                        unlockHandler.postDelayed(unlockAction, 2_000L)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        // A long hold unlocks instead of creating an extra photo.
                        val stillLocked = photoOnlyLock
                        lockTouchInProgress = false
                        unlockHandler.removeCallbacks(unlockAction)
                        if (stillLocked) takePhoto()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        lockTouchInProgress = false
                        unlockHandler.removeCallbacks(unlockAction)
                        true
                    }
                    else -> true
                }
            }
        }
        bottom.addView(shutterButton, LinearLayout.LayoutParams(dp(166), dp(52)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4)
            bottomMargin = dp(8)
        })
        cameraShortcuts = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        cameraShortcuts.addView(
            button("Photos") { openGallery() },
            LinearLayout.LayoutParams(0, dp(49), 1f)
        )
        cameraShortcuts.addView(
            button("Settings") { openSettings() },
            LinearLayout.LayoutParams(0, dp(49), 1f)
        )
        cameraShortcuts.addView(
            button("Lock") {
                if (store.activeShift() == null) {
                    message("Start a shift before locking the camera")
                } else {
                    setPhotoOnlyLock(true)
                }
            },
            LinearLayout.LayoutParams(0, dp(49), 1f)
        )
        bottom.addView(cameraShortcuts, LinearLayout.LayoutParams(-1, -2))
        root.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
    }

    /** CameraX flash mode applies to subsequent photographs without rebinding. */
    private fun chooseFlashMode() {
        if (!hasFlashUnit || imageCapture == null) {
            message("Flash is not available on this camera")
            return
        }
        val options = arrayOf(
            "Off — no flash (best for fast photos)",
            "On — request flash with each photo",
            "Auto — flash when the phone decides"
        )
        val modes = intArrayOf(
            ImageCapture.FLASH_MODE_OFF,
            ImageCapture.FLASH_MODE_ON,
            ImageCapture.FLASH_MODE_AUTO
        )
        val currentIndex = modes.indexOf(flashMode).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Camera flash")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val newMode = modes[which]
                if (torchChanging) {
                    message("Torch is still switching. Try again.")
                } else if (torchEnabled) {
                    // Turn off continuous light before selecting a photo flash.
                    setTorch(false) {
                        applyFlashChoice(newMode)
                        dialog.dismiss()
                    }
                } else {
                    applyFlashChoice(newMode)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyFlashChoice(newMode: Int) {
        try {
            imageCapture?.setFlashMode(newMode)
            flashMode = newMode
            getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
                .edit().putInt("flash_mode", newMode).apply()
            updateFlashButton()
        } catch (e: Exception) {
            message("Could not change flash: " + e.message)
        }
    }

    private fun toggleTorch() {
        if (!hasFlashUnit || boundCamera == null) {
            message("The rear camera has no supported torch")
            return
        }
        if (torchChanging) return
        if (!torchEnabled && flashMode != ImageCapture.FLASH_MODE_OFF) {
            // The phone LED cannot reliably serve as a continuous torch and a
            // photographic flash simultaneously; turn photo flash OFF.
            applyFlashChoice(ImageCapture.FLASH_MODE_OFF)
        }
        setTorch(!torchEnabled)
    }

    private fun setTorch(enabled: Boolean, after: (() -> Unit)? = null) {
        val camera = boundCamera ?: run {
            message("Camera not ready")
            return
        }
        if (torchChanging) return
        torchChanging = true
        updateTorchButton()
        try {
            val future = camera.cameraControl.enableTorch(enabled)
            future.addListener({
                try {
                    // The future is already complete when this listener runs.
                    future.get()
                    torchEnabled = enabled
                    after?.invoke()
                } catch (e: Exception) {
                    message("Could not change torch: " + e.message)
                } finally {
                    torchChanging = false
                    updateTorchButton()
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            torchChanging = false
            updateTorchButton()
            message("Could not switch torch: " + e.message)
        }
    }

    private fun updateTorchButton() {
        if (!::torchButton.isInitialized) return
        torchButton.isEnabled = hasFlashUnit && boundCamera != null && !torchChanging
        torchButton.text = when {
            !hasFlashUnit -> "Torch: N/A"
            torchChanging -> "Torch: ..."
            torchEnabled -> "Torch: ON"
            else -> "Torch: Off"
        }
    }

    private fun updateFlashButton() {
        if (!::flashButton.isInitialized) return
        flashButton.isEnabled = hasFlashUnit && imageCapture != null
        flashButton.text = if (!hasFlashUnit) "Flash: unavailable" else when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> "Flash: On"
            ImageCapture.FLASH_MODE_AUTO -> "Flash: Auto"
            else -> "Flash: Off"
        }
    }

    /**
     * An in-app control lock; does NOT lock Android Home, notifications or
     * the phone's hardware buttons. The preview and photo shutter remain live.
     */
    private fun setPhotoOnlyLock(enabled: Boolean) {
        photoOnlyLock = enabled
        cameraTopBar.visibility = if (enabled) View.GONE else View.VISIBLE
        cameraShortcuts.visibility = if (enabled) View.GONE else View.VISIBLE
        lockedHint.visibility = if (enabled) View.VISIBLE else View.GONE
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            message("Camera locked. Tap to shoot; hold TAKE PHOTO for 2 seconds to unlock.")
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun button(title: String, onClick: () -> Unit): Button = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.BLACK)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), Color.BLACK)
            cornerRadius = dp(9).toFloat()
        }
        setPadding(dp(4), dp(4), dp(4), dp(4))
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun displayState() {
        if (!::status.isInitialized) return
        val shift = store.activeShift()
        if (shift == null) {
            val company = PatrolPreferences.company(this).ifBlank {
                store.shifts.firstOrNull()?.company.orEmpty()
            }
            val site = PatrolPreferences.site(this).ifBlank {
                store.shifts.firstOrNull()?.place.orEmpty()
            }
            status.text = if (company.isBlank() || site.isBlank()) {
                "Set your company and site in Settings"
            } else {
                company + "  •  " + site + "\nReady to start shift"
            }
        } else {
            val roundNumber = shift.rounds.indexOfFirst { it.id == shift.activeRoundId } + 1
            val currentPhotos = shift.photos.count { it.roundId == shift.activeRoundId }
            status.text = shift.company + "  •  " + shift.place + "\n" +
                "Patrol " + roundNumber.coerceAtLeast(1) + " — " +
                currentPhotos + " photos (" + shift.photos.size + " shift total)"
        }
        shiftButton.text = if (shift == null) "Start shift" else "End shift"
        nextPatrolButton.isEnabled = shift != null
        if (photoOnlyLock && shift == null) setPhotoOnlyLock(false)
        val fix = validFix()
        gpsText.text = when {
            !PatrolPreferences.gpsEnabled(this) -> "GPS off • Settings"
            fix == null -> "GPS: waiting for location • photos still work"
            else -> "GPS: " + String.format(java.util.Locale.UK, "%.6f, %.6f  ±%.0f m",
                fix.latitude, fix.longitude, if (fix.hasAccuracy()) fix.accuracy else 0f)
        }
    }

    private fun validFix(): Location? {
        if (!PatrolPreferences.gpsEnabled(this)) return null
        val loc = latestFix ?: return null
        val ageNs = SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos
        return if (ageNs in 0L..120_000_000_000L) loc else null
    }

    private fun chooseShiftAction() {
        val shift = store.activeShift()
        if (shift != null) {
            AlertDialog.Builder(this).setTitle("End this shift?")
                .setMessage("Your photographs and hourly patrols remain saved inside the app.")
                .setPositiveButton("End shift") { _, _ ->
                    store.endShift()
                    displayState()
                }
                .setNegativeButton("Cancel", null).show()
            return
        }
        val company = PatrolPreferences.company(this).ifBlank {
            store.shifts.firstOrNull()?.company.orEmpty()
        }
        val site = PatrolPreferences.site(this).ifBlank {
            store.shifts.firstOrNull()?.place.orEmpty()
        }
        if (company.isBlank() || site.isBlank()) {
            message("Enter your company and site in Settings first")
            openSettings()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Start shift?")
            .setMessage(company + "\n" + site + "\n\nHourly patrol grouping: " +
                if (PatrolPreferences.autoHourly(this)) "On" else "Off")
            .setPositiveButton("Start") { _, _ ->
                try {
                    if (PatrolPreferences.company(this).isBlank()) {
                        PatrolPreferences.saveProfile(this, company, site)
                    }
                    store.startShift(company, site, PatrolPreferences.autoHourly(this))
                    displayState()
                } catch (e: Exception) {
                    message(e.message ?: "Cannot start shift")
                }
            }
            .setNegativeButton("Settings") { _, _ -> openSettings() }
            .show()
    }

    private fun nextPatrol() {
        val shift = store.activeShift() ?: run { message("Start a shift first"); return }
        val round = store.currentRound()
        val count = shift.photos.count { it.roundId == round?.id }
        if (count == 0) {
            message("This patrol is still empty. Take photos before starting the next.")
            return
        }
        val nextNumber = shift.rounds.size + 1
        AlertDialog.Builder(this)
            .setTitle("Start Patrol " + nextNumber + "?")
            .setMessage("New photographs will be saved in Patrol " + nextNumber +
                ". Earlier patrol photographs stay in their own group.")
            .setPositiveButton("Start next patrol") { _, _ ->
                store.startNextRound()
                displayState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun takePhoto() {
        val shift = store.activeShift() ?: run { message("Start a shift before taking photos"); return }
        val capture = imageCapture ?: run { message("Camera is not ready"); return }
        val captureMs = System.currentTimeMillis()
        // Assign the patrol round at shutter time, not later during processing.
        // Automatically opens a fresh round on the next picture after an hourly
        // cycle and a break; manual Next Patrol works even earlier.
        val round = store.roundForCapture(captureMs)
        val fix = validFix()
        val ageSeconds = fix?.let { max(0L, (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000_000L) }
        val photoId = UUID.randomUUID().toString()
        val relative = "patrols/" + shift.id + "/original/" + photoId + ".jpg"
        val output = File(filesDir, relative).apply { parentFile?.mkdirs() }
        val photo = PatrolPhoto(
            id = photoId, shiftId = shift.id, timeMs = captureMs,
            place = shift.place, lat = fix?.latitude, lon = fix?.longitude,
            accuracyMetres = fix?.takeIf { it.hasAccuracy() }?.accuracy,
            gpsAgeSeconds = ageSeconds, originalPath = relative,
            roundId = round.id
        )
        try {
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(output).build(),
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        try {
                            store.addPhoto(photo)
                        } catch (e: Exception) {
                            android.util.Log.e("PatrolCamera", "Failed to index original", e)
                        }
                        imageProcessor.execute {
                            try {
                                val (base, small) = PhotoProcessor.process(this@MainActivity, photo, shift.company)
                                store.processed(photo.id, base, small)
                                runOnUiThread { displayState() }
                            } catch (e: Exception) {
                                android.util.Log.e("PatrolCamera", "Photo processing failed; original retained", e)
                                runOnUiThread { message("A photo could not be compressed. The original is kept.") }
                            }
                        }
                        runOnUiThread { displayState() }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        output.delete()
                        runOnUiThread { message("Photo failed: " + exception.message) }
                    }
                }
            )
            // An immediate, short audible click on each accepted shutter press,
            // including the phone's volume-key shortcut. No processing delay.
            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        } catch (e: Exception) {
            message("Unable to capture: " + e.message)
        }
    }

    private fun openGallery() {
        startActivity(Intent(this, GalleryActivity::class.java))
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (event?.repeatCount == 0) takePhoto()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
}

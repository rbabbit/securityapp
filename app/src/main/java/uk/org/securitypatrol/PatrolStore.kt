package uk.org.securitypatrol

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PatrolPhoto(
    val id: String,
    val shiftId: String,
    val timeMs: Long,
    var place: String,
    val lat: Double?,
    val lon: Double?,
    val accuracyMetres: Float?,
    val gpsAgeSeconds: Long?,
    var originalPath: String?,
    var basePath: String? = null,
    var smallPath: String? = null,
    var confirmedSent: Boolean = false,
    // The patrol round this photo belongs to (kept across app restarts).
    var roundId: String? = null
)

data class PatrolRound(
    val id: String,
    val startedMs: Long,
    var endedMs: Long? = null
)

data class PatrolShift(
    val id: String,
    val company: String,
    var place: String,
    val startedMs: Long,
    var endedMs: Long? = null,
    val photos: MutableList<PatrolPhoto> = mutableListOf(),
    val rounds: MutableList<PatrolRound> = mutableListOf(),
    var activeRoundId: String? = null,
    var autoHourly: Boolean = true
)

class PatrolStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "patrol_records.json"))
    val shifts: MutableList<PatrolShift> = mutableListOf()
    var activeId: String? = null
        private set
    var pendingShareIds: List<String> = emptyList()
        private set

    init { read() }

    @Synchronized
    fun activeShift(): PatrolShift? = shifts.firstOrNull { it.id == activeId && it.endedMs == null }

    @Synchronized
    fun startShift(company: String, place: String, autoHourly: Boolean = true): PatrolShift {
        check(activeShift() == null) { "End the current shift first" }
        val now = System.currentTimeMillis()
        val shift = PatrolShift(
            UUID.randomUUID().toString(), company.trim(), place.trim(),
            now, autoHourly = autoHourly
        )
        val initialRound = PatrolRound(UUID.randomUUID().toString(), now)
        shift.rounds.add(initialRound)
        shift.activeRoundId = initialRound.id
        shifts.add(0, shift)
        activeId = shift.id
        save()
        return shift
    }

    @Synchronized
    fun endShift() {
        activeShift()?.let { shift ->
            val now = System.currentTimeMillis()
            shift.endedMs = now
            shift.rounds.firstOrNull { it.id == shift.activeRoundId }?.endedMs = now
            shift.activeRoundId = null
        }
        activeId = null
        save()
    }

    @Synchronized
    fun currentRound(): PatrolRound? {
        val shift = activeShift() ?: return null
        return shift.rounds.firstOrNull { it.id == shift.activeRoundId }
    }

    /**
     * An officer can always manually begin a new round.
     * Do not create repeated empty rounds from accidental taps.
     */
    @Synchronized
    fun startNextRound(): PatrolRound {
        val shift = activeShift() ?: error("Start a shift first")
        val existing = currentRound()
        if (existing != null && shift.photos.none { it.roundId == existing.id }) return existing
        val now = System.currentTimeMillis()
        existing?.endedMs = now
        val next = PatrolRound(UUID.randomUUID().toString(), now)
        shift.rounds.add(next)
        shift.activeRoundId = next.id
        save()
        return next
    }

    /**
     * Auto-split only after a roughly hourly cycle PLUS a real quiet break.
     * A patrol at 10:55-11:10 must stay in one round across the clock hour.
     * Check only on shutter press, no timers or background location needed.
     */
    @Synchronized
    fun roundForCapture(captureMs: Long): PatrolRound {
        val shift = activeShift() ?: error("Start a shift first")
        var round = currentRound()
        if (round == null) {
            round = PatrolRound(UUID.randomUUID().toString(), captureMs)
            shift.rounds.add(round)
            shift.activeRoundId = round.id
            save()
        }
        val captures = shift.photos.filter { it.roundId == round.id }
        if (shift.autoHourly && captures.isNotEmpty()) {
            val first = captures.minOf { it.timeMs }
            val last = captures.maxOf { it.timeMs }
            val elapsed = captureMs - first
            val idle = captureMs - last
            if (elapsed >= 50 * 60_000L && idle >= 15 * 60_000L) {
                round.endedMs = captureMs
                round = PatrolRound(UUID.randomUUID().toString(), captureMs)
                shift.rounds.add(round)
                shift.activeRoundId = round.id
                save()
            }
        }
        return round
    }

    @Synchronized
    fun setActivePlace(value: String) {
        activeShift()?.place = value.trim()
        save()
    }

    @Synchronized
    fun addPhoto(photo: PatrolPhoto) {
        shifts.firstOrNull { it.id == photo.shiftId }?.let { shift ->
            if (photo.roundId == null) photo.roundId = shift.activeRoundId ?: shift.rounds.lastOrNull()?.id
            shift.photos.add(photo)
        }
        save()
    }

    @Synchronized
    fun findPhoto(id: String): PatrolPhoto? =
        shifts.asSequence().flatMap { it.photos.asSequence() }.firstOrNull { it.id == id }

    @Synchronized
    fun processed(id: String, base: String, small: String) {
        findPhoto(id)?.let { it.basePath = base; it.smallPath = small }
        save()
    }

    @Synchronized
    fun updatePlace(id: String, place: String) {
        findPhoto(id)?.place = place.trim()
        save()
    }

    @Synchronized
    fun clearOriginal(id: String) {
        findPhoto(id)?.originalPath = null
        save()
    }

    @Synchronized
    fun noteShareAttempt(ids: List<String>) {
        pendingShareIds = ids
        save()
    }

    @Synchronized
    fun confirmShare() {
        pendingShareIds.forEach { id -> findPhoto(id)?.confirmedSent = true }
        pendingShareIds = emptyList()
        save()
    }

    @Synchronized
    fun clearPendingShare() {
        pendingShareIds = emptyList()
        save()
    }

    @Synchronized
    fun save() {
        val document = JSONObject()
        document.put("version", 2)
        document.put("activeId", activeId ?: JSONObject.NULL)
        document.put("pendingShareIds", JSONArray(pendingShareIds))
        val items = JSONArray()
        shifts.forEach { shift ->
            val sj = JSONObject()
                .put("id", shift.id).put("company", shift.company).put("place", shift.place)
                .put("startedMs", shift.startedMs)
                .put("endedMs", shift.endedMs ?: JSONObject.NULL)
                .put("activeRoundId", shift.activeRoundId ?: JSONObject.NULL)
                .put("autoHourly", shift.autoHourly)
            val rounds = JSONArray()
            shift.rounds.forEach { r ->
                rounds.put(
                    JSONObject().put("id", r.id)
                        .put("startedMs", r.startedMs)
                        .put("endedMs", r.endedMs ?: JSONObject.NULL)
                )
            }
            sj.put("rounds", rounds)
            val photos = JSONArray()
            shift.photos.forEach { p ->
                photos.put(
                    JSONObject().put("id", p.id).put("shiftId", p.shiftId)
                        .put("timeMs", p.timeMs).put("place", p.place)
                        .put("lat", p.lat ?: JSONObject.NULL).put("lon", p.lon ?: JSONObject.NULL)
                        .put("accuracyMetres", p.accuracyMetres ?: JSONObject.NULL)
                        .put("gpsAgeSeconds", p.gpsAgeSeconds ?: JSONObject.NULL)
                        .put("originalPath", p.originalPath ?: JSONObject.NULL)
                        .put("basePath", p.basePath ?: JSONObject.NULL)
                        .put("smallPath", p.smallPath ?: JSONObject.NULL)
                        .put("confirmedSent", p.confirmedSent)
                        .put("roundId", p.roundId ?: JSONObject.NULL)
                )
            }
            items.put(sj.put("photos", photos))
        }
        document.put("shifts", items)
        val bytes = document.toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }

    private fun read() {
        if (!file.baseFile.exists()) return
        try {
            val document = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            activeId = document.optString("activeId").takeUnless { it.isBlank() || it == "null" }
            val pending = document.optJSONArray("pendingShareIds") ?: JSONArray()
            pendingShareIds = (0 until pending.length()).map { pending.getString(it) }
            val items = document.optJSONArray("shifts") ?: JSONArray()
            for (index in 0 until items.length()) {
                val sj = items.getJSONObject(index)
                val shift = PatrolShift(
                    sj.getString("id"), sj.getString("company"),
                    sj.optString("place"), sj.getLong("startedMs"),
                    if (sj.isNull("endedMs")) null else sj.optLong("endedMs"),
                    autoHourly = sj.optBoolean("autoHourly", true)
                )
                shift.activeRoundId = sj.pathOrNull("activeRoundId")
                val rounds = sj.optJSONArray("rounds") ?: JSONArray()
                for (r in 0 until rounds.length()) {
                    val entry = rounds.getJSONObject(r)
                    shift.rounds.add(PatrolRound(
                        entry.getString("id"),
                        entry.getLong("startedMs"),
                        if (entry.isNull("endedMs")) null else entry.optLong("endedMs")
                    ))
                }
                val photos = sj.optJSONArray("photos") ?: JSONArray()
                for (j in 0 until photos.length()) {
                    val p = photos.getJSONObject(j)
                    shift.photos.add(PatrolPhoto(
                        id = p.getString("id"),
                        shiftId = p.getString("shiftId"),
                        timeMs = p.getLong("timeMs"),
                        place = p.optString("place"),
                        lat = if (p.isNull("lat")) null else p.getDouble("lat"),
                        lon = if (p.isNull("lon")) null else p.getDouble("lon"),
                        accuracyMetres = if (p.isNull("accuracyMetres")) null else p.getDouble("accuracyMetres").toFloat(),
                        gpsAgeSeconds = if (p.isNull("gpsAgeSeconds")) null else p.getLong("gpsAgeSeconds"),
                        originalPath = p.pathOrNull("originalPath"),
                        basePath = p.pathOrNull("basePath"),
                        smallPath = p.pathOrNull("smallPath"),
                        confirmedSent = p.optBoolean("confirmedSent", false),
                        roundId = p.pathOrNull("roundId")
                    ))
                }
                // Upgrade existing 0.1.1 shifts without discarding or reordering
                // any photo or changing its capture time or storage path.
                if (shift.rounds.isEmpty()) {
                    val timestamp = shift.photos.minOfOrNull { it.timeMs } ?: shift.startedMs
                    val legacyRound = PatrolRound(
                        UUID.randomUUID().toString(), timestamp, shift.endedMs
                    )
                    shift.rounds.add(legacyRound)
                }
                val known = shift.rounds.map { it.id }.toSet()
                shift.photos.forEach { photo ->
                    if (photo.roundId !in known) photo.roundId = shift.rounds.first().id
                }
                if (shift.endedMs == null) {
                    if (shift.activeRoundId !in known) {
                        shift.activeRoundId = shift.rounds.last().id
                    }
                } else {
                    shift.activeRoundId = null
                }
                shifts.add(shift)
            }
            if (activeShift() == null) activeId = null
        } catch (e: Exception) {
            android.util.Log.e("PatrolStore", "Unable to read previous shifts", e)
            // Keep the source file in place for recovery. Never overwrite it here.
        }
    }
}

private fun JSONObject.pathOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

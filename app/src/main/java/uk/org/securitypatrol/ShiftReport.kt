package uk.org.securitypatrol

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generate an officer-controlled text report for a single shift.
 * This does not contact WhatsApp, a server or any external service.
 */
object ShiftReport {

    private fun stamp(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.UK).apply {
            timeZone = TimeZone.getTimeZone("Europe/London")
        }.format(Date(timestamp))

    fun build(shift: PatrolShift): String = buildString {
        appendLine("SECURITY PATROL SHIFT REPORT")
        appendLine("Company: " + shift.company)
        appendLine("Site: " + shift.place)
        appendLine("Shift started: " + stamp(shift.startedMs))
        appendLine("Shift ended: " + (shift.endedMs?.let(::stamp) ?: "Still active"))
        appendLine("Patrol rounds: " + shift.rounds.size)
        appendLine("Photographs: " + shift.photos.size)
        appendLine("Manually confirmed sent: " + shift.photos.count { it.confirmedSent })
        appendLine()

        shift.rounds.forEachIndexed { index, round ->
            val pictures = shift.photos.filter { it.roundId == round.id }.sortedBy { it.timeMs }
            appendLine("PATROL " + (index + 1))
            appendLine("Started: " + stamp(round.startedMs))
            appendLine("Ended: " + (round.endedMs?.let(::stamp) ?: "Not recorded"))
            appendLine("Photos: " + pictures.size)
            appendLine("Marked sent: " + pictures.count { it.confirmedSent })
            if (pictures.isNotEmpty()) {
                appendLine("First photo: " + stamp(pictures.first().timeMs))
                appendLine("Last photo: " + stamp(pictures.last().timeMs))
            }
            if (round.notes.isNotBlank()) {
                appendLine("Officer notes:")
                appendLine(round.notes.trim())
            }
            appendLine()
        }
        appendLine("Note: 'Marked sent' reflects manual confirmation in the app,")
        appendLine("not independent verification of WhatsApp delivery.")
    }

    fun show(activity: Activity, shift: PatrolShift) {
        val report = build(shift)
        AlertDialog.Builder(activity)
            .setTitle("Shift report")
            .setMessage(report)
            .setPositiveButton("Share report") { _, _ ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                try {
                    activity.startActivity(Intent.createChooser(intent, "Share shift report"))
                } catch (e: Exception) {
                    Toast.makeText(activity, "Unable to share report: " + e.message, Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Copy text") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Security patrol shift report", report))
                Toast.makeText(activity, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}

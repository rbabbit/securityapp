package uk.org.securitypatrol

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Opt-in, short-lived WhatsApp chat-title *experiment*.
 *
 * This object never retrieves chat messages. It keeps only a tentative title
 * exposed by a known WhatsApp accessibility view ID. Confirmation is manual.
 */
object WhatsAppTitleHelper {
    private const val PREFS = "whatsapp_title_helper_test"
    private const val CONSENT = "consent"
    private const val UNTIL = "until_ms"
    private const val PACKAGE = "watched_package"
    private const val CANDIDATE = "candidate_name"
    private const val CONFIRMED = "confirmed_name"
    private const val STATUS = "state"
    private const val DURATION_MS = 120_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun grantLocalConsent(context: Context) {
        prefs(context).edit().putBoolean(CONSENT, true).apply()
    }

    fun hasConsent(context: Context) = prefs(context).getBoolean(CONSENT, false)

    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val targetName = WhatsAppTitleAccessibilityService::class.java.name
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { entry ->
                val service = entry.resolveInfo.serviceInfo
                service.packageName == context.packageName &&
                    (service.name == targetName ||
                        service.name.endsWith(".WhatsAppTitleAccessibilityService"))
            }
    }

    fun armIfEnabled(context: Context, selectedPackage: String) {
        if (selectedPackage != "com.whatsapp" && selectedPackage != "com.whatsapp.w4b") return
        if (!hasConsent(context) || !isServiceEnabled(context)) return
        prefs(context).edit()
            .remove(CANDIDATE)
            .putString(PACKAGE, selectedPackage)
            .putLong(UNTIL, System.currentTimeMillis() + DURATION_MS)
            .putString(STATUS, "Watching WhatsApp for up to two minutes")
            .apply()
    }

    /** A non-null return means there is an explicit, still-valid share session. */
    fun activePackage(context: Context): String? {
        if (!hasConsent(context)) return null
        val pref = prefs(context)
        val deadline = pref.getLong(UNTIL, 0L)
        val now = System.currentTimeMillis()
        if (deadline == 0L) return null
        if (deadline < now || deadline > now + DURATION_MS) {
            cancelPending(context)
            if (pref.getString(CANDIDATE, null) == null) {
                pref.edit().putString(STATUS, "No recognised WhatsApp title in the test window").apply()
            }
            return null
        }
        return pref.getString(PACKAGE, null)
            ?.takeIf { it == "com.whatsapp" || it == "com.whatsapp.w4b" }
    }

    fun noteWhatsAppOpened(context: Context) {
        val pref = prefs(context)
        if (pref.getString(STATUS, null) == "Watching WhatsApp for up to two minutes") {
            pref.edit().putString(STATUS, "WhatsApp opened; waiting for a recognised chat title").apply()
        }
    }

    fun noteCandidate(context: Context, raw: String) {
        if (activePackage(context) == null) return
        val label = raw.trim().replace(Regex("[\\r\\n]+"), " ")
        if (label.length !in 2..90) return
        val excluded = setOf("whatsapp", "whatsapp business", "chats", "send to",
            "forward to", "status", "calls", "updates", "communities", "new chat")
        if (label.lowercase(java.util.Locale.ROOT) in excluded) return
        if (label.all { it.isDigit() || it.isWhitespace() || it == '+' }) return
        val pref = prefs(context)
        if (pref.getString(CANDIDATE, null) != label) {
            pref.edit()
                .putString(CANDIDATE, label)
                .putString(STATUS, "Possible chat title found; confirm in Settings")
                .apply()
        }
    }

    fun finishOnReturn(context: Context): String? {
        val pref = prefs(context)
        if (pref.getLong(UNTIL, 0L) == 0L) return null
        val name = pref.getString(CANDIDATE, null)
        cancelPending(context)
        val newStatus = if (name.isNullOrBlank()) {
            "WhatsApp opened but no recognisable chat title appeared"
        } else {
            "Possible chat title detected; review and confirm it"
        }
        pref.edit().putString(STATUS, newStatus).apply()
        return if (name == null) "Helper test: no title detected. See Settings."
            else "Helper saw possible chat '$name'. Confirm in Settings."
    }

    fun cancelPending(context: Context) {
        prefs(context).edit().remove(UNTIL).remove(PACKAGE).apply()
    }

    fun candidate(context: Context) = prefs(context).getString(CANDIDATE, null)
    fun remembered(context: Context) = prefs(context).getString(CONFIRMED, null)
    fun status(context: Context) = prefs(context).getString(STATUS, "").orEmpty()

    fun confirmCandidate(context: Context) {
        val possible = candidate(context) ?: return
        prefs(context).edit().putString(CONFIRMED, possible).apply()
    }

    fun disableAndForget(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

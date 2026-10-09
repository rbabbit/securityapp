package uk.org.securitypatrol

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Narrow proof-of-concept: inspect only named WhatsApp chat-title UI nodes.
 *
 * Not a conversation recorder. Does not read message content, capture a screen,
 * use gestures, open chats, intercept Send, or keep a background event log.
 */
class WhatsAppTitleAccessibilityService : AccessibilityService() {
    private var lastInspectionMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val targetPackage = WhatsAppTitleHelper.activePackage(this) ?: return
        if (event?.packageName?.toString() != targetPackage) return
        val kind = event.eventType
        if (kind != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            kind != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            kind != AccessibilityEvent.TYPE_VIEW_CLICKED
        ) return

        WhatsAppTitleHelper.noteWhatsAppOpened(this)
        val now = SystemClock.elapsedRealtime()
        if (now - lastInspectionMs < 160L) return
        lastInspectionMs = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != targetPackage) return

        // This is a historically exposed WhatsApp *conversation header* field.
        // A changed WhatsApp UI may mean no result; never fall back to scanning
        // message bodies, contacts or the entire visible text hierarchy.
        val ids = arrayOf(
            "$targetPackage:id/conversation_contact_name",
            "$targetPackage:id/recipient_name"
        )
        for (id in ids) {
            val matches = try { root.findAccessibilityNodeInfosByViewId(id) }
                catch (_: Exception) { emptyList() }
            for (node in matches) {
                if (!node.isVisibleToUser || node.isPassword) continue
                val title = node.text?.toString().orEmpty()
                if (title.isNotBlank()) {
                    WhatsAppTitleHelper.noteCandidate(this, title)
                    return
                }
            }
        }
    }

    override fun onInterrupt() {
        // Deliberately no action and no retained event data.
    }
}

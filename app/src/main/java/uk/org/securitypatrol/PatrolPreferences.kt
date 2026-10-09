package uk.org.securitypatrol

import android.content.Context

/**
 * Simple, locally saved settings for the Camera / Photos / Settings interface.
 * They affect NEW shifts and NEW pictures; old evidence stays unchanged.
 */
object PatrolPreferences {
    private const val PREFS = "patrol_simple_settings"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun company(context: Context): String =
        prefs(context).getString("company", "").orEmpty().trim()

    fun site(context: Context): String =
        prefs(context).getString("site", "").orEmpty().trim()

    fun saveProfile(context: Context, company: String, site: String) {
        prefs(context).edit()
            .putString("company", company.trim())
            .putString("site", site.trim())
            .apply()
    }

    fun gpsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("gps_enabled", true)

    fun setGpsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("gps_enabled", enabled).apply()
    }

    fun autoHourly(context: Context): Boolean =
        prefs(context).getBoolean("auto_hourly", true)

    fun setAutoHourly(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("auto_hourly", enabled).apply()
    }

    // JPEG quality for the smaller stamped copies, not the source evidence.
    const val SMALL = 64
    const val STANDARD = 79
    const val HIGH = 90

    fun jpegQuality(context: Context): Int {
        val saved = prefs(context).getInt("jpeg_quality", STANDARD)
        return if (saved in listOf(SMALL, STANDARD, HIGH)) saved else STANDARD
    }

    fun setJpegQuality(context: Context, value: Int) {
        require(value in listOf(SMALL, STANDARD, HIGH))
        prefs(context).edit().putInt("jpeg_quality", value).apply()
    }
}

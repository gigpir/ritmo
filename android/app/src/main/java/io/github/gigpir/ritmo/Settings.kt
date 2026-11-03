package io.github.gigpir.ritmo

import android.content.Context
import androidx.core.content.edit

/**
 * The three switches of Settings, all on by default, and what Ritmo remembers about
 * asking for a donation. All of it stays on the phone.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var voice: Boolean
        get() = prefs.getBoolean("voice", true)
        set(value) = prefs.edit { putBoolean("voice", value) }

    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(value) = prefs.edit { putBoolean("vibration", value) }

    var duckMusic: Boolean
        get() = prefs.getBoolean("duckMusic", true)
        set(value) = prefs.edit { putBoolean("duckMusic", value) }

    /** After "I already donated", Ritmo never asks again. */
    var hasDonated: Boolean
        get() = prefs.getBoolean("hasDonated", false)
        set(value) = prefs.edit { putBoolean("hasDonated", value) }

    /** When Ritmo last asked for a donation; at first, when it was first opened. */
    private var askedAt: Long
        get() = prefs.getLong("donationAskedAt", 0)
        set(value) = prefs.edit { putLong("donationAskedAt", value) }

    init {
        if (!prefs.contains("donationAskedAt")) askedAt = System.currentTimeMillis()
    }

    /** At the end of a completed workout, at most once a week, the first time a week after installing. */
    fun shouldAskForDonation(now: Long = System.currentTimeMillis()): Boolean =
        Donation.URL.isNotEmpty() && !hasDonated && now - askedAt >= WEEK

    fun didAskForDonation(now: Long = System.currentTimeMillis()) {
        askedAt = now
    }

    private companion object {
        const val WEEK = 7 * 24 * 60 * 60 * 1000L
    }
}

/** Where the Donate button leads. While it's empty, Ritmo never asks. */
object Donation {
    const val URL = ""
}

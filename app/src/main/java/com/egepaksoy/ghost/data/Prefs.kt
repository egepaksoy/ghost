package com.egepaksoy.ghost.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("ghost", Context.MODE_PRIVATE)

    /** The user wants recording to be on (survives reboots and app restarts). */
    var tracking: Boolean
        get() = sp.getBoolean("tracking", false)
        set(v) = sp.edit().putBoolean("tracking", v).apply()

    /** Seconds between records while moving. */
    var movingSec: Int
        get() = sp.getInt("movingSec", 60)
        set(v) = sp.edit().putInt("movingSec", v).apply()

    /** Seconds between records while staying in one place. */
    var stationarySec: Int
        get() = sp.getInt("stationarySec", 300)
        set(v) = sp.edit().putInt("stationarySec", v).apply()
}

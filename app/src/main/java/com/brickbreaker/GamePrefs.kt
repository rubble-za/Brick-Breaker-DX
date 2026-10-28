package com.brickbreaker

import android.content.Context

/** The high score and the sound/vibration switches, kept in SharedPreferences. */
class GamePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("brick_breaker", Context.MODE_PRIVATE)

    var highScore: Int
        get() = prefs.getInt("high_score", 0)
        set(value) = prefs.edit().putInt("high_score", value).apply()

    var soundOn: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) = prefs.edit().putBoolean("sound", value).apply()

    var hapticsOn: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()
}

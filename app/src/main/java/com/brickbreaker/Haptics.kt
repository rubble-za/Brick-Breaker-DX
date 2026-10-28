package com.brickbreaker

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.brickbreaker.engine.GameEvent
import java.util.concurrent.Executors

/** Short vibrations for the events that deserve a physical cue; the frequent ones (walls, bricks) stay silent. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    // Vibrator calls are binder transactions; keep them off the game loop like the audio.
    private val worker = Executors.newSingleThreadExecutor()

    fun play(event: GameEvent) {
        val (ms, amplitude) = when (event) {
            GameEvent.PADDLE_HIT -> 10L to 60
            GameEvent.BRICK_BREAK -> 15L to 100
            GameEvent.POWERUP -> 30L to 120
            GameEvent.STICK -> 15L to 80
            GameEvent.LIFE_LOST -> 150L to 255
            GameEvent.LEVEL_CLEAR -> 60L to 150
            GameEvent.GAME_OVER -> 250L to 255
            else -> return
        }
        worker.execute {
            val v = vibrator?.takeIf { it.hasVibrator() } ?: return@execute
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
        }
    }

    fun release() = worker.shutdown()
}

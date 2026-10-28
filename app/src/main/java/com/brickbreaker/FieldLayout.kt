package com.brickbreaker

import com.brickbreaker.engine.GameEngine
import kotlin.math.max
import kotlin.math.min

/**
 * Where the HUD panel, play field and control panel sit on a screen of any size.
 *
 * The field keeps its 360:640 shape and is as large as fits between a top panel and a bottom panel of
 * at least [minTopPx] and [minBottomPx]. Any height left over (tall phones) goes to the two panels, mostly to
 * the bottom one where the thumb is, so the play area never changes shape.
 */
class FieldLayout(
    /** Screen pixels per field unit. */
    val scale: Float,
    /** Left edge of the field in screen pixels. */
    val left: Float,
    /** Top edge of the field in screen pixels, which is the height of the top panel. */
    val top: Float,
    val fieldHeight: Float,
    val bottomHeight: Float,
) {
    val fieldWidth get() = GameEngine.WIDTH * scale
    val fieldBottom get() = top + fieldHeight

    companion object {
        /** Share of the spare height that goes to the top panel; the rest goes to the bottom one. */
        private const val TOP_SHARE = 0.35f

        fun compute(width: Float, height: Float, minTopPx: Float, minBottomPx: Float): FieldLayout {
            // On a very small screen, shrink both panels so the field still gets at least 55% of the height.
            val panelBudget = height * 0.45f
            val minSum = minTopPx + minBottomPx
            val k = if (minSum > panelBudget) panelBudget / minSum else 1f
            val minTop = minTopPx * k
            val minBottom = minBottomPx * k

            val scale = max(
                0.01f,
                min(width / GameEngine.WIDTH, (height - minTop - minBottom) / GameEngine.HEIGHT),
            )
            val fieldHeight = GameEngine.HEIGHT * scale
            val spare = max(0f, height - fieldHeight - minTop - minBottom)
            val top = minTop + spare * TOP_SHARE
            return FieldLayout(
                scale = scale,
                left = (width - GameEngine.WIDTH * scale) / 2f,
                top = top,
                fieldHeight = fieldHeight,
                bottomHeight = height - top - fieldHeight,
            )
        }
    }
}

package com.brickbreaker

import com.brickbreaker.engine.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldLayoutTest {
    private val aspect = GameEngine.WIDTH / GameEngine.HEIGHT

    private fun check(w: Float, h: Float, minTop: Float, minBottom: Float): FieldLayout {
        val l = FieldLayout.compute(w, h, minTop, minBottom)
        assertTrue("field must fit width", l.fieldWidth <= w + 0.5f)
        assertEquals("field keeps its aspect", aspect, l.fieldWidth / l.fieldHeight, 0.001f)
        assertEquals("panels and field fill the screen", h, l.top + l.fieldHeight + l.bottomHeight, 0.5f)
        assertEquals("field is centred", (w - l.fieldWidth) / 2f, l.left, 0.5f)
        assertTrue("panels never have negative height", l.top >= 0f && l.bottomHeight >= 0f)
        return l
    }

    @Test
    fun tallPhoneGivesSpareHeightToThePanelsNotTheField() {
        // 20:9 phone at 3x density
        val l = check(1080f, 2600f, 200f, 240f)
        assertEquals(3f, l.scale, 0.001f) // limited by width
        assertTrue(l.top > 200f && l.bottomHeight > 240f)
        assertTrue("thumb-side panel gets the larger share", l.bottomHeight - 240f > l.top - 200f)
    }

    @Test
    fun fieldSizeIsIndependentOfSpareHeight() {
        val short = check(1080f, 2400f, 200f, 240f)
        val tall = check(1080f, 2800f, 200f, 240f)
        assertEquals(short.fieldHeight, tall.fieldHeight, 0.5f)
    }

    @Test
    fun squarishScreenShrinksFieldAndKeepsMinimumPanels() {
        val l = check(1200f, 1600f, 250f, 300f)
        assertEquals(250f, l.top, 0.5f)
        assertEquals(300f, l.bottomHeight, 0.5f)
        assertTrue(l.left > 0f)
    }

    @Test
    fun smallLowDpiScreenStillWorks() {
        check(320f, 480f, 90f, 100f)
    }

    @Test
    fun tinyScreenCapsPanelsSoFieldKeepsMostOfTheHeight() {
        val l = check(240f, 320f, 200f, 200f)
        assertTrue(l.fieldHeight >= 320f * 0.55f - 0.5f)
    }
}

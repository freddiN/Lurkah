package com.lurkah.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayButtonsTest {

    @Test
    fun constants_areDistinct() {
        val all = setOf(
            OverlayButtonsPosition.TOP_END,
            OverlayButtonsPosition.BOTTOM_START,
            OverlayButtonsPosition.BOTTOM_END
        )
        assertEquals(3, all.size)
        assertEquals(all, OverlayButtonsPosition.ALL)
    }

    @Test
    fun default_isTopEnd() {
        assertEquals("top_end", OverlayButtonsPosition.TOP_END)
    }

    @Test
    fun bottomDetection() {
        assertEquals(false, isBottomButtonsPosition(OverlayButtonsPosition.TOP_END))
        assertEquals(true, isBottomButtonsPosition(OverlayButtonsPosition.BOTTOM_START))
        assertEquals(true, isBottomButtonsPosition(OverlayButtonsPosition.BOTTOM_END))
        assertEquals(false, isBottomButtonsPosition("nonsense"))
    }
}

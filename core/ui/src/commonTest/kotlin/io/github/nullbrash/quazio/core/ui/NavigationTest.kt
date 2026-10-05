package io.github.nullbrash.quazio.core.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationTest {

    @Test
    fun narrowScreenGetsBottomBar() {
        assertEquals(NavLayout.BOTTOM_BAR, navLayoutFor(360.dp))
        assertEquals(NavLayout.BOTTOM_BAR, navLayoutFor(599.dp))
    }

    @Test
    fun wideScreenGetsSideRail() {
        assertEquals(NavLayout.SIDE_RAIL, navLayoutFor(600.dp))
        assertEquals(NavLayout.SIDE_RAIL, navLayoutFor(1000.dp))
    }
}

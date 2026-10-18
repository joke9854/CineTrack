package com.cinetrack

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextWidgetRowsTest {
    @Test
    fun oneCellTallShowsOneRow() {
        assertEquals(1, upNextWidgetRows(DpSize(250.dp, 70.dp)))
        assertEquals(1, upNextWidgetRows(DpSize(110.dp, 40.dp)))
    }

    @Test
    fun twoOrThreeCellsTallShowThreeRows() {
        assertEquals(3, upNextWidgetRows(DpSize(250.dp, 110.dp)))
        assertEquals(3, upNextWidgetRows(DpSize(110.dp, 200.dp)))
    }

    @Test
    fun fourCellsTallShowFiveRows() {
        assertEquals(5, upNextWidgetRows(DpSize(250.dp, 280.dp)))
    }

    @Test
    fun narrowWidgetDropsPosters() {
        assertFalse(upNextWidgetShowsPosters(DpSize(110.dp, 110.dp)))
        assertTrue(upNextWidgetShowsPosters(DpSize(250.dp, 110.dp)))
    }
}

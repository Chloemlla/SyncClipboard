package com.chloemlla.syncclipboard.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelSizingTest {

    @Test
    fun mb_roundTrip() {
        assertEquals(20, MainViewModel.bytesToMb(20L * 1024 * 1024))
        assertEquals(20L * 1024 * 1024, MainViewModel.mbToBytes(20))
        assertEquals(1, MainViewModel.bytesToMb(1))
        assertEquals(1L * 1024 * 1024, MainViewModel.mbToBytes(0))
        assertEquals(512L * 1024 * 1024, MainViewModel.mbToBytes(9999))
    }
}

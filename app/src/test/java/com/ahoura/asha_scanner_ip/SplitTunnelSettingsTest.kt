package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.guard.SplitTunnelSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTunnelSettingsTest {

    @Test
    fun testModes() {
        assertEquals("All apps", SplitTunnelSettings.Mode.ALL.label)
        assertEquals("Only selected apps", SplitTunnelSettings.Mode.INCLUDE.label)
        assertEquals("Exclude selected apps", SplitTunnelSettings.Mode.EXCLUDE.label)
    }

    @Test
    fun testModeValues() {
        val modes = SplitTunnelSettings.Mode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(SplitTunnelSettings.Mode.ALL))
        assertTrue(modes.contains(SplitTunnelSettings.Mode.INCLUDE))
        assertTrue(modes.contains(SplitTunnelSettings.Mode.EXCLUDE))
    }
}

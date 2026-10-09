package com.lnu.tclhdmilauncher.cec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CecLogParserTest {

    private fun line(command: String, params: String? = null): String {
        val suffix = if (params != null) " params:$params" else ""
        return "10-09 12:00:00.123 D/HdmiCecController( 1234): [R] command:<$command>$suffix"
    }

    @Test
    fun `unrelated lines are ignored`() {
        assertNull(CecLogParser.parse("10-09 12:00:00.000 I/ActivityManager: something else"))
        assertNull(CecLogParser.parse(""))
    }

    @Test
    fun `image view on is detected`() {
        assertEquals(CecLogEvent.ImageViewOn, CecLogParser.parse(line("Image View On")))
    }

    @Test
    fun `standby and inactive source map to their own events`() {
        assertEquals(CecLogEvent.Standby, CecLogParser.parse(line("Standby")))
        assertEquals(CecLogEvent.InactiveSource, CecLogParser.parse(line("InActive Source", " 10 00")))
    }

    @Test
    fun `report power status is not treated as standby`() {
        assertNull(CecLogParser.parse(line("Report Power Status", " 01")))
    }

    @Test
    fun `active source uses first nibble of first parameter as port`() {
        assertEquals(CecLogEvent.ActiveSource(1), CecLogParser.parse(line("Active Source", " 10 00")))
        assertEquals(CecLogEvent.ActiveSource(3), CecLogParser.parse(line("Active Source", " 30 00")))
        assertEquals(CecLogEvent.ActiveSource(4), CecLogParser.parse(line("Active Source", " 40 00")))
    }

    @Test
    fun `active source with unsupported or missing address yields null port`() {
        assertEquals(CecLogEvent.ActiveSource(null), CecLogParser.parse(line("Active Source", " 00 00")))
        assertEquals(CecLogEvent.ActiveSource(null), CecLogParser.parse(line("Active Source", " F0 00")))
        assertEquals(CecLogEvent.ActiveSource(null), CecLogParser.parse(line("Active Source")))
    }

    @Test
    fun `routing change uses the new physical address`() {
        // params: old address (10 00) then new address (20 00)
        assertEquals(
            CecLogEvent.RoutingChange(2),
            CecLogParser.parse(line("Routing Change", " 10 00 20 00")),
        )
    }

    @Test
    fun `routing change without enough parameters yields null port`() {
        assertEquals(
            CecLogEvent.RoutingChange(null),
            CecLogParser.parse(line("Routing Change", " 10 00")),
        )
    }

    @Test
    fun `hex parameters are case insensitive`() {
        assertEquals(2, CecLogParser.parsePortFromParams("params: 2a 00", 0))
        assertEquals(2, CecLogParser.parsePortFromParams("params: 2A 00", 0))
    }

    @Test
    fun `physical address int maps to switchable ports only`() {
        assertEquals(1, CecLogParser.portFromPhysicalAddress(0x1000))
        assertEquals(3, CecLogParser.portFromPhysicalAddress(0x3000))
        assertEquals(2, CecLogParser.portFromPhysicalAddress(0x2100))
        assertNull(CecLogParser.portFromPhysicalAddress(0x4000))
        assertNull(CecLogParser.portFromPhysicalAddress(0))
        assertNull(CecLogParser.portFromPhysicalAddress(-1))
    }

    @Test
    fun `physical address string maps to switchable ports only`() {
        assertEquals(1, CecLogParser.portFromPhysicalAddress("1000"))
        assertEquals(3, CecLogParser.portFromPhysicalAddress("3000"))
        assertNull(CecLogParser.portFromPhysicalAddress("4000"))
        assertNull(CecLogParser.portFromPhysicalAddress(""))
        assertNull(CecLogParser.portFromPhysicalAddress("zzzz"))
    }
}

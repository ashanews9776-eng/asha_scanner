package com.ahoura.asha_scanner_ip

import com.ahoura.asha_scanner_ip.core.guard.CoreConfig
import com.ahoura.asha_scanner_ip.core.guard.TunPacketAction
import com.ahoura.asha_scanner_ip.core.guard.TunPacketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class NetworkGuardTest {

    @Test
    fun formatsPeerForVariousProtocols() {
        // WireGuard default port is 2408
        assertEquals("162.159.192.1:2408", CoreConfig.formatPeerForProtocol("162.159.192.1", "wireguard"))
        assertEquals("162.159.192.1:854", CoreConfig.formatPeerForProtocol("162.159.192.1:854", "wireguard"))
        assertEquals("[2606:4700::1]:2408", CoreConfig.formatPeerForProtocol("2606:4700::1", "wireguard"))
        assertEquals("[2606:4700::1]:854", CoreConfig.formatPeerForProtocol("[2606:4700::1]:854", "wireguard"))

        // MASQUE / WOM default port is 443
        assertEquals("162.159.192.1:443", CoreConfig.formatPeerForProtocol("162.159.192.1", "masque"))
        assertEquals("104.16.1.1:443", CoreConfig.formatPeerForProtocol("104.16.1.1", "wom"))
        assertEquals("[2606:4700::1]:443", CoreConfig.formatPeerForProtocol("2606:4700::1", "masque"))
    }

    @Test
    fun quicBlockerGeneratesIcmpPortUnreachable() {
        // Build a mock IPv4 UDP packet targeting port 443 (QUIC)
        val srcIp = byteArrayOf(10, 0, 0, 2)
        val dstIp = byteArrayOf(104, 16, 1, 1)
        val packet = ByteArray(40)
        val bb = ByteBuffer.wrap(packet)

        // IPv4 Header
        bb.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
        bb.put(0.toByte())    // DSCP/ECN
        bb.putShort(40.toShort()) // Total length
        bb.putShort(1234.toShort()) // Identification
        bb.putShort(0.toShort()) // Flags & Fragment Offset
        bb.put(64.toByte()) // TTL
        bb.put(17.toByte()) // Protocol 17 = UDP
        bb.putShort(0.toShort()) // Header Checksum (placeholder)
        bb.put(srcIp)
        bb.put(dstIp)

        // UDP Header
        bb.putShort(54321.toShort()) // Src Port
        bb.putShort(443.toShort())   // Dst Port = 443 (QUIC)
        bb.putShort(20.toShort())    // UDP Length
        bb.putShort(0.toShort())     // UDP Checksum

        val action = TunPacketPolicy.inspect(
            packet = packet,
            length = packet.size,
            isSinkholeEnabled = false,
            isWebRtcBlockerEnabled = false,
            isQuicBlockerEnabled = true,
            isIpv6BlockerEnabled = false,
        )
        assertEquals("QUIC packet should be rejected", TunPacketAction.REJECT, action)

        val reply = ByteArray(1500)
        val replyLen = TunPacketPolicy.buildPortUnreachable(packet, packet.size, reply)

        // Verify synthetic ICMP packet structure:
        // IPv4 header (20 bytes) + ICMP header (8 bytes) + Original IPv4 header (20 bytes) + Original UDP header (8 bytes) = 56 bytes
        assertEquals(56, replyLen)
        assertEquals(0x45.toByte(), reply[0]) // IPv4
        assertEquals(1.toByte(), reply[9])    // Protocol 1 = ICMP

        // Check source and destination swapped
        assertEquals(dstIp[0], reply[12])
        assertEquals(dstIp[1], reply[13])
        assertEquals(dstIp[2], reply[14])
        assertEquals(dstIp[3], reply[15])

        assertEquals(srcIp[0], reply[16])
        assertEquals(srcIp[1], reply[17])
        assertEquals(srcIp[2], reply[18])
        assertEquals(srcIp[3], reply[19])

        // Check ICMP Type 3 (Destination Unreachable), Code 3 (Port Unreachable)
        assertEquals(3.toByte(), reply[20])
        assertEquals(3.toByte(), reply[21])
    }

    @Test
    fun webrtcBlockerDropsStunPackets() {
        val packet = ByteArray(48)
        val bb = ByteBuffer.wrap(packet)

        // IPv4 Header
        bb.put(0x45.toByte())
        bb.put(0.toByte())
        bb.putShort(48.toShort())
        bb.putShort(1.toShort())
        bb.putShort(0.toShort())
        bb.put(64.toByte())
        bb.put(17.toByte()) // UDP
        bb.putShort(0.toShort())
        bb.put(byteArrayOf(10, 0, 0, 2))
        bb.put(byteArrayOf(104, 16, 1, 1))

        // UDP Header
        bb.putShort(54321.toShort())
        bb.putShort(3478.toShort()) // STUN port 3478
        bb.putShort(28.toShort())
        bb.putShort(0.toShort())

        // STUN Message: Binding Request (0x0001) + Magic Cookie 0x2112A442
        bb.putShort(0x0001.toShort()) // Binding Request
        bb.putShort(0.toShort())      // Message length 0
        bb.put(TunPacketPolicy.STUN_MAGIC_0)
        bb.put(TunPacketPolicy.STUN_MAGIC_1)
        bb.put(TunPacketPolicy.STUN_MAGIC_2)
        bb.put(TunPacketPolicy.STUN_MAGIC_3)

        val action = TunPacketPolicy.inspect(
            packet = packet,
            length = packet.size,
            isSinkholeEnabled = false,
            isWebRtcBlockerEnabled = true,
            isQuicBlockerEnabled = false,
            isIpv6BlockerEnabled = false,
        )
        assertEquals(TunPacketAction.DROP, action)
    }

    @Test
    fun ipv6BlockerDropsIpv6PacketsWhenEnabled() {
        val ipv6Packet = ByteArray(40)
        ipv6Packet[0] = 0x60.toByte() // IPv6 Version 6

        val actionEnabled = TunPacketPolicy.inspect(
            packet = ipv6Packet,
            length = ipv6Packet.size,
            isSinkholeEnabled = false,
            isWebRtcBlockerEnabled = false,
            isQuicBlockerEnabled = false,
            isIpv6BlockerEnabled = true,
        )
        val actionDisabled = TunPacketPolicy.inspect(
            packet = ipv6Packet,
            length = ipv6Packet.size,
            isSinkholeEnabled = false,
            isWebRtcBlockerEnabled = false,
            isQuicBlockerEnabled = false,
            isIpv6BlockerEnabled = false,
        )

        assertEquals(TunPacketAction.DROP, actionEnabled)
        assertEquals(TunPacketAction.FORWARD, actionDisabled)
    }

    @Test
    fun dnsSinkholeSynthesizesZerosForBlockedDomains() {
        // Target domain: ad.doubleclick.net
        val domainLabels = listOf("ad", "doubleclick", "net")
        var qnameLen = 1 // trailing 0
        domainLabels.forEach { qnameLen += 1 + it.length }

        val totalLen = 20 + 8 + 12 + qnameLen + 4 // IPv4 + UDP + DNS Header + QNAME + QTYPE/QCLASS
        val packet = ByteArray(totalLen)
        val bb = ByteBuffer.wrap(packet)

        // IPv4 Header
        bb.put(0x45.toByte())
        bb.put(0.toByte())
        bb.putShort(totalLen.toShort())
        bb.putShort(999.toShort())
        bb.putShort(0.toShort())
        bb.put(64.toByte())
        bb.put(17.toByte()) // UDP
        bb.putShort(0.toShort())
        bb.put(byteArrayOf(10, 0, 0, 2))
        bb.put(byteArrayOf(1, 1, 1, 1))

        // UDP Header
        bb.putShort(45678.toShort())
        bb.putShort(53.toShort()) // Port 53
        bb.putShort((totalLen - 20).toShort())
        bb.putShort(0.toShort())

        // DNS Header
        val queryId = 0xABCD.toShort()
        bb.putShort(queryId)
        bb.putShort(0x0100.toShort()) // Standard query, RD=1
        bb.putShort(1.toShort())      // QDCOUNT = 1
        bb.putShort(0.toShort())      // ANCOUNT = 0
        bb.putShort(0.toShort())      // NSCOUNT = 0
        bb.putShort(0.toShort())      // ARCOUNT = 0

        // QNAME
        for (label in domainLabels) {
            bb.put(label.length.toByte())
            bb.put(label.toByteArray())
        }
        bb.put(0.toByte()) // null terminator

        // QTYPE = 1 (A), QCLASS = 1 (IN)
        bb.putShort(1.toShort())
        bb.putShort(1.toShort())

        val action = TunPacketPolicy.inspect(
            packet = packet,
            length = packet.size,
            isSinkholeEnabled = true,
            isWebRtcBlockerEnabled = false,
            isQuicBlockerEnabled = false,
            isIpv6BlockerEnabled = false,
        )
        assertEquals("Tracker DNS query should be sinkholed", TunPacketAction.SINKHOLE, action)

        val reply = ByteArray(1500)
        val replyLen = TunPacketPolicy.buildDnsSinkholeReply(packet, packet.size, reply)
        assertTrue(replyLen > 0)

        val replyBb = ByteBuffer.wrap(reply)
        replyBb.position(28)
        val replyId = replyBb.short
        assertEquals(queryId, replyId)
        val flags = replyBb.short.toInt() and 0xFFFF
        assertTrue("QR bit must be set (response)", (flags and 0x8000) != 0)
        val anCount = replyBb.getShort(34).toInt() and 0xFFFF
        assertEquals(1, anCount)
    }
}

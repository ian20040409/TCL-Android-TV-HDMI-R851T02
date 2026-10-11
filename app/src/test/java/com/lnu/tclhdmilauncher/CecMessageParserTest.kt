package com.lnu.tclhdmilauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CecMessageParserTest {

    @Test
    fun testExtractSrc() {
        assertEquals(4, CecMessageParser.extractSrc("command:<Standby> src: 4, dest: 15"))
        assertEquals(0, CecMessageParser.extractSrc("command:<Active Source> src: 0, dest: 15, params: 10 00"))
        assertEquals(8, CecMessageParser.extractSrc("[R]: command:<Inactive Source> src: 8, dest: 0, params: 20 00"))
        assertNull(CecMessageParser.extractSrc("something without src"))
    }

    @Test
    fun testExtractDest() {
        assertEquals(15, CecMessageParser.extractDest("command:<Standby> src: 4, dest: 15"))
        assertEquals(0, CecMessageParser.extractDest("command:<Active Source> src: 4, dest: 0, params: 10 00"))
        assertNull(CecMessageParser.extractDest("something without dest"))
    }

    @Test
    fun testExtractParams() {
        val params = CecMessageParser.extractParams("command:<Active Source> src: 4, dest: 15, params: 10 00")
        assertEquals(listOf("10", "00"), params)

        val routingParams = CecMessageParser.extractParams("command:<Routing Change> src: 0, dest: 15, params: 10 00 20 00")
        assertEquals(listOf("10", "00", "20", "00"), routingParams)

        val empty = CecMessageParser.extractParams("command:<Standby> src: 4, dest: 15")
        assertTrue(empty.isEmpty())
    }

    @Test
    fun testParsePortFromAddressByte() {
        assertEquals(1, CecMessageParser.parsePortFromAddressByte("10"))
        assertEquals(2, CecMessageParser.parsePortFromAddressByte("20"))
        assertEquals(3, CecMessageParser.parsePortFromAddressByte("30"))
        assertEquals(4, CecMessageParser.parsePortFromAddressByte("40"))
        assertNull(CecMessageParser.parsePortFromAddressByte("00"))
        assertNull(CecMessageParser.parsePortFromAddressByte("F0"))
        assertNull(CecMessageParser.parsePortFromAddressByte(null))
    }

    @Test
    fun testSleepAction_differentPort_ignored() {
        // User is watching HDMI 2, but device on HDMI 1 goes to sleep
        val decision = CecMessageParser.evaluateSleepAction(
            sourcePort = 1,
            src = 4,
            currentViewingPort = 2,
            isVideoActive = true,
            isViewerActive = true
        )
        assertFalse("Should not sleep when sleeping device is HDMI 1 but user is watching HDMI 2", decision.shouldSleep)
        assertTrue(decision.reason.contains("currently viewing HDMI 2"))
    }

    @Test
    fun testSleepAction_matchingPort_accepted() {
        // User is watching HDMI 1, and device on HDMI 1 goes to sleep
        val decision = CecMessageParser.evaluateSleepAction(
            sourcePort = 1,
            src = 4,
            currentViewingPort = 1,
            isVideoActive = true,
            isViewerActive = true
        )
        assertTrue("Should sleep when sleeping device matches active viewing port", decision.shouldSleep)
        assertTrue(decision.reason.contains("matches active HDMI 1"))
    }

    @Test
    fun testSleepAction_viewerNotActive_ignored() {
        // User is in Launcher or YouTube (HDMI viewer not active)
        val decision = CecMessageParser.evaluateSleepAction(
            sourcePort = 1,
            src = 4,
            currentViewingPort = null,
            isVideoActive = false,
            isViewerActive = false
        )
        assertFalse("Should not sleep when user is not actively watching HDMI", decision.shouldSleep)
        assertTrue(decision.reason.contains("HDMI viewer not active"))
    }

    @Test
    fun testSleepAction_unknownPortWithActiveVideo_ignored() {
        // Unknown device sends Standby, but active video is rendering on HDMI 2
        val decision = CecMessageParser.evaluateSleepAction(
            sourcePort = null,
            src = 4,
            currentViewingPort = 2,
            isVideoActive = true,
            isViewerActive = true
        )
        assertFalse("Should not sleep when video is actively playing on current port", decision.shouldSleep)
        assertTrue(decision.reason.contains("Active video is playing"))
    }

    @Test
    fun testSleepAction_unknownPortWithoutVideo_accepted() {
        // Unknown device sends Standby, and current port has lost video
        val decision = CecMessageParser.evaluateSleepAction(
            sourcePort = null,
            src = 4,
            currentViewingPort = 2,
            isVideoActive = false,
            isViewerActive = true
        )
        assertTrue("Should sleep when current port has no video and received standby", decision.shouldSleep)
        assertTrue(decision.reason.contains("has no video signal"))
    }

    @Test
    fun testSleepAction_standbyFromTvItself_ignored() {
        val decision = CecMessageParser.evaluateSleepAction(
            sourcePort = null,
            src = 0,
            currentViewingPort = 1,
            isVideoActive = true,
            isViewerActive = true,
            isFromTvItself = true
        )
        assertFalse("Standby issued by TV itself must be ignored", decision.shouldSleep)
    }

    // ── 使用者實測三點重要情境驗證 ───────────────────────────────────────

    @Test
    fun testUserScenario1_appleTvWakeDetectsPortAndOverridesInput() {
        // 情境 1: Apple TV 遙控器喚醒，發送 Active Source params: 10 00
        val line = "10-11 12:00:00.000 D/HdmiCecController: [R]: command:<Active Source> src: 4, dest: 15, params: 10 00"
        val src = CecMessageParser.extractSrc(line)
        val params = CecMessageParser.extractParams(line)
        val port = CecMessageParser.parsePortFromAddressByte(params.getOrNull(0))

        assertEquals(4, src)
        assertEquals(1, port) // 正確解析為 HDMI 1，CecLogReaderService 會主動喚醒並切換至 HDMI 1
    }

    @Test
    fun testUserScenario2_appleTvIdlesWhilePlayingPcGameOnHdmi3_doesNotSleep() {
        // 情境 2: Apple TV (HDMI 1) 用到一半換 HDMI 3 玩電腦遊戲（視訊正常渲染中）
        // Apple TV 15 分鐘後閒置逾時，發送 Inactive Source (params: 10 00) 與 Standby (src: 4)
        val currentViewingPort = 3 // 正在玩電腦遊戲
        val isVideoActive = true    // 電腦視訊畫面正常顯示中
        val isViewerActive = true

        // 2-1: Inactive Source 來自 HDMI 1
        val inactiveDecision = CecMessageParser.evaluateSleepAction(
            sourcePort = 1,
            src = 4,
            currentViewingPort = currentViewingPort,
            isVideoActive = isVideoActive,
            isViewerActive = isViewerActive
        )
        assertFalse("正在觀看 HDMI 3 時，HDMI 1 的 Inactive Source 絕不能關閉電視", inactiveDecision.shouldSleep)

        // 2-2: Standby 來自 HDMI 1 (src 4)
        val standbyDecision = CecMessageParser.evaluateSleepAction(
            sourcePort = 1,
            src = 4,
            currentViewingPort = currentViewingPort,
            isVideoActive = isVideoActive,
            isViewerActive = isViewerActive
        )
        assertFalse("正在觀看 HDMI 3 時，HDMI 1 的 Standby 絕不能關閉電視", standbyDecision.shouldSleep)

        // 2-3: 假設 sourcePort 未知（未預先建立對應），但 HDMI 3 遊戲畫面仍在正常渲染
        val unknownSrcDecision = CecMessageParser.evaluateSleepAction(
            sourcePort = null,
            src = 4,
            currentViewingPort = currentViewingPort,
            isVideoActive = isVideoActive,
            isViewerActive = isViewerActive
        )
        assertFalse("HDMI 3 畫面正在正常渲染，未知背景來源的 Standby 絕不能關閉電視", unknownSrcDecision.shouldSleep)
    }

    @Test
    fun testUserScenario3_appleTvRemotePowerOffWhileWatchingAppleTv_sleepsTv() {
        // 情境 3: 使用 Apple TV 訊號源 (HDMI 1) 時，使用 Apple TV 遙控器關機
        val currentViewingPort = 1 // 正在觀看 Apple TV
        val isViewerActive = true

        // 3-1: 透過 Apple TV 遙控器送出 Standby 指令 (src 4 -> HDMI 1)
        val standbyDecision = CecMessageParser.evaluateSleepAction(
            sourcePort = 1,
            src = 4,
            currentViewingPort = currentViewingPort,
            isVideoActive = true, // 按下的瞬間畫面可能仍在
            isViewerActive = isViewerActive
        )
        assertTrue("當前正在觀看 HDMI 1，Apple TV 遙控器關機應正確連動電視休眠", standbyDecision.shouldSleep)
        assertTrue(standbyDecision.reason.contains("matches active HDMI 1"))

        // 3-2: Apple TV 同時送出 Inactive Source (params: 10 00 -> HDMI 1)
        val inactiveDecision = CecMessageParser.evaluateSleepAction(
            sourcePort = 1,
            src = 4,
            currentViewingPort = currentViewingPort,
            isVideoActive = false, // 畫面熄滅
            isViewerActive = isViewerActive
        )
        assertTrue("當前正在觀看 HDMI 1，收到 Inactive Source 應正確連動電視休眠", inactiveDecision.shouldSleep)
    }
}

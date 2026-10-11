package com.lnu.tclhdmilauncher

/**
 * Helper utility for parsing HDMI-CEC logcat lines and evaluating smart standby decisions.
 */
object CecMessageParser {

    fun extractSrc(line: String): Int? {
        return Regex("src:\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun extractDest(line: String): Int? {
        return Regex("dest:\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun extractParams(line: String): List<String> {
        val raw = Regex("params:\\s*((?:[0-9a-fA-F]{2}(?:\\s+|$))+)", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.get(1)?.trim() ?: return emptyList()
        return raw.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    /**
     * Parses the HDMI port number (1..4) from a physical address byte hex string (e.g., "10" -> 1, "20" -> 2).
     */
    fun parsePortFromAddressByte(byteHex: String?): Int? {
        if (byteHex == null) return null
        val firstNibble = byteHex.firstOrNull()?.digitToIntOrNull(16)
        return if (firstNibble in 1..4) firstNibble else null
    }

    /**
     * Checks if a CEC log message represents an external wake-up or active source request.
     */
    fun isWakeMessage(line: String): Boolean {
        val normalized = line.lowercase(java.util.Locale.ROOT)
        if (normalized.contains("<image view on>") || normalized.contains("<text view on>")) {
            return true
        }
        val src = extractSrc(line)
        if (src != null && src != 0) {
            if (normalized.contains("<active source>") || normalized.contains("<routing change>")) {
                return true
            }
        }
        return false
    }

    /**
     * Evaluates whether an incoming CEC Standby, Inactive Source, or voicestandby signal
     * should put the TV into sleep mode.
     *
     * Prevents TV from turning off when an idle CEC device goes to sleep in the background
     * while the user is watching another input or using an app/launcher.
     *
     * @param sourcePort Port of the sleeping device (1..3), or null if unknown
     * @param src CEC logical address of the sender (0..15), or null if unknown
     * @param currentViewingPort Port currently displayed by the viewer (1..3), or null if not viewing HDMI
     * @param isVideoActive Whether video is actively rendering on currentViewingPort
     * @param isViewerActive Whether the HDMI viewer activity is alive and visible
     * @param isFromTvItself True if the standby command was issued by TV itself (src: 0)
     */
    fun evaluateSleepAction(
        sourcePort: Int?,
        src: Int?,
        currentViewingPort: Int?,
        isVideoActive: Boolean,
        isViewerActive: Boolean,
        isFromTvItself: Boolean = (src == 0)
    ): SleepDecision {
        if (isFromTvItself) {
            return SleepDecision(
                shouldSleep = false,
                reason = "Standby sent by TV itself (src: 0) ignored"
            )
        }

        if (!isViewerActive || currentViewingPort == null) {
            return SleepDecision(
                shouldSleep = false,
                reason = "HDMI viewer not active (current viewing: null, device port: ${sourcePort ?: "?"})"
            )
        }

        if (sourcePort != null && sourcePort != currentViewingPort) {
            return SleepDecision(
                shouldSleep = false,
                reason = "Sleeping device is HDMI $sourcePort, but TV is currently viewing HDMI $currentViewingPort"
            )
        }

        if (sourcePort == currentViewingPort) {
            return SleepDecision(
                shouldSleep = true,
                reason = "Sleeping device matches active HDMI $currentViewingPort"
            )
        }

        // sourcePort is unknown
        if (isVideoActive) {
            return SleepDecision(
                shouldSleep = false,
                reason = "Active video is playing on HDMI $currentViewingPort; unknown device sleep must be background"
            )
        }

        return SleepDecision(
            shouldSleep = true,
            reason = "Active HDMI $currentViewingPort has no video signal and received sleep command"
        )
    }
}

data class SleepDecision(
    val shouldSleep: Boolean,
    val reason: String
)

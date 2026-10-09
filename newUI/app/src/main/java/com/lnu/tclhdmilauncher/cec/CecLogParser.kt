package com.lnu.tclhdmilauncher.cec


/** 從 HDMI-CEC logcat 一行文字解析出的事件。 */
internal sealed interface CecLogEvent {
    /** TV 被要求開機（Image View On）。 */
    data object ImageViewOn : CecLogEvent

    /**
     * 來源裝置宣告自己為 Active Source。
     * [port] 為 TV 的 HDMI port；無法解析或不在 1..4 時為 null。
     */
    data class ActiveSource(val port: Int?) : CecLogEvent

    /** 路由變更，[port] 取自「新的實體位址」；無法解析時為 null。 */
    data class RoutingChange(val port: Int?) : CecLogEvent

    data object InactiveSource : CecLogEvent

    data object Standby : CecLogEvent
}

/**
 * 解析 HDMI-CEC 相關資訊的純函式集合（不依賴 Android framework，可直接做 JVM 單元測試）。
 */
internal object CecLogParser {

    /** 實體位址第一個 nibble 即為 TV 的輸入 port；超出此範圍視為不支援。 */
    private val VALID_LOGCAT_PORTS = 1..4

    /** 本 App 實際能切換的 HDMI port 範圍。 */
    val SWITCHABLE_PORTS = 1..3

    private val PARAMS_REGEX = Regex("params:((?: [0-9a-fA-F]{2})+)")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /** 回傳 logcat 該行對應的事件；與 CEC 無關的行回傳 null。 */
    fun parse(line: String): CecLogEvent? = when {
        line.contains("command:<Image View On>") -> CecLogEvent.ImageViewOn
        // Active Source 的參數是來源的實體位址，例如 10 00 = HDMI 1、30 00 = HDMI 3。
        line.contains("command:<Active Source>") ->
            CecLogEvent.ActiveSource(parsePortFromParams(line, parameterIndex = 0))
        // Routing Change 帶有舊 / 新實體位址，新位址是第三個參數（index 2）。
        line.contains("command:<Routing Change>") ->
            CecLogEvent.RoutingChange(parsePortFromParams(line, parameterIndex = 2))
        line.contains("command:<InActive Source>") -> CecLogEvent.InactiveSource
        // Report Power Status 01 只是 TV 輪詢待機中播放裝置的回應，不是 Standby 指令，
        // 所以只比對 <Standby> 指令本身。
        line.contains("command:<Standby>") -> CecLogEvent.Standby
        else -> null
    }

    /** 取出 `params: xx xx ...` 的第 [parameterIndex] 個位元組，並轉為 HDMI port。 */
    fun parsePortFromParams(line: String, parameterIndex: Int): Int? {
        val params = PARAMS_REGEX.find(line)
            ?.groupValues?.get(1)
            ?.trim()
            ?.split(WHITESPACE_REGEX)
            ?: return null
        val byte = params.getOrNull(parameterIndex) ?: return null
        return byte.firstOrNull()?.digitToIntOrNull(16)?.takeIf { it in VALID_LOGCAT_PORTS }
    }

    /** 將 16 位元實體位址（例如 0x3000）轉成 port；只接受 [SWITCHABLE_PORTS]。 */
    fun portFromPhysicalAddress(address: Int): Int? {
        if (address <= 0) return null
        return ((address shr 12) and 0xF).takeIf { it in SWITCHABLE_PORTS }
    }

    /** 將字串型態的實體位址（例如 "1000"、"3000"）轉成 port；只接受 [SWITCHABLE_PORTS]。 */
    fun portFromPhysicalAddress(address: String): Int? =
        address.firstOrNull()?.digitToIntOrNull(16)?.takeIf { it in SWITCHABLE_PORTS }
}

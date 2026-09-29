fun main() {
    val line = "09-29 22:34:50.992   669   669 D HdmiCecController: HDMI_DEBUG handleIncomingCecCommand command:<Active Source> src: 4, dst: 15, params: 10 00"
    val regex = "params: ([0-9a-fA-F]{2})".toRegex()
    val match = regex.find(line)
    if (match != null) {
        val param1 = match.groupValues[1]
        val portChar = param1[0]
        val port = portChar.toString().toIntOrNull()
        println("port: \$port")
    } else {
        println("no match")
    }
}

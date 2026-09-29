fun main() {
    val proc = Runtime.getRuntime().exec(arrayOf("input", "keyevent", "26"))
    proc.waitFor()
    println("Exit code: \${proc.exitValue()}")
}

package df.root

/**
 * Port of DFRoot 4.7's IReporter.java (GPL-2.0) — line-for-line, no logic changes.
 * In Kotlin this is one abstract function; the SAM shape is identical.
 */
fun interface IReporter {
    fun report(msg: String)
}

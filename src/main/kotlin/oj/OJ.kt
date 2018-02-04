import oj.models.NFA
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import oj.scanner.ScannerError
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        exitProcess(42)
    }

    var baseDfas = BASE_DFA_NAMES
            .keys
            .map { NFA.deserialize("gen/$it.dfa", oj.scanner.ALPHABET, it) }
            .toSet()
    val scannerDfa = NFA.deserialize("gen/$SCANNER_DFA.dfa", oj.scanner.ALPHABET, "")
    val inputStream = File(args[0]).inputStream()
    var inputFileString = inputStream.bufferedReader().use { it.readText() }

    val scanner = Scanner(scannerDfa, baseDfas)
    try {
        scanner.tokenize(inputFileString)
        println("Scanner Passes")
    } catch (e: ScannerError) {
        println("Scanner Error")
        System.exit(42)
    }
    System.exit(0)
}

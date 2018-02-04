import oj.models.NFA
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import oj.scanner.ScannerError
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
    val scanner = Scanner(args[0], scannerDfa, baseDfas)
    try {
        scanner.tokenize()
        println("Scanner Passes")
    } catch (e: ScannerError) {
        println("Scanner Error")
        System.exit(42)
    }
    System.exit(0)
}

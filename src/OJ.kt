import models.NFA
import scanner.BASE_DFA_NAMES
import scanner.SCANNER_DFA
import scanner.Scanner
import scanner.ScannerError
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        exitProcess(42)
    }

    var baseDfas = BASE_DFA_NAMES
            .keys
            .map { NFA.deserialize("gen/$it.dfa", scanner.ALPHABET, it) }
            .toSet()
    val scannerDfa = NFA.deserialize("gen/$SCANNER_DFA.dfa", scanner.ALPHABET, "")
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

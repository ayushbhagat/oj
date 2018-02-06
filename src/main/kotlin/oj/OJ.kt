import oj.models.NFA
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import oj.scanner.ScannerError
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.exit(42)
    }
    var inputFileString = File(args[0]).inputStream().bufferedReader().use { it.readText() }
    var baseDfas = BASE_DFA_NAMES
            .keys
            .map {
                NFA.deserialize(
                        "gen/$it.dfa",
                        NFA.EmptyStateDataHelper(),
                        oj.scanner.ALPHABET,
                        it)
            }
            .toSet()
    val scannerDfa = NFA.deserialize(
            "gen/$SCANNER_DFA.dfa",
            NFA.EmptyStateDataHelper(),
            oj.scanner.ALPHABET,
            "")
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

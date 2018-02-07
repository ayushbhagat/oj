import oj.models.NFA
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.exit(42)
    }
    try {
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
        scanner.tokenize(inputFileString)
    } catch (e: Exception) {
        println(e.message)
        System.exit(42)
    }
    println("Passes")
    System.exit(0)
}

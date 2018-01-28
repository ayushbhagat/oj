import models.NFAType
import models.NFA
import scanner.SCANNER_DFA
import scanner.Scanner
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        exitProcess(42)
    }

    var baseDfas = NFAType
            .values()
            .map { NFA.deserialize("gen/${it.fileName}.dfa", it) }
            .toSet()
    val scannerDfa = NFA.deserialize("gen/$SCANNER_DFA.dfa", null)
    val scanner = Scanner(args[0], scannerDfa, baseDfas)
    try {
        scanner.tokenize()
        println("Scanner Passes")
    } catch (e: Exception) {
        exitProcess(42)
    }
}
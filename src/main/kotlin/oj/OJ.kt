import oj.parser.CFGStateDataHelper
import oj.parser.Parser
import oj.models.NFA
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import oj.weeder.Weeder
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.exit(42)
    }
    try {
        var inputFileString = File(args[0]).inputStream().bufferedReader().use { it.readText() }
        //var inputFileString = File("test/programs/HelloWorld.java").inputStream().bufferedReader().use{ it.readText() }
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
                "").toDFA()
        val scanner = Scanner(scannerDfa, baseDfas)
        val tokens = scanner.tokenize(inputFileString)

        val lr1DFAFilelocation = "gen/joos-lr1.dfa"
        val lr1DFA = NFA.deserialize(
            lr1DFAFilelocation,
            CFGStateDataHelper(),
            setOf(), // TODO: This should be a set of terminals/non-terminals in CFG
            ""
        )

        val parser = Parser(lr1DFA)
        val cst = parser.parse(tokens)
        Weeder.weed(cst)
    } catch (e: Exception) {
        e.printStackTrace()
        println(e.message)
        System.exit(42)
    }
    println("Passes")
    System.exit(0)
}

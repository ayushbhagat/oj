import oj.parser.CFGStateDataHelper
import oj.parser.Parser
import oj.models.NFA
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import oj.weeder.Weeder
import java.io.File

class InvalidClassOrInterfaceNameError(message: String) : Exception(message)

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.exit(42)
    }
    val filename = args[0]
    //val filename = "test/programs/HelloWorld.java"

    try {
        var inputFileString = File(filename).inputStream().bufferedReader().use { it.readText() }
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

        cst
            .getDescendants({ it.name == "ClassDeclaration" || it.name == "InterfaceDeclaration" })
            .forEach({ node ->
                val classOrInterfaceName = node.children[2].lexeme
                val expectedFileName = "$classOrInterfaceName.java"
                val expectedClassName = filename.split("/").last().replace(".java", "")

                if (!filename.endsWith(expectedFileName)) {
                    throw InvalidClassOrInterfaceNameError(
                        "Expected ${node.name} \"$classOrInterfaceName\" to be named \"$expectedClassName\"."
                    )
                }
            })

    } catch (e: Exception) {
        e.printStackTrace()
        println(e.message)
        System.exit(42)
    }
    println("Passes")
    System.exit(0)
}

import oj.models.CSTNode
import oj.parser.CFGStateDataHelper
import oj.parser.Parser
import oj.models.NFA
import oj.nameresolver.NameResolver
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import oj.weeder.Weeder
import java.io.File

class InvalidClassOrInterfaceNameError(message: String) : Exception(message)
class InvalidFileNameError(message: String) : Exception(message)

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.exit(42)
    }

    val filenames = args.toList()
//    val filenames = listOf(
//        "./test/name-resolution/A.java",
//        "./test/name-resolution/B.java"
//        "./test/name-resolution/temp/B.java"
//    )

    try {
        val baseDfas = BASE_DFA_NAMES
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

        val lr1DFAFilelocation = "gen/joos-lr1.dfa"
        val lr1DFA = NFA.deserialize(
            lr1DFAFilelocation,
            CFGStateDataHelper(),
            setOf(), // TODO: This should be a set of terminals/non-terminals in CFG
            ""
        )

        val parser = Parser(lr1DFA)
        val packages = mutableMapOf<String, MutableList<CSTNode>>()

        filenames.forEach({ filename ->
            val inputFileString = File(filename).inputStream().bufferedReader().use { it.readText() }
            val tokens = scanner.tokenize(inputFileString)
            val cst = parser.parse(tokens)
            Weeder.weed(cst)

            // Ensure type within this file has the same name as the basename of this file
            cst
                .getDescendants({ it.name == "ClassDeclaration" || it.name == "InterfaceDeclaration" })
                .forEach({ node ->
                    val classOrInterfaceName = node.children[2].lexeme
                    val basename = filename.split("/").last()
                    val expectedExtension = ".java"

                    if (!basename.endsWith(expectedExtension)) {
                        throw InvalidFileNameError("File extension is invalid: \"$expectedExtension\"")
                    }

                    val expectedClassName = basename.substring(0, basename.length - expectedExtension.length)

                    if (classOrInterfaceName != expectedClassName) {
                        throw InvalidClassOrInterfaceNameError(
                            "Expected ${node.name} \"$classOrInterfaceName\" to be named \"$expectedClassName\"."
                        )
                    }
                })

            val packageDeclarationNodes = cst.getDescendants("PackageDeclaration")

            val packageName = if (packageDeclarationNodes.isEmpty()) "" else {
                val nameNode = packageDeclarationNodes[0].children[1]
                nameNode.getDescendants("IDENTIFIER").map({ it.lexeme }).joinToString(".")
            }

            val pkg = packages.getOrDefault(packageName, mutableListOf())
            pkg.add(cst)

            packages[packageName] = pkg
        })

        NameResolver.resolveNames(packages)


    } catch (e: Exception) {
        e.printStackTrace()
        println(e.message)
        System.exit(42)
    }
    println("Passes")
    System.exit(0)
}

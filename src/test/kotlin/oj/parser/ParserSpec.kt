package oj.parser

import oj.models.CSTNode
import oj.models.NFA
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.subject.SubjectSpek
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(JUnitPlatform::class)
object ParserSpec : SubjectSpek<(String) -> CSTNode>({
    subject {
        var baseDfas = BASE_DFA_NAMES
            .keys
            .map({
                NFA.deserialize(
                    "gen/$it.dfa",
                    NFA.EmptyStateDataHelper(),
                    oj.scanner.ALPHABET,
                    it
                )})
            .toSet()
        val scannerDfa = NFA.deserialize(
            "gen/${SCANNER_DFA}.dfa",
            NFA.EmptyStateDataHelper(),
            oj.scanner.ALPHABET,
            ""
        ).toDFA()
        val scanner = Scanner(scannerDfa, baseDfas)

        val lr1DFAFilelocation = "gen/joos-lr1.dfa"
        val lr1DFA = NFA.deserialize(
            lr1DFAFilelocation,
            CFGStateDataHelper(),
            setOf(), // TODO: This should be a set of terminals/non-terminals in CFG
            ""
        )

        val parser = Parser(lr1DFA);

        { input: String ->
            val tokens = scanner.tokenize(input)
            val cst = parser.parse(tokens)
            cst
        }
    }

    it("Should parse Java Hello World") {
        val program = """
            |public class HelloWorld {
            |   public static void main(String args[]) {
            |       System.out.println("Hello World!");
            |   }
            |}
        """.trimMargin()

        val tree : CSTNode = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }
})

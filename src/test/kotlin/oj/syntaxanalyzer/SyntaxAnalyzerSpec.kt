package oj.syntaxanalyzer

import oj.models.CSTNode
import oj.models.NFA
import oj.nameresolver.NameResolutionError
import oj.nameresolver.NameResolver
import oj.parser.CFGStateDataHelper
import oj.parser.Parser
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import oj.weeder.Weeder
import org.jetbrains.spek.api.dsl.*
import org.jetbrains.spek.subject.SubjectSpek
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(JUnitPlatform::class)
object SyntaxAnalyzerSpec : SubjectSpek<(List<String>) -> Map<String, List<CSTNode>>>({
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

        val parser = Parser(lr1DFA)

        fun (inputPrograms: List<String>) : Map<String, List<CSTNode>> {
            val packages = mutableMapOf<String, MutableList<CSTNode>>()

            inputPrograms.forEach({ inputProgram ->
                val tokens = scanner.tokenize(inputProgram)
                val cst = parser.parse(tokens)
                Weeder.weed(cst)

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
            SyntaxAnalyzer.analyze(packages)
            return packages
        }
    }

    it("should not reject variable usage in its initializer after it's been initialized") {
        subject(listOf("""
            public class A {
                public A() {
                    int a = (a = 1) + a;
                }
            }
        """.trimIndent()))
    }

    it("should reject variable use in its initializer before its been initialized") {
        assertFailsWith(NameResolutionError::class, {
            subject(listOf("""
                public class A {
                    public A() {
                        int a = 1 + a;
                    }
                }
            """.trimIndent()))
        })
    }

    it("should reject variable use in its initializer before its been initialized when it shadows a field") {
        assertFailsWith(NameResolutionError::class, {
            subject(listOf("""
                public class A {
                    public int a = 1;
                    public A() {
                        int a = 1 + a;
                    }
                }
            """.trimIndent()))
        })
    }
})

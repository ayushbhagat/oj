package oj.weeder

import oj.models.CSTNode
import oj.models.NFA
import oj.parser.CFGStateDataHelper
import oj.parser.Parser
import oj.scanner.BASE_DFA_NAMES
import oj.scanner.SCANNER_DFA
import oj.scanner.Scanner
import org.jetbrains.spek.api.dsl.*
import org.jetbrains.spek.subject.SubjectSpek
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(JUnitPlatform::class)
object WeederSpec : SubjectSpek<(String) -> CSTNode>({
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
            Weeder.weed(cst)
            cst
        }
    }

    it("should parse Java Hello World") {
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

    it("should reject abstract final on class declarations") {
        val program = """
            |public abstract final class HelloWorld {
            |   public static void main(String args[]) {
            |   }
            |}
        """.trimMargin()

        assertFailsWith(GeneralModifiersWeeder.AbstractAndFinalInModifiersError::class) {
            subject(program)
        }
    }

    it("should reject abstract methods with bodies") {
        val program = """
            |public abstract class HelloWorld {
            |   public abstract void foo() {}
            |}
        """.trimMargin()

        assertFailsWith(MethodIsAbstractOrNativeIFFItHasNoMethodBodyWeeder.AbstractOrNativeMethodHasBodyError::class) {
            subject(program)
        }
    }

    it("should reject native methods with bodies") {
        val program = """
            |public class HelloWorld {
            |   public native void foo() {}
            |}
        """.trimMargin()

        assertFailsWith(MethodIsAbstractOrNativeIFFItHasNoMethodBodyWeeder.AbstractOrNativeMethodHasBodyError::class) {
            subject(program)
        }
    }

    it("should reject methods without bodies that are neither abstract nor native") {
        val program = """
            |public class HelloWorld {
            |   public void foo();
            |}
        """.trimMargin()

        assertFailsWith(MethodIsAbstractOrNativeIFFItHasNoMethodBodyWeeder.NeitherAbstractNorNativeMethodHasNoBodyError::class) {
            subject(program)
        }
    }

    it("should reject abstract methods that are final") {
        val program = """
            |public abstract class HelloWorld {
            |   public abstract final void foo();
            |}
        """.trimMargin()

        assertFailsWith(WeedError::class) {
            subject(program)
        }
    }

    it("should reject abstract methods that are both static and final") {
        val program = """
            |public abstract class HelloWorld {
            |   public abstract static final void foo();
            |}
        """.trimMargin()

        assertFailsWith(WeedError::class) {
            subject(program)
        }
    }

    it("should reject abstract methods that are static") {
        val program = """
            |public abstract class HelloWorld {
            |   public abstract static void foo();
            |}
        """.trimMargin()

        assertFailsWith(MethodModifiersWeeder.AbstractMethodIsStaticError::class) {
            subject(program)
        }
    }

    it("should reject static methods that are final") {
        val program = """
            |public class HelloWorld {
            |   public static final void foo() {}
            |}
        """.trimMargin()

        assertFailsWith(MethodModifiersWeeder.StaticMethodIsFinalError::class) {
            subject(program)
        }
    }

    it("should reject native methods that are not static") {
        val program = """
            |public class HelloWorld {
            |   public native void foo();
            |}
        """.trimMargin()

        assertFailsWith(MethodModifiersWeeder.NativeMethodIsNotStaticError::class) {
            subject(program)
        }
    }
})

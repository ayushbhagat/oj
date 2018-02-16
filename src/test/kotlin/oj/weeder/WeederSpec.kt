package oj.weeder

import oj.models.CSTNode
import oj.models.NFA
import oj.parser.CFGStateDataHelper
import oj.parser.ParseError
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
            |   public HelloWorld() {}
            |   public static void main(String[] args) {
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
            |   public HelloWorld() {}
            |   public static void main(String[] args) {
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
            |   public HelloWorld() {}
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
            |   public HelloWorld() {}
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
            |   public HelloWorld() {}
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
            |   public HelloWorld() {}
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
            |   public HelloWorld() {}
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
            |   public HelloWorld() {}
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
            |   public HelloWorld() {}
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
            |   public HelloWorld() {}
            |   public native void foo();
            |}
        """.trimMargin()

        assertFailsWith(MethodModifiersWeeder.NativeMethodIsNotStaticError::class) {
            subject(program)
        }
    }

    it("should reject native methods on interfaces") {
        val program = """
            |public interface HelloWorld {
            |   public native void foo();
            |}
        """.trimMargin()

        assertFailsWith(InterfaceWeeder.InterfaceMethodIsNativeError::class) {
            subject(program)
        }
    }

    it("should reject final methods on interfaces") {
        val program = """
            |public interface HelloWorld {
            |   public final void foo();
            |}
        """.trimMargin()

        assertFailsWith(InterfaceWeeder.InterfaceMethodIsFinalError::class) {
            subject(program)
        }
    }

    it("should reject static methods on interfaces") {
        val program = """
            |public interface HelloWorld {
            |   public static void foo();
            |}
        """.trimMargin()

        assertFailsWith(InterfaceWeeder.InterfaceMethodIsStaticError::class) {
            subject(program)
        }
    }

    it("should reject classes that don't have an explicit constructor method") {
        val program = """
            |public class HelloWorld {
            |   public native void foo();
            |}
        """.trimMargin()

        assertFailsWith(ClassWeeder.NoConstructorFoundInClass::class) {
            subject(program)
        }
    }

    it ("should not allow constructor name to be different from class name") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public HelloWorld2() {}
            |   public void main(String[] args) {}
            |}
        """.trimMargin()

        assertFailsWith(ClassWeeder.ClassNameAndConstructorNameMismatch::class) {
            subject(program)
        }
    }

    it("should reject classes with final fields") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   protected final int[] x = 1;
            |}
        """.trimMargin()

        assertFailsWith(FieldWeeder.FieldIsFinalError::class) {
            subject(program)
        }
    }

    it("should reject classes with final fields without initializers") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   protected final int x /* = 0 */;
            |}
        """.trimMargin()

        assertFailsWith(FieldWeeder.FieldIsFinalError::class) {
            subject(program)
        }
    }

    it("should reject constructors with this() calls") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {
            |       this("bob");
            |   }
            |
            |   public HelloWorld(String name) {
            |       System.out.println(name);
            |   }
            |}
        """.trimMargin()

        assertFailsWith(ParseError::class) {
            subject(program)
        }
    }

    it("should reject constructors with super() calls") {
        val program = """
            |public class HelloWorld extends B {
            |   public HelloWorld() {
            |       super("bob");
            |   }
            |}
        """.trimMargin()
        assertFailsWith(ParseError::class) {
            subject(program)
        }
    }

    it("should reject methods with this() calls") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void hi() {
            |       this();
            |   }
            |}
        """.trimMargin()

        assertFailsWith(ParseError::class) {
            subject(program)
        }
    }

    it("should reject methods with super() calls") {
        val program = """
            |public class HelloWorld extends B {
            |   public HelloWorld() {}
            |   public void hi() {
            |       super();
            |   }
            |}
        """.trimMargin()

        assertFailsWith(ParseError::class) {
            subject(program)
        }
    }

    it("should correctly parse methods with array declarations") {
        val program = """
            |public class HelloWorld extends B {
            |   public HelloWorld() {}
            |   public int[] mCount;
            |   public void hi() {
            |       int[] count = new int[10];
            |   }
            |}
        """.trimMargin()
        val tree = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should parse integer literal -(2^31) with space between sign and number") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = - 2147483648;
            |   }
            |}
        """.trimMargin()

        val tree = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should reject methods with multidimensional arrays") {
        val program = """
            |public class HelloWorld extends B {
            |   public int[] mCount;
            |   public void hi() {
            |       int[][] count = new int[10][10];
            |   }
            |}
        """.trimMargin()

        assertFailsWith(ParseError::class) {
            subject(program)
        }
    }

    it("should correctly parse methods with array declarations as formal parameters") {
        val program = """
            |public class HelloWorld extends B {
            |   public HelloWorld() {}
            |   public void hi(int[] count) {
            |       int[] count2 = count;
            |   }
            |}
        """.trimMargin()
        val tree = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should parse integer literal -(2^31) with comments between sign and number") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = - /**/ /**/ 2147483648;
            |   }
            |}
        """.trimMargin()

        val tree : CSTNode = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should not parse integer literals > 2^31 - 1") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = 2147483648;
            |   }
            |}
        """.trimMargin()

        assertFailsWith(IntegerRangeWeeder.IntGreaterThanUpperBoundError::class) {
            subject(program)
        }
    }


    it("should reject methods with multi-dimensional array declarations as formal parameters") {
        val program = """
            |public class HelloWorld extends B {
            |   public HelloWorld() {}
            |   public void hi(int[][] count) {
            |       int[][] count2 = count;
            |   }
            |}
        """.trimMargin()

        assertFailsWith(ParseError::class) {
            val tree = subject(program)
            assertEquals("CompilationUnit", tree.name)
        }
    }

    it("should reject methods with multidimensional arrays") {
        val program = """
            |public class HelloWorld extends B {
            |   public HelloWorld() {}
            |   public int[][] mCount;
            |   public void hi() {}
            |}
        """.trimMargin()

        assertFailsWith(ParseError::class) {
            subject(program)
        }
    }

    it("should not parse integer literals < -(2^31)") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = -2147483649;
            |   }
            |}
        """.trimMargin()
        assertFailsWith(IntegerRangeWeeder.IntLessThanLowerBoundError::class) {
            subject(program)
        }
    }

    it("should not parse integer literals < -(2^31) with space between sign and " +
            "number") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = - 2147483649;
            |   }
            |}
        """.trimMargin()
        assertFailsWith(IntegerRangeWeeder.IntLessThanLowerBoundError::class) {
            subject(program)
        }
    }


    it("should reject methods with multidimensional arrays") {
        val program = """
            |public class HelloWorld extends B {
            |   public HelloWorld() {}
            |   public int[][] mCount = new int[20][30];
            |   public void hi() {}
            |}
        """.trimMargin()

        assertFailsWith(ParseError::class) {
            subject(program)
        }
    }

    it("should not parse integer literals out of bounds in () ") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = -(2147483648);
            |   }
            |}
        """.trimMargin()

        assertFailsWith(IntegerRangeWeeder.IntGreaterThanUpperBoundError::class) {
            subject(program)
        }
    }

    it("should allow basic type as a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = (int) 2;
            |   }
            |}
        """.trimMargin()

        val tree: CSTNode = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should allow basic type array as a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = (int []) 2;
            |   }
            |}
        """.trimMargin()

        val tree: CSTNode = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should allow name as a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = (A) 2;
            |   }
            |}
        """.trimMargin()

        val tree: CSTNode = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should allow name array as a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = (A[]) 2;
            |   }
            |}
        """.trimMargin()

        val tree: CSTNode = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should allow qualified name as a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = (A.b.c) 2;
            |   }
            |}
        """.trimMargin()

        val tree: CSTNode = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should allow qualified name array as a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = (A.b.c[]) 2;
            |   }
            |}
        """.trimMargin()

        val tree: CSTNode = subject(program)
        assertEquals("CompilationUnit", tree.name)
    }

    it("should not allow additive expression as a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = (1 + 2) a;
            |   }
            |}
        """.trimMargin()

        assertFailsWith(CastExpressionWeeder.CastExpressionError::class) {
            subject(program)
        }
    }

    it("should not allow assignment as a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = (a = b) 2;
            |   }
            |}
        """.trimMargin()

        assertFailsWith(CastExpressionWeeder.CastExpressionError::class) {
            subject(program)
        }
    }

    it("should not allow cast within a cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = ((int) 2) 2;
            |   }
            |}
        """.trimMargin()

        assertFailsWith(CastExpressionWeeder.CastExpressionError::class) {
            subject(program)
        }
    }

    it("should not allow double parenthesis cast") {
        val program = """
            |public class HelloWorld {
            |   public HelloWorld() {}
            |   public void main(String[] args) {
            |       int a = ((1)) 2;
            |   }
            |}
        """.trimMargin()

        assertFailsWith(CastExpressionWeeder.CastExpressionError::class) {
            subject(program)
        }
    }

})

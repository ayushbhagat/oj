package oj.nameresolver

import oj.models.CSTNode
import oj.models.NFA
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
object NameResolverSpec : SubjectSpek<(List<String>) -> Map<String, List<CSTNode>>>({
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
            return packages
        }
    }

    describe("single type import declaration") {
        it("should be supported") {
            subject(listOf("""
            // Default package
            import temp.A;

            public class B extends A {
               public B() {}
            }
        """, """
            package temp;

            public class A {
               public A() {}
            }
        """))
        }

        it("should reject usages of classes that haven't been imported") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf("""
                import temp.A;

                public class B extends C {
                   public B() {}
                }
            """, """
                package temp;

                public class A {
                   public A() {}
                }
            """))
            })
        }


        it("should shadow import on demand declarations") {
            subject(listOf(
                """
                    import temp.*;
                    import temp2.A;

                    public class B extends A {
                        public B() {}
                    }
                """,
                """
                    package temp;

                    public class A {
                        public A() {}
                    }
                """,
                """
                    package temp2;

                    public class A {
                        public A() {}
                    }
                """
            ))
        }

        it("should allow single type import to shadow a class from default package") {
            subject(listOf(
                """
                import test.B;

                public class A {
                    public A() {
                        B b = new B();
                    }
                }
                """,
                """
                package test;

                public class B {
                    public B() {}
                }
                """,
                """
                public class B {
                    public B() {}
                }
                """
            ))
        }


        it("should not allow importing 2 classes with the same canonical name") {
            assertFailsWith(DetectedTwoTypesWithSameNameInSamePackage::class, {
                subject(listOf(
                    """
            package test;

            public class A {
                public A() {}
            }
        """,
                    """
            package test;

            public class A {
                public A() {}
            }
        """
                ))
            })
        }

        it("should not allow single name import to conflict with current class") {
            assertFailsWith(DetectedTwoTypesWithSameNameInSamePackage::class, {
                subject(listOf(
                    """
            import test.A;

            public class A {
                public A() {}
            }
        """,
                    """
            package test;

            public class A {
                public A() {}
            }
        """
                ))
            })
        }

        it("should not allow 2 single name imports") {
            assertFailsWith(DetectedTwoTypesWithSameNameInSamePackage::class, {
                subject(listOf(
                    """
            import test.B;
            import test.util.B;

            public class A {
                public A() {}
            }
        """,
                    """
            package test;

            public class B {
                public B() {}
            }
        """,
                    """
            package test.util;

            public class B {
                public B() {}
            }
        """
                ))
            })
        }
    }


    describe("import on demand declarations") {
        it("should not raise an error when another import on demand declaration contains the same type and the type is unused") {
            subject(listOf(
                """
                    import temp.*;
                    import temp2.*;

                    public class B {
                        public B() {}
                    }
                """,
                """
                    package temp;

                    public class A {
                        public A() {}
                    }
                """,
                """
                    package temp2;

                    public class A {
                        public A() {}
                    }
                """
            ))
        }

        it("should raise an error when another import on demand declaration contains the same type and the type is used") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
                        import temp.*;
                        import temp2.*;

                        public class B extends A {
                            public B() {}
                        }
                    """,
                    """
                        package temp;

                        public class A {
                            public A() {}
                        }
                    """,
                    """
                        package temp2;

                        public class A {
                            public A() {}
                        }
                    """
                ))
            })
        }

        it("should allow imports of prefixes of packages") {
            subject(listOf(
                """
                import java.*;

                public class A {
                    public A() {}
                }
            """,
                """
                package java.lang.util.horse.cow.moose;

                public class B {
                    public B() {}
                }
            """
            ))
        }

        it("should allow importing class from prefix of a package and a IOD from package without use") {
            subject(listOf(
                """
                import java.B;
                import java.util.*;

                public class A {
                    public A() {}
                }
            """,
                """
                package java;

                public class B {
                    public B() {}
                }
            """,
                """
                package java.util;

                public class B {
                    public B() {}
                }
            """
            ))
        }

        it("should allow importing class from prefix of a package and a IOD from package with use") {
            subject(listOf(
                """
            import java.B;
            import java.util.*;

            public class A {
                public A() {
                    B b = new B();
                }
            }
        """,
                """
            package java;

            public class B {
                public B() {}
            }
        """,
                """
            package java.util;

            public class B {
                public B() {}
            }
        """
            ))
        }

        it("should support duplicate input on demand declarations for regular packages") {
            subject(listOf(
                """
                import java.util.*;
                import java.util.*;

                public class A {
                    public A() {
                        B b = new B();
                    }
                }
                """,
                """
                package java.util;

                public class B {
                    public B() {}
                }
                """,
                """
                package java.util;

                public class C {
                    public C() {}
                }
                """
            ))
        }

        it("should support input on demand declarations for \"java.lang\" packages") {
            subject(listOf(
                """
                import java.lang.*;

                public class A {
                    public A() {
                        Object b = new Object();
                    }
                }
                """,
                """
                package java.lang;

                public class Object {
                    public Object() {}
                }
                """
            ))
        }

        it("should support duplicate input on demand declarations for \"java.lang\" packages") {
            subject(listOf(
                """
                import java.lang.*;
                import java.lang.*;

                public class A {
                    public A() {
                        Object b = new Object();
                    }
                }
                """,
                """
                package java.lang;

                public class Object {
                    public Object() {}
                }
                """
            ))
        }
    }

    describe("import on default package types") {
        it("should automatically import classes from the default package") {
            subject(listOf(
                """
            public class A extends C {
                public A() {
                    B b = new B();
                }
            }
        """,
                    """
            public class B {
                public B() {}
            }
        """,
                    """
            public class C {
                public C() {}
            }
        """
            ))
        }

        it("should import Object without explicitly importing java.lang.*") {
            subject(listOf(
                """
            public class A {
                public A() {
                    Object o = new Object();
                }
            }
        """,
                """
            package java.lang;

            public class Object {
                public Object() {}
            }
        """
            ))
        }
    }

    describe("fields") {
        it("should not allow 2 fields to have the same name in the same class") {
            assertFailsWith(NameResolutionError::class, {
            subject(listOf(
                    """
            public class A {
                public int a = 0;
                public int a = 10;

                public A() {}
            }
            """
                ))
            })
        }

        it("should allow 2 fields to have the same name if one of them is in a parent class") {
            subject(listOf(
                """
            public class A extends B {
                public int a = 0;

                public A() {}
            }
            """,
                    """
            public class B {
                public int a = 10;

                public B() {}
            }
            """
            ))
        }

        it("should allow using a parent class field") {
            subject(listOf(
                """
            public class A extends B {
                public A() {
                    a = 0;
                }
            }
            """,
                """
            public class B {
                public int a = 1;

                public B() {}
            }
            """
            ))
        }

        it("should allow using a static parent class field") {
            subject(listOf(
                """
            public class A extends B {
                public A() {
                    a = 0;
                }
            }
            """,
                """
            public class B {
                public static int a = 0;

                public B() {}
            }
            """
            ))
        }

        it("should not be able to access instance fields in static method") {
            assertFailsWith(Environment.LookupFailed::class, {
                subject(listOf(
                    """
            public class A {
                public int a = 0;

                public A() {}

                public static void staticMethod() {
                    a = 10;
                }
            }
            """
                ))
            })
        }

        it("should be able to access static fields in instance method") {
            subject(listOf(
                """
            public class A {
                public static int a = 0;

                public A() {}

                public void b() {
                    a = 10;
                }
            }
            """
            ))
        }

        it("should be able to access grandparents field") {
            subject(listOf(
                """
            public class A extends B {
                public A() {
                    c = 1;
                }
            }
            """,
                """
            public class B extends C {
                public B() {}
            }
            """,
                """
            public class C {
                public int c = 10;

                public C() {}
            }
            """
            ))
        }

        it("should allow local variable to have the same name as field") {
            subject(listOf(
                """
            public class A {
                public int a = 0;
                public A() {
                    int a = 1;
                }
            }
            """
            ))
        }

        it("should allow formal parameter to have the same name as field") {
            subject(listOf(
                """
            public class A {
                public int a = 0;

                public A(int a) {}
            }
            """
            ))
        }

        it("should not allow local variable to have the same name as formal parameters") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
                public class A {
                    public A(int a) {
                        int a = 1;
                    }
                }
                """
                ))
            })
        }

        it("should not allow a local variable to shadow another") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
                public class A {
                    public A() {
                        int a = 1;
                        {
                            int a = 0;
                        }
                    }
                }
                """
                ))
            })
        }
    }

    describe("methods") {
        it("should not allow 2 methods to have the same name and same parameter types in the same class") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
            public class A {
                public A() {}

                public void test(int a) {}
                public void test(int b) {}
            }
            """
                ))
            })
        }

        it("should not allow 2 methods to have the same name and parameters that resolve to the same type in the same class") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
            import test.B;

            public class A {
                public A() {}

                public void test(test.B b) {}
                public void test(B b) {}
            }
            """,
                    """
            package test;

            public class B {
                public B() {}
            }
            """
                ))
            })
        }

        it("should allow 2 methods to have the same name and parameter types if one of them is in a parent class") {
            subject(listOf(
                """
            public class A extends B {
                public A() {}

                public void test(int a) {}  // overriding
            }
            """,
                """
            public class B {
                public B() {}

                public void test(int a) {}
            }
            """
            ))
        }

        it("should allow 2 methods to have the same name if they have different formal parameters") {
            subject(listOf(
                """
            public class A {
                public A() {}

                public void test() {}
                public void test(int a) {}
            }
            """
            ))
        }

        it("should allow calling a parent class method") {
            subject(listOf(
                """
            public class A extends B {
                public A() {
                    test();
                }
            }
            """,
                """
            public class B {
                public B() {}

                public void test() {}
            }
            """
            ))
        }

        it("should allow calling a static parent class method") {
            subject(listOf(
                """
            public class A extends B {
                public A() {
                    test();
                }
            }
            """,
                """
            public class B {
                public B() {}

                public static void test() {}
            }
            """
            ))
        }

        it("should not be able to access instance methods in static method") {
            assertFailsWith(Environment.LookupFailed::class, {
                subject(listOf(
                    """
            public class A {
                public A() {}

                public void b() {}

                public static void staticMethod() {
                    b();
                }
            }
            """
                ))
            })
        }

        it("should be able to access static methods in instance method") {
            subject(listOf(
                """
            public class A {
                public A() {}

                public void b() {
                    staticMethod();
                }

                public static void staticMethod() {}
            }
            """
            ))
        }

        it("should be able to access grandparents methods") {
            subject(listOf(
                """
            public class A extends B {
                public A() {
                    c();
                }
            }
            """,
                """
            public class B extends C {
                public B() {}
            }
            """,
                """
            public class C {
                public C() {}

                public void c() {}
            }
            """
            ))
        }

        it("should not allow duplicate method declarations in interfaces") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
                    public interface A {
                        public void foo();
                        public void foo();
                    }
                """.trimIndent()
                ))
            })
        }

        it("should not allow duplicate method declarations with arguments in interfaces") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
                        public interface A {
                            public B foo(int a, B b);
                            public B foo(int c, B a);
                        }
                    """.trimIndent(),
                    """
                        public class B {
                            public B() {}
                        }
                    """.trimIndent()
                ))
            })
        }

        it("should not allow class method arguments to have the same name") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
                        public class A {
                            public A() {}
                            public static void main(int args, int args) {}
                        }
                    """.trimIndent()
                ))
            })
        }

        it("should not allow class constructor arguments to have the same name") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
                        public class A {
                            public A(char args, int args) {}
                        }
                    """.trimIndent()
                ))
            })
        }

        it("should not allow interface method arguments to have the same name") {
            assertFailsWith(NameResolutionError::class, {
                subject(listOf(
                    """
                        public interface A {
                            public void main(int args, int args);
                        }
                    """.trimIndent()
                ))
            })
        }

        it("should allow two distinct interface method arguments to have the same name") {
            subject(listOf(
                """
                    public interface A {
                        public void main(int args);
                        public void main2(int args);
                    }
                """.trimIndent()
            ))
        }

        it("should put constructor formals in the scope of the respective constructor") {
            assertFailsWith(Environment.LookupFailed::class, {
                subject(listOf(
                    """
                        public class A {
                            public A(int i) {}
                            public A() {
                                i = 0;
                            }
                        }
                    """.trimIndent()
                ))
            })
        }
    }

    it("should not allow any package prefix of a fully qualified TypeName usage to contain types") {
        assertFailsWith(NameResolutionError::class, {
            subject(listOf(
                """
                    public class Main {
                        public Main() {
                            horse.fly.cow.moo.A a = new horse.fly.cow.moo.A();
                        }
                    }
                """.trimIndent(),
                """
                    package horse.fly.cow.moo;

                    public class A {
                        public A(int i) {}
                    }
                """.trimIndent(),
                """
                    package horse.fly;

                    public class Test {
                        public Test() {}
                    }
                """.trimIndent()
            ))
        })
    }
})

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
            subject(listOf(
                """
                    import temp.A;

                    public class B extends A {
                       public B() {}
                    }
                """.trimIndent(),
                """
                    package temp;

                    public class A {
                       public A() {}
                    }
                """.trimIndent()
            ))
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

        it("should not allow a package name to clash with a canonical class name") {
            assertFailsWith(EitherPackageNameOrQualifiedType::class, {
                subject(listOf(
                    """
                    package java;

                    public class A {
                        public A() {}
                    }
                    """.trimIndent(),
                    """
                    package java.A;

                    public class B {
                        public B() {}
                    }
                    """.trimIndent()
                ))
            })
        }

        it("should allow a class in the default package to have the same name as a package") {
            subject(listOf(
                """
                    package B;

                    public class A {
                        public A() {}
                    }
                    """.trimIndent(),
                """
                    public class B {
                        public B() {}
                    }
                    """.trimIndent()
            ))
        }

        it("should not allow import on demand declarations for a package that doesn't exist even if its a string prefix of some package that does exist") {
            assertFailsWith(ImportOnDemandDeclarationDetectedForNonExistentPackage::class, {
                subject(listOf(
                    """
                    import fo.*;

                    public class A {
                        public A() {}
                    }
                    """.trimIndent(),
                    """
                    package foo;

                    public class B {
                        public B() {}
                    }
                    """.trimIndent()
                ))
            })
        }

        it("should not allow single-type-import declarations for a package that doesn't exist even if its a string prefix of some package that does exist") {
            assertFailsWith(SingleTypeImportDeclarationDetectedForNonExistentPackage::class, {
                subject(listOf(
                    """
                    import fo.C;

                    public class A {
                        public A() {}
                    }
                    """.trimIndent(),
                    """
                    package foo;

                    public class B {
                        public B() {}
                    }
                    """.trimIndent()
                ))
            })
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
                    """.trimIndent()
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
                """.trimIndent()
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

    it("should not allow a class to extend an interface") {
        assertFailsWith(ClassExtendsNonClass::class, {
            subject(listOf(
                """
                    public class A extends B {
                        public A() {}
                    }
                """.trimIndent(),
                """
                    public interface B {
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow a class to implement another class") {
        assertFailsWith(ClassImplementsNonInterface::class, {
            subject(listOf(
                """
                    public class A implements B {
                        public A() {}
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

    it("should not allow interfaces to be repeated in an implements clause of a class declaration") {
        assertFailsWith(ClassImplementsAnInterfaceMoreThanOnce::class, {
            subject(listOf(
                """
                    import test.*;

                    public class A implements B, C, test.B {
                        public A() {}
                    }
                """.trimIndent(),
                """
                    package test;
                    public interface B {
                        public void sayHiB();
                    }
                """.trimIndent(),
                """
                    public interface C {
                        public void sayHiC();
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow non-abstract classes that don't implement all methods of their interfaces") {
        assertFailsWith(UnimplementedInterfaceMethodException::class, {
            subject(listOf(
                """
                    public class A implements B {
                        public A() {}
                        public int toInt() {
                            return 1;
                        }
                    }
                """.trimIndent(),
                """
                    public interface B extends C {
                        public void foo(A a, B[] b);
                        public int toInt();
                    }
                """.trimIndent(),
                """
                    public interface C {
                        public void sayHiC(B b);
                    }
                """.trimIndent()
            ))
        })
    }

    it("should reject classes where abstract method shadows concrete implementation of interface method") {
        assertFailsWith(UnimplementedInterfaceMethodException::class, {
            subject(listOf(
                """
                    public class A extends B implements Foo {
                        public A() {}
                    }
                """.trimIndent(),
                """
                    public abstract class B extends C {
                        public B() {}
                        public abstract int foo();
                    }
                """.trimIndent(),
                """
                    public class C {
                        public C() {}
                        public int foo() {
                            return 1;
                        }
                    }
                """.trimIndent(),
                """
                    public interface Foo {
                        public int foo();
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow non-abstract classes that don't implement all methods of their abstract parents") {
        assertFailsWith(UnimplementedAbstractMethodException::class, {
            subject(listOf(
                """
                    public class A extends B {
                        public A() {}
                        public int toInt() {
                            return 1;
                        }
                    }
                """.trimIndent(),
                """
                    abstract public class B {
                        public B() {}
                        abstract public void foo();
                    }
                """.trimIndent()
            ))
        })
    }

    it("should allow undirected cycles in the interface hierarchies that aren't directed cycles") {
        subject(listOf(
            """
                public class D implements A {
                    public D() {}
                    public void foo() {}
                }
            """.trimIndent(),
            """
                public interface A extends B, C{}
            """.trimIndent(),
            """
                public interface B extends C {}
            """.trimIndent(),
            """
                public interface C {
                    public void foo();
                }
            """.trimIndent()
        ))
    }

    it("should not allow interfaces to be repeated in the extends clause of an interface declaration") {
        assertFailsWith(InterfaceExtendsAnotherMoreThanOnce::class, {
            subject(listOf(
                """
                    public interface A extends B, C, B {
                    }
                """.trimIndent(),
                """
                    public interface B {
                        public void sayHiB();
                    }
                """.trimIndent(),
                """
                    public interface C {
                        public void sayHiC();
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow a class to extend a final class") {
        assertFailsWith(ClassExtendsFinalSuperClass::class, {
            subject(listOf(
                """
                    public class A extends B implements C {
                        public A() {}
                        public void sayHiC() {}
                    }
                """.trimIndent(),
                """
                    public final class B {
                        public B() {}
                        public void sayHiB() {}
                    }
                """.trimIndent(),
                """
                    public interface C {
                        public void sayHiC();
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow an interface to extend a class") {
        assertFailsWith(InterfaceExtendsNonInterface::class, {
            subject(listOf(
                """
                    public interface A extends B {
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

    it("should not allow cycles in the interface hierarchy") {
        assertFailsWith(InterfaceHierarchyIsCyclic::class, {
            subject(listOf(
                """
                    public interface A extends B {
                        public void sayHiA();
                    }
                """.trimIndent(),
                """
                    public interface B extends C {
                        public void sayHiB();
                    }
                """.trimIndent(),
                """
                    public interface C extends A {
                        public void sayHiC();
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow an interface to extend itself") {
        assertFailsWith(InterfaceHierarchyIsCyclic::class, {
            subject(listOf(
                """
                    public interface A extends A {
                        public void sayHi();
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow cycles in the class hierarchy") {
        assertFailsWith(ClassHierarchyIsCyclic::class, {
            subject(listOf(
                """
                    public class A extends B {
                        public A() {}
                    }
                """.trimIndent(),
                """
                    public class B extends C {
                        public B() {}
                    }
                """.trimIndent(),
                """
                    public class C extends A {
                        public C() {}
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow a class to extend itself") {
        assertFailsWith(ClassHierarchyIsCyclic::class, {
            subject(listOf(
                """
                    public class A extends A {
                        public A() {}
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow a class to declare two methods with the same signature") {
        assertFailsWith(DuplicateMethodsDetectedInClass::class, {
            subject(listOf(
                """
                    public class A {
                        public A() {}

                        public static void foo(int i, B b, B[] rest) {}
                        public static void foo(int i2, B b2, B[] rest2) {}
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

    it("should not allow an interface to declare two methods with the same signature") {
        assertFailsWith(DuplicateMethodsDetectedInInterface::class, {
            subject(listOf(
                """
                    public interface A {
                        public void foo(int i, B b, B[] rest);
                        public void foo(int i2, B b2, B[] rest2);
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

    it("should not allow a class to declare two or more constructors with the same signatures") {
        assertFailsWith(DuplicateConstructorsDetectedInClass::class, {
            subject(listOf(
                """
                    package horse;
                    import cow.B;

                    public class A {
                        public A(int i, cow.B b, B[] rest) {}
                        public A(int i2, B b2, cow.B[] rest2) {}
                    }
                """.trimIndent(),
                """
                    package cow;

                    public class B {
                        public B() {}
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow a class to override a method D with E if the return types of D and E are different") {
        assertFailsWith(TwoMethodsInClassHierarchyWithSameSignatureButDifferentReturnTypes::class, {
            subject(listOf(
                """
                    public class A extends B {
                        public A() {}

                        public void toInt(char b, int[] holymoly) {}
                    }
                """.trimIndent(),
                """
                    public class B {
                        public B() {}

                        public int toInt(char k, int[] j) {}
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow an interface to override a method D with E if the return types of D and E are different") {
        assertFailsWith(TwoMethodsInInterfaceHierarchyWithSameSignatureButDifferentReturnTypes::class, {
            subject(listOf(
                """
                    public interface A extends B {
                        public void toInt(char b, int[] holymoly);
                    }
                """.trimIndent(),
                """
                    public interface B {
                        public int toInt(char k, int[] j);
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow non-abstract classes that inherit but don't implement abstract methods") {
        assertFailsWith(UnimplementedAbstractMethodException::class, {
            subject(listOf(
                """
                    public class A extends B {
                        public A() {}
                    }
                """.trimIndent(),
                """
                    public abstract class B {
                        public B() {}
                        abstract public void toInt();
                    }
                """.trimIndent()
                ))
        })
    }

    it("should allow an interface method to override a superinterface method if return types are same") {
        subject(listOf(
            """
                public interface A {
                    public int a();
                }
            """.trimIndent(),
            """
                public interface B extends A {
                    public int a();
                }
            """.trimIndent()
        ))
    }

    it("should not allow a non-static method to replace a static method") {
        assertFailsWith(IllegalMethodReplacement::class, {
            subject(listOf(
                """
                    public class A {
                        public A() {}
	                    public static void foo() {}
                    }
                """.trimIndent(),
                """
                    public class B extends A {
                        public B() {}
                        public void foo() {}
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow a static method to replace a non-static method") {
        assertFailsWith(IllegalMethodReplacement::class, {
            subject(listOf(
                """
                    public class A {
                        public A() {}
	                    public void foo() {}
                    }
                """.trimIndent(),
                """
                    public class B extends A {
                        public B() {}
                        public static void foo() {}
                    }
                """.trimIndent()
            ))
        })
    }



    it("should not allow a protected method to override a public method in classes") {
        assertFailsWith(IllegalMethodReplacement::class, {
            subject(listOf(
                """
                    public class A {
                        public A() {}
	                    public void foo() {}
                    }
                """.trimIndent(),
                """
                    public class B extends A {
                        public B() {}
                        protected void foo() {}
                    }
                """.trimIndent()
            ))
        })
    }

    it("should not allow a class method to replace a final method") {
        assertFailsWith(IllegalMethodReplacement::class, {
            subject(listOf(
                """
                    public class A {
                        public A() {}
	                    final public void foo() {}
                    }
                """.trimIndent(),
                """
                    public class B extends A {
                        public B() {}
                        public void foo() {}
                    }
                """.trimIndent()
            ))
        })
    }

    it("shouldn't require abstract classes to implement all interface methods") {
        subject(listOf(
            """
                abstract public class A implements B {
                    public A() {}
                }
            """.trimIndent(),
            """
                public interface B {
                    public void foo();
                }
            """.trimIndent()
        ))
    }

    it("should not allow the prefix of a fully qualified type to be a type") {
        assertFailsWith(NameResolutionError::class, {
            subject(listOf(
                """
                package A;

                public class A {
                    public A() {}

                    public void test() {
                        new A.A();
                    }
                }
            """.trimIndent()
            ))
        })
    }

    it("should require interfaces without super-interface to incorrectly override Object methods") {
        assertFailsWith(TwoMethodsInInterfaceHierarchyWithSameSignatureButDifferentReturnTypes::class, {
            subject(listOf(
                """
                    public interface A {
                        public void toString();
                    }
                """.trimIndent(),
                """
                    package java.lang;

                    public class Object {
                        public Object() {}

                        public String toString() {}
                    }
                """.trimIndent(),
                """
                    package java.lang;

                    public class String {
                        public String() {}
                    }
                """.trimIndent()
            ))
        })
    }
})

package oj.scanner

import oj.models.NFA
import oj.models.TokenType
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.subject.SubjectSpek
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(JUnitPlatform::class)
object SimpleCalculatorSpec: SubjectSpek<Scanner>({
    subject {
        var baseDfas = BASE_DFA_NAMES
                .keys
                .map { NFA.deserialize("gen/$it.dfa", oj.scanner.ALPHABET, it) }
                .toSet()
        val scannerDfa = NFA.deserialize("gen/$SCANNER_DFA.dfa", oj.scanner.ALPHABET, "")
        Scanner(scannerDfa, baseDfas)
    }

    describe("Comments") {

        it("should tokenize empty // comments") {
            val comment = "//"
            val tokens = subject.tokenize(comment)

            assertTrue(tokens.isNotEmpty())
            assertEquals(TokenType.COMMENT, tokens[0].type)
            assertEquals(comment, tokens[0].lexeme)
        }

        it("can tokenize // comments without a '\\n' character at the end") {
            val comment = "// Hi, I'm a random comment, yay!"
            val tokens = subject.tokenize(comment)
            assertEquals(1, tokens.size)
            assertEquals(TokenType.COMMENT, tokens[0].type)
            assertEquals(comment, tokens[0].lexeme)
        }

        it("can tokenize // comments with a '\\n' character at the end") {
            val comment = "// Hi, I'm a random comment, yay!"
            val tokens = subject.tokenize("$comment\n")
            assertTrue(tokens.isNotEmpty())
            assertEquals(TokenType.COMMENT, tokens[0].type)
            assertEquals(comment, tokens[0].lexeme)
        }

        it("can tokenize // comments with other comments inside of them") {
            val comment = "// Hi, // I'm a random /* comment */, yay!"
            val tokens = subject.tokenize("$comment\n")
            assertTrue(tokens.isNotEmpty())
            assertEquals(TokenType.COMMENT, tokens[0].type)
            assertEquals(comment, tokens[0].lexeme)
        }

        it("can tokenize /* */ comments that are single line") {
            val comment = "/* Salutations, compadre! */"
            val tokens = subject.tokenize(comment)
            assertEquals(1, tokens.size)
            assertEquals(TokenType.COMMENT, tokens[0].type)
            assertEquals(comment, tokens[0].lexeme)
        }

        it("can tokenize /* */ comments that are multi-line") {
            val comment = """
                |/* Salutations, compadre
                |I come bearing good news
                |You have a brand new child!
                |I bought it for only $12.99 at WalMart!
                |Huzzah!
                |*/
            """.trimMargin()

            val tokens = subject.tokenize(comment)
            assertEquals(1, tokens.size)
            assertEquals(TokenType.COMMENT, tokens[0].type)
            assertEquals(comment, tokens[0].lexeme)
        }

        it("can tokenize /* */ comments that contain other comments") {
            val comment = """
                |/* Salutations, compadre
                |// You have a new ring
                |/* What am I doing? // Can't close this multiline comment yet
                |Huzzah!
                |*/
            """.trimMargin()

            val tokens = subject.tokenize(comment)
            assertEquals(1, tokens.size)
            assertEquals(TokenType.COMMENT, tokens[0].type)
            assertEquals(comment, tokens[0].lexeme)
        }

        it("should parse nested /* */ comments correctly/") {
            val commentLines = listOf(
                "/* Salutations, compadre",
                "/* What am I doing? // Can't close this multiline comment yet.",
                " Sych, I closed it */",
                "Huzzah!",
                "*/"
            )

            val comment = commentLines.joinToString("\n")

            val tokens = subject.tokenize(comment)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            // Should parse more than one tokens
            assertTrue(nonWhitespaceTokens.size > 1)

            val commentToken = nonWhitespaceTokens[0]
            assertEquals(TokenType.COMMENT, commentToken.type)

            val firstComment = commentLines.slice(IntRange(0, 2)).joinToString("\n")
            assertEquals(firstComment, commentToken.lexeme)
        }

        it("should parse javadoc comments as regular comments") {
            val comment = """
                |/**
                | * @description This is the most amazing method you'll ever execute in your life
                | *
                | * @param {Logger} logger - A goddamn logger
                | */
            """.trimMargin()

            val code = comment + "\n" + """
                |public void doSomethingAmazing(Logger logger) {
                |  System.out.println("Boom!");
                |  System.out.println("Has your live been changed yet");
                |}
            """.trimIndent()

            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            assertTrue(nonWhitespaceTokens.isNotEmpty())
            assertEquals(TokenType.COMMENT, nonWhitespaceTokens[0].type)
            assertEquals(comment, nonWhitespaceTokens[0].lexeme)
        }

    }

    describe("Identifiers") {

        it("can tokenize _ as an identifier") {
            val tokens = subject.tokenize("_")

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals("_", tokens[0].lexeme)
        }

        it("can tokenize $ as an identifier") {
            val tokens = subject.tokenize("$")

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals("$", tokens[0].lexeme)
        }

        it("can tokenize identifiers starting with _") {
            val identifierName = "_yayyyy"
            val tokens = subject.tokenize(identifierName)

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(identifierName, tokens[0].lexeme)
        }

        it("can tokenize identifiers starting with $") {
            val identifierName = "\$yayyyy"
            val tokens = subject.tokenize(identifierName)

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(identifierName, tokens[0].lexeme)
        }

        it("can tokenize identifiers starting with a lowercase letter") {
            val identifierName = "yayyyy"
            val tokens = subject.tokenize(identifierName)

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(identifierName, tokens[0].lexeme)
        }

        it("can tokenize identifiers starting with an uppercase letter") {
            val identifierName = "Yayyyy"
            val tokens = subject.tokenize(identifierName)

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(identifierName, tokens[0].lexeme)
        }

        it("should not tokenize 'identifiers' starting with a number") {
            val identifierName = "ayyyy"
            val tokens = subject.tokenize("1$identifierName")

            assertEquals(2, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[1].type)
            assertEquals(identifierName, tokens[1].lexeme)
        }

        it("can tokenize identifiers containing numbers") {
            val identifierName = "im2cool4school"
            val tokens = subject.tokenize(identifierName)

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(identifierName, tokens[0].lexeme)
        }

        it( "can tokenize identifiers containing $ in the middle") {
            val identifierName = "im2cool4\$chool"
            val tokens = subject.tokenize(identifierName)

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(identifierName, tokens[0].lexeme)
        }

        it( "can tokenize identifiers containing a _ in the middle") {
            val identifierName = "sssssssssssssss_im_a_snek_sssss"
            val tokens = subject.tokenize(identifierName)

            assertEquals(1, tokens.size)
            assertEquals(TokenType.IDENTIFIER, tokens[0].type)
            assertEquals(identifierName, tokens[0].lexeme)
        }

    }

    describe("Keywords") {
        it("should recognize all keywords") {
            val keywords = listOf(
                "abstract", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else", "extends", "final", "finally", "float",
                "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
                "native", "new", "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
                "try", "void", "volatile", "while"
            )

            val code = keywords.joinToString(" ")
            val tokens = subject.tokenize(code)

            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            assertTrue(nonWhitespaceTokens.all({ it.type == TokenType.KEYWORD }))
        }
    }

    describe("Literals") {

        describe("Booleans") {

            it ("should tokenize boolean 'true'") {
                val tokens = subject.tokenize("true")
                assertEquals(tokens.size, 1)

                val tokenLexeme = tokens[0].lexeme
                val tokenType = tokens[0].type

                assertEquals(TokenType.BOOLEAN, tokenType)
                assertEquals("true", tokenLexeme)
            }

            it ("should tokenize boolean 'false'") {
                val tokens = subject.tokenize("false")
                assertEquals(tokens.size, 1)

                val tokenLexeme = tokens[0].lexeme
                val tokenType = tokens[0].type

                assertEquals(TokenType.BOOLEAN, tokenType)
                assertEquals("false", tokenLexeme)
            }

            it ("should tokenize a mixture of booleans correctly") {
                val tokens = subject.tokenize("true false true false false false")
                val booleanTokens = tokens.filter({ it.type == TokenType.BOOLEAN })

                assertEquals(listOf("true", "false", "true", "false", "false", "false"), booleanTokens.map{ it.lexeme })
            }

        }

        describe("Characters") {

            it("should tokenize all ASCII letters") {
                val letters = (
                    IntRange('a'.toInt(), 'z'.toInt()) +
                    IntRange('A'.toInt(), 'Z'.toInt())
                )
                    .map({ "'${it.toChar()}'" })

                val code = letters.joinToString("")

                val tokens = subject.tokenize(code)
                val numberOfLetters = 26

                assertEquals(numberOfLetters * 2, tokens.size)
                assertTrue(tokens.all({ it.type == TokenType.CHARACTER }))
                assertEquals(letters, tokens.map({ it.lexeme }))
            }

            it("should tokenize all ASCII non-letters") {
                val specialChars = (
                    IntRange(' '.toInt(), '@'.toInt()) +
                    IntRange('['.toInt(), '`'.toInt()) +
                    IntRange('{'.toInt(), '~'.toInt())
                )
                    .map({ it.toChar() })
                    .filter({ it != '\'' && it != '\\'})
                    .map({ "'$it'" })


                assertEquals(41, specialChars.size)

                val code = specialChars.joinToString(" ")
                val tokens = subject.tokenize(code)
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(specialChars.size, nonWhitespaceTokens.size)
                assertTrue(nonWhitespaceTokens.all({ it.type == TokenType.CHARACTER }))
                assertEquals(specialChars, nonWhitespaceTokens.map({ it.lexeme }))
            }

            it("should reject the empty character") {
                val code = "''"

                assertFailsWith(ScannerError::class) {
                    subject.tokenize(code)
                }
            }

            it("should reject character literals with more than one character") {
                val code = "'abc'"

                assertFailsWith(ScannerError::class) {
                    subject.tokenize(code)
                }
            }

            it("should tokenize '\\b'") {
                val code = """'\b'"""
                val tokens = subject.tokenize(code)

                assertTrue(tokens.isNotEmpty())
                assertEquals(TokenType.CHARACTER, tokens[0].type)
                assertEquals(code, tokens[0].lexeme)
            }

            it("should tokenize '\\t'") {
                val code = """'\t'"""
                val tokens = subject.tokenize(code)

                assertTrue(tokens.isNotEmpty())
                assertEquals(TokenType.CHARACTER, tokens[0].type)
                assertEquals(code, tokens[0].lexeme)
            }

            it("should tokenize '\\n'") {
                val code = """'\n'"""
                val tokens = subject.tokenize(code)

                assertTrue(tokens.isNotEmpty())
                assertEquals(TokenType.CHARACTER, tokens[0].type)
                assertEquals(code, tokens[0].lexeme)
            }

            it("should tokenize '\\f'") {
                val code = """'\f'"""
                val tokens = subject.tokenize(code)

                assertTrue(tokens.isNotEmpty())
                assertEquals(TokenType.CHARACTER, tokens[0].type)
                assertEquals(code, tokens[0].lexeme)
            }

            it("should tokenize '\\r'") {
                val code = """'\r'"""
                val tokens = subject.tokenize(code)

                assertTrue(tokens.isNotEmpty())
                assertEquals(TokenType.CHARACTER, tokens[0].type)
                assertEquals(code, tokens[0].lexeme)
            }

            it("should tokenize '\\\"'") {
                val code = """'\"'"""
                val tokens = subject.tokenize(code)

                assertTrue(tokens.isNotEmpty())
                assertEquals(TokenType.CHARACTER, tokens[0].type)
                assertEquals(code, tokens[0].lexeme)
            }

            it("should tokenize '\\'") {
                val code = """'\''"""
                val tokens = subject.tokenize(code)

                assertTrue(tokens.isNotEmpty())
                assertEquals(TokenType.CHARACTER, tokens[0].type)
                assertEquals(code, tokens[0].lexeme)
            }

            it("should tokenize '\\\\'") {
                val code = """'\\'"""
                val tokens = subject.tokenize(code)

                assertTrue(tokens.isNotEmpty())
                assertEquals(TokenType.CHARACTER, tokens[0].type)
                assertEquals(code, tokens[0].lexeme)
            }

            it("should reject unknown escape characters") {
                val code = """'\h'"""

                assertFailsWith(ScannerError::class) {
                    subject.tokenize(code)
                }
            }

        }

        describe("String") {

            it("should tokenize strings with ASCII letters") {
                val letters = (
                    IntRange('a'.toInt(), 'z'.toInt()) +
                    IntRange('A'.toInt(), 'Z'.toInt())
                )
                    .map({ it.toChar() })

                val string = "\"" + letters.joinToString("") + "\""
                val tokens = subject.tokenize("String x = $string;")
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(5, nonWhitespaceTokens.size)
                assertEquals(TokenType.STRING, nonWhitespaceTokens[3].type)
                assertEquals(string, nonWhitespaceTokens[3].lexeme)
            }

            it("should tokenize strings with ASCII non-letters") {
                val specialChars = (
                    IntRange(' '.toInt(), '@'.toInt()) +
                    IntRange('['.toInt(), '`'.toInt()) +
                    IntRange('{'.toInt(), '~'.toInt())
                )
                    .map({ it.toChar() })
                    .filter({ it != '\'' && it != '\\' && it != '\"'})

                val string = "\"" + specialChars.joinToString("") + "\""
                val code = "String x = $string;"
                val tokens = subject.tokenize(code)
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(5, nonWhitespaceTokens.size)
                assertEquals(TokenType.STRING, nonWhitespaceTokens[3].type)
                assertEquals(string, nonWhitespaceTokens[3].lexeme)
            }

            it("should tokenize strings with escape characters") {
                val string = """"Hi! \n \b\n bye! \t\f\r\" \' good bye""""
                val tokens = subject.tokenize("String ayush = $string;")
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(5, nonWhitespaceTokens.size)
                assertEquals(TokenType.STRING, nonWhitespaceTokens[3].type)
                assertEquals(string, nonWhitespaceTokens[3].lexeme)
            }

            it("should reject strings with unknown escape characters") {
                val string = """"hurrrrrr \z\g""""

                assertFailsWith(ScannerError::class) {
                    subject.tokenize("String ayush = $string")
                }
            }

            it("should tokenize \"\"") {
                val string = "\"\""
                val tokens = subject.tokenize("\"a\" + $string + \"c\"")
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(5, nonWhitespaceTokens.size)
                assertEquals(TokenType.STRING, nonWhitespaceTokens[2].type)
                assertEquals(string, nonWhitespaceTokens[2].lexeme)
            }

            it("should reject multi-line strings") {
                val string = "\"Hi, guys!\n My name is Raman\""

                assertFailsWith(ScannerError::class) {
                    subject.tokenize("\"a\" + $string + \"c\"")
                }
            }

        }

        describe("Integer") {

            it("should tokenize the number 0") {
                val integer = "0"
                val tokens = subject.tokenize("int x = $integer;")
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(5, nonWhitespaceTokens.size)
                assertEquals(integer, nonWhitespaceTokens[3].lexeme)
                assertEquals(TokenType.INTEGER, nonWhitespaceTokens[3].type)
            }

            it("should tokenize oct literals as two numbers") {
                val integer = "0123"
                val tokens = subject.tokenize("int horse = $integer * 2;")
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(8, nonWhitespaceTokens.size)

                assertEquals("0", nonWhitespaceTokens[3].lexeme)
                assertEquals(TokenType.INTEGER, nonWhitespaceTokens[3].type)

                assertEquals("123", nonWhitespaceTokens[4].lexeme)
                assertEquals(TokenType.INTEGER, nonWhitespaceTokens[4].type)
            }

            it("should tokenize negative integers as two tokens") {
                val integer = "12345"
                val tokens = subject.tokenize("2 * -$integer")
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(4, nonWhitespaceTokens.size)
                assertEquals(integer, nonWhitespaceTokens[3].lexeme)
                assertEquals(TokenType.INTEGER, nonWhitespaceTokens[3].type)
            }

            it("should tokenize integer literals >= 2^31") {
                val integer = "214748364800000"
                val tokens = subject.tokenize("int ab = 2 * ($integer + 1)")
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(10, nonWhitespaceTokens.size)
                assertEquals(integer, nonWhitespaceTokens[6].lexeme)
                assertEquals(TokenType.INTEGER, nonWhitespaceTokens[6].type)
            }

        }

        describe("Null") {

            it("should tokenize null") {
                val code = "x = null"
                val tokens = subject.tokenize(code)
                val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

                assertEquals(3, nonWhitespaceTokens.size)

                val nullToken = nonWhitespaceTokens[2]

                assertEquals(TokenType.NULL, nullToken.type)
                assertEquals("null", nullToken.lexeme)
            }
        }
    }

    describe("Operators") {
        it("should tokenize arithmetic operators") {
            val code = "-2 * x + 87 % x - (x/7)"
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            val assertIsOp = { i: Int, lexeme: String ->
                assertEquals(TokenType.OPERATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertIsOp(0, "-")
            assertIsOp(2, "*")
            assertIsOp(4, "+")
            assertIsOp(6, "%")
            assertIsOp(8, "-")
            assertIsOp(11, "/")
        }

        it("should tokenize comparison operators") {
            val code = "return (x<87) && (x>42) && (x<=86) && (x>=43) && (x==51) && (x!=52) ;"
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            val assertIsOp = { i: Int, lexeme: String ->
                assertEquals(TokenType.OPERATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertIsOp(3, "<")
            assertIsOp(6, "&&")
            assertIsOp(9, ">")
            assertIsOp(12, "&&")
            assertIsOp(15, "<=")
            assertIsOp(18, "&&")
            assertIsOp(21, ">=")
            assertIsOp(24, "&&")
            assertIsOp(27, "==")
            assertIsOp(30, "&&")
            assertIsOp(33, "!=")
        }

        it("should tokenize eager boolean operators") {
            val code = "return (x & true) | !x;"
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            val assertIsOp = { i: Int, lexeme: String ->
                assertEquals(TokenType.OPERATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertIsOp(3, "&")
            assertIsOp(6, "|")
            assertIsOp(7, "!")
        }

        it("should tokenize lazy boolean operators") {
            val code = "return (x && true) || x;"
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            val assertIsOp = { i: Int, lexeme: String ->
                assertEquals(TokenType.OPERATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertIsOp(3, "&&")
            assertIsOp(6, "||")
        }

        it("should tokenize '--' as an operator") {
            val code = "a--b"
            val tokens = subject.tokenize(code)

            assertEquals(3, tokens.size)
            assertEquals(TokenType.OPERATOR, tokens[1].type)
            assertEquals("--", tokens[1].lexeme)
        }
    }

    describe("Separators") {
        it("should tokenize () as separators") {
            val code = "1 + (2 + test - (10 - 5)) + (2)"
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            assertEquals(17, nonWhitespaceTokens.size)

            val assertSeparator = { i: Int, lexeme: String ->
                assertEquals(TokenType.SEPARATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertSeparator(2, "(")
            assertSeparator(7, "(")
            assertSeparator(11, ")")
            assertSeparator(12, ")")
            assertSeparator(14, "(")
            assertSeparator(16, ")")
        }

        it("should tokenize {} as separators") {
            val code = """
                |public static void main() {
                |  if (x == 10) {
                |    System.out.println(10);
                |  }
                |}
            """.trimMargin()
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            assertEquals(25, nonWhitespaceTokens.size)

            val assertSeparator = { i: Int, lexeme: String ->
                assertEquals(TokenType.SEPARATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertSeparator(6, "{")
            assertSeparator(13, "{")
            assertSeparator(23, "}")
            assertSeparator(24, "}")
        }

        it("should tokenize [] as separators") {
            val code = """
                |String[] args[], a = new String[20][getSize()], "hi";
            """.trimMargin()
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            assertEquals(22, nonWhitespaceTokens.size)

            val assertSeparator = { i: Int, lexeme: String ->
                assertEquals(TokenType.SEPARATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertSeparator(1, "[")
            assertSeparator(2, "]")
            assertSeparator(4, "[")
            assertSeparator(5, "]")
            assertSeparator(11, "[")
            assertSeparator(13, "]")
            assertSeparator(14, "[")
            assertSeparator(18, "]")
        }

        it("should tokenize , as a separator") {
            val code = """
                |final String x = (false, callHorse("come,", "black beauty", "my darling steed"));
            """.trimMargin()
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            assertEquals(17, nonWhitespaceTokens.size)

            val assertSeparator = { i: Int, lexeme: String ->
                assertEquals(TokenType.SEPARATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertSeparator(6, ",")
            assertSeparator(10, ",")
            assertSeparator(12, ",")

            assertEquals(
                3,
                nonWhitespaceTokens
                    .filter({ it.type == TokenType.SEPARATOR })
                    .filter({ it.lexeme == ","})
                    .size
            )
        }

        it("should tokenize . as a separator") {
            val code = """
                |public static void main(String[] args) {
                |   if (Logger.out != null) {
                |       Logger.out.stdout.println(getInstance().getHorse().neigh().toString());
                |   }
                |}
            """.trimMargin()
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            assertEquals(47, nonWhitespaceTokens.size)

            val assertSeparator = { i: Int, lexeme: String ->
                assertEquals(TokenType.SEPARATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertSeparator(14, ".")
            assertSeparator(21, ".")
            assertSeparator(23, ".")
            assertSeparator(25, ".")
            assertSeparator(31, ".")
            assertSeparator(35, ".")
            assertSeparator(39, ".")
        }

        it("should tokenize ; as a separator") {
            val code = """
                |final String hi = "hi";
                |final String bye = "bye";
                |return hi + bye;
            """.trimMargin()
            val tokens = subject.tokenize(code)
            val nonWhitespaceTokens = tokens.filter({ it.type != TokenType.WHITESPACE })

            assertEquals(17, nonWhitespaceTokens.size)

            val assertSeparator = { i: Int, lexeme: String ->
                assertEquals(TokenType.SEPARATOR, nonWhitespaceTokens[i].type)
                assertEquals(lexeme, nonWhitespaceTokens[i].lexeme)
            }

            assertSeparator(5, ";")
            assertSeparator(11, ";")
            assertSeparator(16, ";")
        }
    }
})

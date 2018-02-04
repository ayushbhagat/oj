package oj.scanner

import oj.models.NFA
import oj.models.Token
import oj.models.TokenType
import org.jetbrains.spek.api.Spek
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
            val comment = """/* Salutations, compadre
                |I come bearing good news
                |You have a brand new child!
                |I bought it for only $12.99 at WalMart!
                |Huzzah!
            |*/""".trimMargin()

            val tokens = subject.tokenize(comment)
            assertEquals(1, tokens.size)
            assertEquals(TokenType.COMMENT, tokens[0].type)
            assertEquals(comment, tokens[0].lexeme)
        }

        it("can tokenize /* */ comments that contain other comments") {
            val comment = """/* Salutations, compadre
                |// You have a new ring
                |/* What am I doing? // Can't close this multiline comment yet
                |Huzzah!
            |*/""".trimMargin()

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

        it( "can tokenize identifiers containign _ in the middle") {
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

            it("should tokenize all ascii letters") {
                val lowerCaseLetters = IntRange('a'.toInt(), 'z'.toInt()).map({ "'" + it.toChar() + "'" })
                val upperCaseLetters = lowerCaseLetters.map({ it.toUpperCase() })
                val letters = lowerCaseLetters + upperCaseLetters

                val code = letters.joinToString("")

                val tokens = subject.tokenize(code)
                val numberOfLetters = 26

                assertEquals(numberOfLetters * 2, tokens.size)
                assertTrue(tokens.all({ it.type == TokenType.CHARACTER }))
                assertEquals(letters, tokens.map({ it.lexeme }))
            }

            it("should tokenize all special characters") {
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

            it("should reject unknown special characters") {
                val code = """'\h'"""

                assertFailsWith(ScannerError::class) {
                    subject.tokenize(code)
                }
            }

        }

        describe("String") {}

        describe("Integer") {}

        describe("Null") {}
    }

    describe("Operators") {}

    describe("Separators") {}
})


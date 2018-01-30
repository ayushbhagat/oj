package scanner

import models.NFA
import models.NFAType
import models.Token
import models.TokenType
import java.io.File

const val SCANNER_DFA = "scanner"

val BACKSLASH_CHARACTERS = mutableMapOf("\b" to "\\b", "\t" to "\\t", "\n" to "\\n", "\r" to "\\r")

val ALPHABET =
        (32..126).map { it.toChar().toString() }
                .toMutableSet()
                .union(BACKSLASH_CHARACTERS.values)

val KEYWORDS = setOf(
        "abstract", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
        "native", "new", "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while")

val BOOLEAN_LITERALS = setOf("true", "false")

class Scanner(private val filePath: String, private var dfa: NFA, private val baseDfas: Set<NFA>) {
    init {
        if (!dfa.isDfa) {
            dfa = dfa.toDFA()
        }
    }

    /**
     * Return a tokenization of the input program.
     * @return The list of tokens that represents the input program.
     */
    fun tokenize(): List<Token> {
        val inputStream = File(filePath).inputStream()
        var inputFileString = inputStream.bufferedReader().use { it.readText() }
        val tokens: MutableList<Token> = mutableListOf()
        var currentState = dfa.startState
        var lexemeStartIndex = 0
        var lastFinalState = ""
        var lastFinalStateIndex = -1
        var index = 0
        while (index < inputFileString.length) {
            val inputCharacter =
                    if (BACKSLASH_CHARACTERS.contains(inputFileString[index].toString()))
                        BACKSLASH_CHARACTERS[inputFileString[index].toString()]
                    else
                        inputFileString[index].toString()
            currentState = dfa.getNextState(currentState, inputCharacter.orEmpty())
            if (currentState.isEmpty()) {
                if (lastFinalState.isEmpty()) {
                    throw ScannerError()
                } else {
                    val lexeme =
                            inputFileString.substring(lexemeStartIndex, lastFinalStateIndex + 1)
                    tokens.add(Token(getTokenType(lastFinalState, lexeme), lexeme))
                    println("${tokens[tokens.size - 1].type} ${tokens[tokens.size - 1].lexeme}")
                    index = lastFinalStateIndex
                    currentState = dfa.startState
                    lexemeStartIndex = lastFinalStateIndex + 1
                    lastFinalState = ""
                    lastFinalStateIndex = -1
                }
            } else {
                if (dfa.isFinalState(currentState)) {
                    lastFinalState = currentState
                    lastFinalStateIndex = index
                }
            }
            index++
        }
        if (dfa.isFinalState(currentState)) {
            val lexeme = inputFileString.substring(lexemeStartIndex, lastFinalStateIndex + 1)
            tokens.add(Token(getTokenType(lastFinalState, lexeme), lexeme))
        } else {
            throw ScannerError()
        }
        return tokens
    }

    /**
     * Return the token type.
     * @param finalState The final state to use to determine the token type just read.
     * @param currentLexeme The lexeme that is also used to determin the token type.
     */
    private fun getTokenType(finalState: String, currentLexeme: String): TokenType {
        val states = finalState
                .substring(finalState.indexOf(SCANNER_DFA) + SCANNER_DFA.length)
                .split("_")
        val dfaType = baseDfas
                .find { baseDfa -> states.find { baseDfa.isFinalState(it) } != null }
                ?.type
        return when (dfaType) {
            NFAType.COMMENT -> TokenType.COMMENT
            NFAType.IDENTIFIER -> {
                when {
                    KEYWORDS.contains(currentLexeme) -> TokenType.KEYWORD
                    BOOLEAN_LITERALS.contains(currentLexeme) -> TokenType.BOOLEAN
                    currentLexeme.equals("null") -> TokenType.NULL
                    else -> TokenType.IDENTIFIER
                }
            }
            NFAType.CHARACTER -> TokenType.CHARACTER
            NFAType.STRING -> TokenType.STRING
            NFAType.INTEGER -> TokenType.INTEGER
            NFAType.OPERATOR -> TokenType.OPERATOR
            NFAType.SEPARATOR -> TokenType.SEPARATOR
            NFAType.WHITESPACE -> TokenType.WHITESPACE
            else -> throw ScannerError()
        }
    }
}
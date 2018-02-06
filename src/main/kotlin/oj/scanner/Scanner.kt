package oj.scanner

import oj.models.NFA
import oj.models.Token
import oj.models.TokenType

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

val BASE_DFA_NAMES = mapOf(
        "comment" to TokenType.COMMENT,
        "identifier" to TokenType.IDENTIFIER,
        "character" to TokenType.CHARACTER,
        "string" to TokenType.STRING,
        "integer" to TokenType.INTEGER,
        "operator" to TokenType.OPERATOR,
        "separator" to TokenType.SEPARATOR,
        "whitespace" to TokenType.WHITESPACE)

class Scanner(private var dfa: NFA, private val baseDfas: Set<NFA>) {
    init {
        if (!dfa.isDfa) {
            dfa = dfa.toDFA()
        }
    }

    /**
     * Return a tokenization of the input program.
     * @return The list of tokens that represents the input program.
     */
    fun tokenize(code: String): List<Token> {
        val tokens: MutableList<Token> = mutableListOf()
        var currentState = dfa.startState
        var lexemeStartIndex = 0
        var lastFinalState = ""
        var lastFinalStateIndex = -1
        var index = 0
        while (index < code.length) {
            val inputCharacter =
                    if (BACKSLASH_CHARACTERS.contains(code[index].toString()))
                        BACKSLASH_CHARACTERS[code[index].toString()]
                    else
                        code[index].toString()
            currentState = dfa.getNextState(currentState, inputCharacter.orEmpty())
            if (currentState.isEmpty()) {
                if (lastFinalState.isEmpty()) {
                    throw ScannerError()
                } else {
                    val lexeme =
                            code.substring(lexemeStartIndex, lastFinalStateIndex + 1)
                    tokens.add(Token(getTokenType(lastFinalState, lexeme), lexeme))
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
            val lexeme = code.substring(lexemeStartIndex, lastFinalStateIndex + 1)
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
        val dfaName = baseDfas
                .find { baseDfa -> states.find { baseDfa.isFinalState(it) } != null }
                ?.name
                .orEmpty()
        return when {
            BASE_DFA_NAMES[dfaName] == TokenType.IDENTIFIER -> {
                when {
                    KEYWORDS.contains(currentLexeme) -> TokenType.KEYWORD
                    BOOLEAN_LITERALS.contains(currentLexeme) -> TokenType.BOOLEAN
                    currentLexeme == "null" -> TokenType.NULL
                    else -> TokenType.IDENTIFIER
                }
            }
            BASE_DFA_NAMES[dfaName] != null -> BASE_DFA_NAMES[dfaName]!!
            else -> throw ScannerError()
        }
    }
}

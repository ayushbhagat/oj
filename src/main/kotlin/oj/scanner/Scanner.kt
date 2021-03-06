package oj.scanner

import oj.models.NFA
import oj.models.Token
import oj.models.TokenType

const val SCANNER_DFA = "scanner"

val BACKSLASH_CHARACTERS = mutableMapOf(
        "\b" to "\\b",
        "\t" to "\\t",
        "\n" to "\\n",
        "\r" to "\\r",
        """"\f"""" to "\\f")

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
    /**
     * Return a tokenization of the input program.
     * @param code The code to tokenize.
     * @return The list of tokens that represents the input program.
     */
    fun tokenize(code: String): List<Token> {
        val tokens: MutableList<Token> = mutableListOf()
        var currentState: NFA.State? = dfa.startState
        var lexemeStartIndex = 0
        var lastFinalState: NFA.State? = null
        var lastFinalStateIndex = -1
        var index = 0
        while (index < code.length) {
            val inputCharacter =
                    if (BACKSLASH_CHARACTERS.contains(code[index].toString()))
                        BACKSLASH_CHARACTERS[code[index].toString()]
                    else
                        code[index].toString()
            currentState = dfa.getNextDFAState(currentState, inputCharacter.orEmpty())
            if (dfa.isFinalState(currentState)) {
                lastFinalState = currentState
                lastFinalStateIndex = index
            }
            // If DFA is in error state or the last input character was read.
            if (currentState == null || index >= code.length - 1) {
                if (lastFinalState == null) {
                    throw ScannerError("Haven't seen a final state when going to the error state")
                } else {
                    val lexeme =
                            code.substring(lexemeStartIndex, lastFinalStateIndex + 1)
                    val token = Token(getTokenType(lastFinalState, lexeme), lexeme)
                    tokens.add(token)
                    index = lastFinalStateIndex
                    currentState = dfa.startState
                    lexemeStartIndex = lastFinalStateIndex + 1
                    lastFinalState = null
                    lastFinalStateIndex = -1
                }
            }
            index++
        }
        return tokens
    }

    /**
     * Return the token type.
     * @param finalState The final state to use to determine the token type just read.
     * @param currentLexeme The lexeme that is also used to determine the token type.
     */
    private fun getTokenType(finalState: NFA.State?, currentLexeme: String): TokenType {
        if (finalState == null) {
            throw ScannerError("Final state is null")
        }
        // Note that this is kind of a hack because it assumes that state names are joined using
        // '_'.
        val stateNames = finalState
                .name
                .substring(finalState.name.indexOf(SCANNER_DFA) + SCANNER_DFA.length)
                .split("_")
        val dfaName = baseDfas
                .find { baseDfa ->
                    stateNames.find {
                        baseDfa.isFinalState(NFA.State(it, NFA.EmptyStateData()))
                    } != null
                }
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
            else -> throw ScannerError("Invalid token type")
        }
    }
}

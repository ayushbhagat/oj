package oj.models

val IGNORED_TOKEN_TYPES = setOf(TokenType.WHITESPACE, TokenType.COMMENT)

enum class TokenType {
    COMMENT,
    IDENTIFIER,
    KEYWORD,
    BOOLEAN,
    CHARACTER,
    STRING,
    INTEGER,
    NULL,
    OPERATOR,
    SEPARATOR,
    WHITESPACE
}

data class Token(val type: TokenType, val lexeme: String)

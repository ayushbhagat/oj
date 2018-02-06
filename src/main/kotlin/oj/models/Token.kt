package oj.models

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

package oj.models

data class CSTNode(
    val name: String,
    val lexeme: String,
    val children: MutableList<CSTNode> = mutableListOf()
)
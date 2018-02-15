package oj.Parser

import oj.models.NFA
import oj.models.NFA.StateData
import oj.models.NFA.StateDataHelper
import oj.models.Token
import oj.models.TokenType

data class CSTNode(
    val name: String,
    val children: MutableList<CSTNode> = mutableListOf()
)

data class Rule(val lhs: String, val rhs: List<String>) {
    override fun toString() : String {
        if (rhs.isEmpty()) {
            return lhs
        }

        return "$lhs ${rhs.joinToString(" ")}"
    }

    companion object {
        fun deserialize(line: String): Rule {
            val sides = line.split(" ")
            val lhs = sides.first()
            val rhs = sides.drop(1)
            return Rule(lhs, rhs)
        }
    }
}

data class CFGStateData(val items: MutableMap<String, Rule> = mutableMapOf()) : StateData

class CFGStateDataHelperException(reason: String): Exception(reason)

class CFGStateDataHelper : StateDataHelper {
    override fun deserialize(line: String) : StateData {
        return if (line.isEmpty()) {
             CFGStateData()
        } else {
            val serializedItems = line.split("  ")

            val items = serializedItems.fold(mutableMapOf<String, Rule>(), { acc, serializedItem ->
                val indexOfFirstSpace = serializedItem.indexOf(" ")
                val lookahead = serializedItem.substring(0, indexOfFirstSpace)
                val serializedRule = serializedItem.substring(indexOfFirstSpace + 1)

                val rule = Rule.deserialize(serializedRule)
                acc[lookahead] = rule
                acc
            })

            CFGStateData(items)
        }
    }

    override fun serialize(stateData: StateData): String {
        if (stateData !is CFGStateData) {
            throw CFGStateDataHelperException("stateData !is CFGStateData")
        }

        return if (stateData.items.isEmpty()) {
            ""
        } else {
            stateData.items
                .map({ entry ->
                    val lookahead = entry.key
                    val rule = entry.value
                    "$lookahead $rule"
                })
                .joinToString("  ")
        }
    }


    override fun combine(vararg stateDataList: StateData): StateData {
        // unused
        throw CFGStateDataHelperException("Tried to execute combine()")
    }
}

class ParserError(reason: String): Exception(reason)

class Parser(
    private val dfa: NFA
) {
    fun parse(tokens: List<Token>) : CSTNode {
        val input: MutableList<String> = mutableListOf("BOF")
        input.addAll(tokens.map({ convertTokenToCFGTerminal(it) }).filter({ it.isNotEmpty() }))
        input.add("EOF")

        val stateStack = mutableListOf(dfa.startState)
        val cstNodeStack : MutableList<CSTNode> = mutableListOf()

        var inputIndex = 0
        while (inputIndex < input.size) {
            val symbol = input[inputIndex]
            val currentState = stateStack.last()
            if (currentState.data !is CFGStateData) {
                throw ParserError("Invalid state data type")
            }

            val rule = currentState.data.items[symbol]

            if (rule != null) {
                val children = rule.rhs.map({
                    val node = cstNodeStack[cstNodeStack.size - 1]
                    cstNodeStack.remove(node)
                    stateStack.remove(stateStack[stateStack.size - 1])
                    node
                }).reversed().toMutableList()

                val node = CSTNode(rule.lhs, children)
                cstNodeStack.add(node)

                val nextState = dfa.getNextDFAState(stateStack.last(), rule.lhs)
                nextState ?: throw ParserError("Invalid symbol detected: $symbol")

                stateStack.add(nextState)
            } else {
                val nextState = dfa.getNextDFAState(currentState, symbol)
                if (nextState == null) {
                    throw ParserError("Invalid symbol detected at ${currentState.name}: $symbol")
                }

                stateStack.add(nextState)
                val node = CSTNode(symbol)
                cstNodeStack.add(node)

                inputIndex += 1
            }
        }

        val cstNodeTop = cstNodeStack.first()

        if (cstNodeTop.name != "S") {
            throw ParserError("Failed to generate a parse tree")
        }

        return cstNodeTop
    }

    fun convertTokenToCFGTerminal(token: Token) : String {
        val tokenTerminals = listOf(
            TokenType.IDENTIFIER,
            TokenType.INTEGER,
            TokenType.BOOLEAN,
            TokenType.CHARACTER,
            TokenType.STRING,
            TokenType.NULL
        )
        val ignoredTokens = listOf(TokenType.WHITESPACE, TokenType.COMMENT)

        return when {
            token.type in tokenTerminals -> token.type.toString()
            token.type in ignoredTokens -> ""
            else -> token.lexeme
        }
    }
}
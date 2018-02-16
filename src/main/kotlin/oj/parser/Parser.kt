package oj.parser

import oj.models.*
import oj.models.NFA.StateData
import oj.models.NFA.StateDataHelper

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

class Parser(
    private val dfa: NFA
) {
    fun parse(tokens: List<Token>) : CSTNode {
        val tokenTerminals = setOf(
            TokenType.IDENTIFIER,
            TokenType.INTEGER,
            TokenType.BOOLEAN,
            TokenType.CHARACTER,
            TokenType.STRING,
            TokenType.NULL
        )

        val relevantTokens = tokens.filter({ token -> !IGNORED_TOKEN_TYPES.contains(token.type)})

        val input: MutableList<String> = mutableListOf("BOF")
        input.addAll(relevantTokens.map({token ->
            when {
                token.type in tokenTerminals -> token.type.toString()
                else -> token.lexeme
            }
        }))
        input.add("EOF")

        val stateStack = mutableListOf(dfa.startState)
        val cstNodeStack : MutableList<CSTNode> = mutableListOf()

        var inputIndex = 0
        while (inputIndex < input.size) {
            val symbol = input[inputIndex]
            val lexeme : String = when {
                symbol in tokenTerminals.map({ it.toString() }) -> relevantTokens[inputIndex - 1].lexeme
                else -> ""
            }

            val currentState = stateStack.last()
            if (currentState.data !is CFGStateData) {
                throw ParseError("Invalid state data type")
            }

            val rule = currentState.data.items[symbol]

            if (rule != null) {
                val children = rule.rhs.map({
                    val node = cstNodeStack.last()
                    cstNodeStack.removeAt(cstNodeStack.size - 1)
                    stateStack.removeAt(stateStack.size - 1)
                    node
                }).reversed().toMutableList()

                val node = CSTNode(rule.lhs, lexeme, children)
                cstNodeStack.add(node)

                val nextState = dfa.getNextDFAState(stateStack.last(), rule.lhs)
                if (nextState == null) {
                    throw ParseError("Invalid symbol detected: $symbol")
                }

                stateStack.add(nextState)
            } else {
                val nextState = dfa.getNextDFAState(currentState, symbol)
                if (nextState == null) {
                    throw ParseError("Invalid symbol detected at ${currentState.name}: $symbol")
                }

                stateStack.add(nextState)
                val node = CSTNode(symbol, lexeme)
                cstNodeStack.add(node)

                inputIndex += 1
            }
        }

        if (cstNodeStack.size != 3) {
            throw ParseError("Failed to generate parse tree")
        }

        val parseTree = cstNodeStack[1]

        if (parseTree.name != "CompilationUnit") {
            throw ParseError("Failed to generate a parse tree")
        }

        return parseTree
    }
}
package oj.models

import java.io.File

class CFGDeserializationError: Exception("CFG Deserialization Error")
class CFGError(reason :String): Exception(reason)

data class Item(
    val lhs: String,
    val rhs: List<String>,
    val bookmark: Int,
    val lookahead: String
)

data class LR1DFAStateData( val items: Set<Item> ) : NFA.StateData

class LR1DFAStateDataHelper : NFA.StateDataHelper {
    override fun deserialize(line: String): NFA.StateData {
        val items : MutableSet<Item> = mutableSetOf()

        val symbols = line.split(" ")
        var i = 0

        while (i < symbols.size) {
            val bookmark = symbols[i].toInt()
            val lookahead = symbols[i + 1]
            val lhs = symbols[i + 2]
            val rhsSize = symbols[i + 3].toInt()

            val rhs: MutableList<String> = mutableListOf()

            for (j in (i + 4)..(i + 4 + rhsSize)) {
                rhs.add(symbols[j])
            }

            items.add(Item(lhs, rhs, bookmark, lookahead))
            i += 5 + rhsSize
        }

        return LR1DFAStateData(items)
    }

    override fun serialize(stateData: NFA.StateData): String {
        if (stateData !is LR1DFAStateData) {
            throw CFGError("Failed to serialize stateData")
        }

        val serializedItems = stateData.items.map({ item ->
            return "${item.bookmark} ${item.lookahead} ${item.lhs} ${item.rhs.size}" + item.rhs.joinToString(" ")
        })

        return serializedItems.joinToString(" ")
    }

    override fun combine(vararg stateDataList: NFA.StateData): NFA.StateData {
        val items: MutableSet<Item> = mutableSetOf()

        for (stateData in stateDataList) {
            if (stateData !is LR1DFAStateData) {
                throw CFGError("Tried to merge non LR1DFAStateData")
            }

            items += stateData.items
        }

        return LR1DFAStateData(items)
    }
}


data class CFG(
    val start: String,
    val terminals: Set<String>,
    val nonTerminals: Set<String>,
    val rules: Map<String, Set<List<String>>>
) {

    companion object {
        fun deserialize(filePath: String): CFG {
            val lines = File(filePath).readLines()
            val start = lines[0]
            val terminals = lines[1].split(" ").filter({ it.trim().isNotEmpty() }).toSet()
            val nonTerminals: MutableSet<String> = mutableSetOf(start)
            val rules: MutableMap<String, MutableSet<List<String>>> = mutableMapOf()

            lines
                .drop(2)
                .forEach({ line ->
                    val tokens = line.split(" ").filter({ it.trim().isNotEmpty() })
                    val nonTerminal = tokens[0]
                    val expansion = tokens.drop(2)

                    // Add non-terminal
                    nonTerminals.add(nonTerminal)

                    val expansions = rules.getOrDefault(nonTerminal, mutableSetOf())
                    expansions.add(expansion)

                    rules[nonTerminal] = expansions
                })

            // Simple CFG verification
            for ((_, expansions) in rules) {
                for (expansion in expansions) {
                    for (terminalOrNonTerminal in expansion) {
                        if (!terminals.contains(terminalOrNonTerminal) && !nonTerminals.contains(terminalOrNonTerminal)) {
                            throw CFGDeserializationError()
                        }
                    }
                }
            }

            return CFG(start, terminals, nonTerminals, rules)
        }
    }

    fun toLR1DFA(): NFA {
        val alphabet = terminals + nonTerminals

        val itemsStateDataMap: MutableMap<Set<Item>, NFA.State> = mutableMapOf()
        var stateNum = 0

        val getState = { items: Set<Item> ->
            if (!itemsStateDataMap.contains(items)) {
                val stateData = LR1DFAStateData(items)
                val state = NFA.State("${stateNum++}", stateData)
                itemsStateDataMap[items] = state
            }

            itemsStateDataMap[items]!!
        }

        val startSymbol = start
        val startItems = getExpansions(startSymbol).map({ rhs -> Item(startSymbol, rhs, 0, "$") }).toSet()
        val startState = getState(startItems)

        val states = mutableListOf(startState)
        val workList = mutableListOf(startState)

        val transitionFn : MutableMap<NFA.State, MutableMap<String, Set<NFA.State>>> = mutableMapOf()

        while (!workList.isEmpty()) {
            val currentState = workList[workList.size - 1]
            workList.remove(currentState)

            if (currentState.data !is LR1DFAStateData) {
                throw CFGError("currentState.data is not an instance of LR1DFAStateData")
            }

            if (!transitionFn.contains(currentState)) {
                transitionFn[currentState] = mutableMapOf()
            }

            val currentItems = currentState.data.items
            for (item in currentItems) {
                if (item.bookmark >= item.rhs.size) {
                    continue
                }

                // Epsilon transitions
                if (item.rhs[item.bookmark] in nonTerminals) {
                    val C = item.rhs[item.bookmark]
                    val beta = item.rhs.drop(item.bookmark + 1)

                    val lookaheads = first(beta + item.lookahead)

                    val nextItems = getExpansions(C).flatMap({ rhs ->
                        lookaheads.map({ lookahead -> Item(C, rhs, 0, lookahead) })
                    }).toSet()

                    nextItems.forEach({ nextItem ->
                        val nextState = getState(setOf(nextItem))

                        if (nextState !in states) {
                            states.add(nextState)
                            workList.add(nextState)
                        }

                        transitionFn[currentState]!![""] = setOf(nextState)
                    })
                }

                val X = item.rhs[item.bookmark]
                val nextItem = Item(item.lhs, item.rhs, item.bookmark + 1, item.lookahead)
                val nextState = getState(setOf(nextItem))

                if (nextState !in states) {
                    states.add(nextState)
                    workList.add(nextState)
                }

                transitionFn[currentState]!![X] = setOf(nextState)
            }
        }

        val nfa = NFA(
            states.toSet(),
            startState,
            states.toSet(),
            transitionFn,
            LR1DFAStateDataHelper(),
            alphabet
        )

        return nfa.toDFA()
    }

    fun first(alpha: List<String>) : Set<String> {
        if (alpha.isEmpty()) {
            return setOf()
        }

        if (alpha[0] in terminals) {
            return setOf(alpha[0])
        }

        val A = alpha[0]

        if (alpha.size == 1) {
            return getExpansions(A)
                .flatMap({ rhs ->  first(rhs) })
                .toSet()
        }

        if (nullable(listOf(A))) {
            return first(listOf(A)) + first(alpha.drop(1))
        }

        return first(listOf(A))
    }

    private fun nullable(alpha: List<String>) : Boolean {
        if (alpha.isEmpty()) {
            return true
        }

        if (alpha.size == 1) {
            val symbol = alpha[0]

            if (symbol in terminals) {
                return false
            }

            return getExpansions(symbol).all({ rhs -> nullable(rhs) })
        }

        return alpha.all({ symbol -> nullable(listOf(symbol)) })
    }

    private fun getExpansions(nonTerminal: String) : Set<List<String>> {
        if (! rules.contains(nonTerminal) || !nonTerminals.contains(nonTerminal)) {
            throw CFGError("Unrecognized non-terminal: $nonTerminal")
        }

        return rules[nonTerminal]!!
    }
}

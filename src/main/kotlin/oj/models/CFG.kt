package oj.models

import java.io.File

class CFGError(reason: String): Exception(reason)

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

                    if (tokens.size < 2) {
                        throw CFGError("Invalid rule detected: " + tokens)
                    }

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
                            throw CFGError("Symbol $terminalOrNonTerminal not found in terminal or non-terminals.")
                        }
                    }
                }
            }

            val symbolsUsed : MutableSet<String> = mutableSetOf()

            for ((_, expansions) in rules) {
                for (expansion in expansions) {
                    for (symbol in expansion) {
                        symbolsUsed.add(symbol)
                    }
                }
            }

            for (terminal in terminals) {
                if (terminal !in symbolsUsed) {
                    throw CFGError("Terminal $terminal was not used in any rules.")
                }
            }

            for (nonTerminal in nonTerminals - "S") {
                if (nonTerminal !in symbolsUsed) {
                    throw CFGError("Non-terminal $nonTerminal was not used in any rules.")
                }
            }

            return CFG(start, terminals, nonTerminals, rules)
        }
    }
}

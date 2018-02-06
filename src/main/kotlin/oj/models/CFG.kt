package oj.models

import java.io.File

class CFGDeserializationError: Exception("CFG Deserialization Error")

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
                            println("expansion: \"$expansion\"")
                            println("terminalOrNonTerminal: \"$terminalOrNonTerminal\"")
                            throw CFGDeserializationError()
                        }
                    }
                }
            }

            return CFG(start, terminals, nonTerminals, rules)
        }
    }
}

package oj.scripts

import oj.parser.CFGStateData
import oj.parser.CFGStateDataHelper
import oj.parser.Rule
import java.io.File

fun main(args: Array<String>) {
    val filePath = "gen/joos-jlalr-lr1-output.dfa"
    val outPath = "gen/joos-lr1.dfa"
    val lines = File(filePath).readLines()

    val numTerminals = lines[0].toInt()
    val numNonTerminals = lines[numTerminals + 1].toInt()

    val numProductionRulesStartIndex = numNonTerminals + numTerminals + 3

    val numProductionRules = lines[numProductionRulesStartIndex].toInt()
    val dfaStartIndex = numTerminals + numNonTerminals + numProductionRules + 4

    val productionRules = lines.subList(numProductionRulesStartIndex + 1, dfaStartIndex)
    val dfaLines = lines.drop(dfaStartIndex)
    val numStates = dfaLines[0].toInt()


    var str = ""
    val write = { line: Any ->
        str += "$line\n"
    }

    write(numStates)

    val dfaTransitions = dfaLines.drop(2)

    val reduceDfaLines = dfaTransitions.filter({
        it.split(" ")[2] == "reduce"
    })

    val stateDataHelper = CFGStateDataHelper()

    val reduceLines = reduceDfaLines
        .fold(mutableMapOf<String, MutableMap<String, Rule>>(), { acc, line ->
            val tokens = line.split(" ")
            val state = tokens[0]
            val lookahead = tokens[1]
            val ruleNum = tokens[3].toInt()

            val rule = Rule.deserialize(productionRules[ruleNum])

            val items = acc[state] ?: mutableMapOf()

            items[lookahead] = rule
            acc[state] = items

            acc
        })
        .map({ entry ->
            val state = entry.key
            val items = entry.value
            "$state ${stateDataHelper.serialize(CFGStateData(items))}"
        })

    reduceLines.forEach({ write(it) })


    val reduceStates = reduceDfaLines.map({ it.split(" ")[0] }).toSet()
    val allStates = IntRange(0, Math.max(numStates - 1, 0)).map({ it.toString() }).toSet()

    (allStates - reduceStates).forEach({ write(it) })

    write(0)
    write(allStates.joinToString(" "))

    dfaTransitions
        .filter({ it.split(" ")[2] == "shift"})
        .map({ line ->
            val tokens = line.split(" ")
            val fromState = tokens[0]
            val toState = tokens[3]
            val transitionToken = tokens[1]
            "$fromState $toState $transitionToken"
        })
        .forEach({ write(it) })

    File(outPath).bufferedWriter().use { it.write(str) }
}
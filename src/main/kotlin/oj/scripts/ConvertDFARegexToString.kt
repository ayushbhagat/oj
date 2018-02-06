package oj.scripts

import oj.scanner.BASE_DFA_NAMES
import java.io.File

fun main(args: Array<String>) {
    BASE_DFA_NAMES.keys.forEach { convertRegexToCharCode("dfa/${it}_regex.dfa") }
}

fun convertRegexToCharCode(filePath: String) {
    var lineList: MutableList<String> = mutableListOf()
    val inputStream = File(filePath).inputStream()
    inputStream.bufferedReader().useLines { lines -> lines.forEach { lineList.add(it) }}

    var newContent = "${lineList[0]}\n${lineList[1]}\n"
    lineList.drop(2).forEach {
        val firstSpaceIndex = it.indexOf(" ")
        val fromState = it.substring(0, firstSpaceIndex)
        val secondSpaceIndex = firstSpaceIndex + it.substring(firstSpaceIndex + 1)
                .indexOf(" ") + 1
        val toState = it.substring(firstSpaceIndex + 1, secondSpaceIndex)
        val transitionRegex = it.substring(secondSpaceIndex + 1)
        oj.scanner.ALPHABET
                .filterNot { oj.scanner.BACKSLASH_CHARACTERS.values.contains(it) }
                .union(oj.scanner.BACKSLASH_CHARACTERS.keys)
                .forEach {
                    if (transitionRegex.toRegex().matches(it)) {
                        newContent +="$fromState $toState ${
                            if (oj.scanner.BACKSLASH_CHARACTERS.contains(it)) oj.scanner.BACKSLASH_CHARACTERS[it]
                            else it
                        }\n"
                    }
                }
    }

    val regexString = "_regex"
    val regexIndex = filePath.indexOf(regexString)
    val dfaString = "dfa"
    val dfaIndex = filePath.indexOf(dfaString)
    val writeFilePath =
            "gen" +
            filePath.substring(dfaIndex + dfaString.length, regexIndex) +
            filePath.substring(regexIndex + regexString.length)
    File(writeFilePath).bufferedWriter().use { it.write(newContent) }
}

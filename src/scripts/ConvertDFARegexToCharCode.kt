package scripts

import models.NFAType
import java.io.File

fun main(args: Array<String>) {
    NFAType.values().forEach { convertRegexToCharCode("dfa/${it.fileName}_regex.dfa") }
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
        scanner.ALPHABET.forEach {
            if (transitionRegex.toRegex().matches(it.toString())) {
                newContent += "$fromState $toState ${it.toInt()}\n"
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
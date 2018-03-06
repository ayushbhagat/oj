package oj.scripts

import java.io.File

fun main(args: Array<String>) {
    val testSuite = "A3"
    val stdlib = when (testSuite) {
        "A2" -> "2.0"
        "A3" -> "3.0"
        "A4" -> "4.0"
        "A5" -> "5.0"
        else -> {
            throw Exception("Found no stdlib")
        }
    }


    val testBaseDir = "./test/marmoset"
    val testLocation = "$testBaseDir/$testSuite"
    val tests = File(testLocation).list()

    val testResults = mutableListOf<String>()
    val stdlibFiles = File("$testBaseDir/stdlib/$stdlib").walkTopDown().filter({ it.extension == "java" }).joinToString(" ")

    for (test in tests) {
        val shouldFail = test.startsWith("Je")
        val testFiles = File("$testLocation/$test").walkTopDown().joinToString(" ")

        val process = Runtime.getRuntime().exec("./joosc $stdlibFiles $testFiles")
        println("Running test: $test")

        process.waitFor()

        if (shouldFail) {
            if (process.exitValue() == 42) {
                testResults.add("PASS: $test")
            } else {
                testResults.add("FAILED: $test")
            }
        } else {
            if (process.exitValue() == 0) {
                testResults.add("PASS: $test")
            } else {
                testResults.add("FAILED: $test")
            }
        }
    }

    for (j in IntRange(0, 4)) {
        for (i in IntRange(0, 100)) {
            print("*")
        }

        println("")
    }

    testResults
        .sortBy({ it -> it.replace("PASS:", "").replace("FAILED:", "")})

    testResults
        .forEach({ testResult ->
            println(testResult)
        })
}
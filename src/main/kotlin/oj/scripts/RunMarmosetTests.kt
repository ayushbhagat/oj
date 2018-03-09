package oj.scripts

import java.io.File
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


const val MARMOSET_DIR = "./test/marmoset"

fun main(args: Array<String>) {
    val assignment = "a3"
    val stdlibVersion = when (assignment) {
        "a2" -> "2.0"
        "a3" -> "3.0"
        "a4" -> "4.0"
        "a5" -> "5.0"
        else -> {
            throw Exception("Found no stdlib")
        }
    }

    println("Building joosc...")

    if (exec("make") != 0) {
        System.err.println("Build failed")
        System.exit(1)
    }

    println()

    val TEST_DIR = "$MARMOSET_DIR/$assignment"
    val STDLIB_DIR = "$MARMOSET_DIR/stdlib/$stdlibVersion"

    val stdlibFiles = getAllJavaFiles(STDLIB_DIR)
    val tests = File(TEST_DIR).list().sorted()

    var testsPassed = 0

    for ((testFile, testNum) in tests.zip(IntRange(0, tests.size))) {
        val testFiles = getAllJavaFiles("$TEST_DIR/$testFile")
        val (exitValue, error, output) = execHideIO("./joosc", *stdlibFiles, *testFiles)

        val testResultPrefix = "[${format(testNum)}/${format(tests.size - 1)}]"
        val shouldTestFail = testFile.startsWith("Je")

        if (shouldTestFail && exitValue == 42 || !shouldTestFail && exitValue == 0) {
            testsPassed += 1
            System.out.println("$testResultPrefix PASS: $testFile")
        } else {
            System.err.println("$testResultPrefix FAILED: $testFile")
            if (shouldTestFail) {
                System.err.println(output)
            } else {
                System.err.println(error)
            }
        }
    }

    println()
    println("Tests passed: ${format(testsPassed)}/${format(tests.size)}")
}

fun getAllJavaFiles(pathname: String): Array<String> {
    return File(pathname).walkTopDown()
        .filter({ it.extension == "java" })
        .map({ it.toString() })
        .toList()
        .toTypedArray()
}

fun format(n: Int, padding: Int = 3): String {
    return "$n".padStart(padding, ' ')
}

fun exec(command: String, vararg arg: String): Int {
    val process = ProcessBuilder().inheritIO().command(command, *arg).start()
    return process.waitFor()
}

fun execHideIO(command: String, vararg arg: String): Triple<Int, String, String> {
    val process = ProcessBuilder().command(command, *arg).start()
    val error = streamToString(process.errorStream)
    val output = streamToString(process.inputStream)
    val exitCode = process.waitFor()
    return Triple(exitCode, error, output)
}

fun streamToString(stream: InputStream) : String {
    val reader = BufferedReader(InputStreamReader(stream))
    val builder = StringBuilder()
    var line: String?
    while (true) {
        line = reader.readLine()
        if (line == null) {
            break
        }

        builder.append(line)
        builder.append(System.getProperty("line.separator"))
    }
    val result = builder.toString()

    return result
}
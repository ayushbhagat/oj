package oj.scripts

import java.io.File
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

const val MARMOSET_DIR = "./test/marmoset"

fun main(args: Array<String>) {
    val assignment = "a4"
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

    val testsPassed = MutableList(tests.size, { false })
    val testsDone = List(tests.size, { EmptySemaphore(1) })
    val testErrors = MutableList(tests.size, { "" })

    val numCores = Runtime.getRuntime().availableProcessors()

    val threads = IntRange(0, numCores - 1).map({ threadNum ->
        thread(start = true) {
            var testNum = threadNum
            while (testNum < tests.size) {
                val test = tests[testNum]

                val testFiles = getAllJavaFiles("$TEST_DIR/$test")
                val (exitValue, error, output) = execHideIO("./joosc", *testFiles, *stdlibFiles)

                val shouldTestFail = test.startsWith("Je")

                if (shouldTestFail && exitValue == 42 || !shouldTestFail && exitValue == 0) {
                    testsPassed[testNum] = true
                } else {
                    testsPassed[testNum] = false
                    if (shouldTestFail) {
                        testErrors[testNum] = output
                    } else {
                        testErrors[testNum] = error
                    }
                }

                testsDone[testNum].release()
                testNum += numCores
            }
        }
    })

    val formatLength = "${tests.size - 1}".length

    tests.zip(IntRange(0, tests.size - 1)).forEach({(testFile, testNum) ->
        testsDone[testNum].acquire()

        val testResultPrefix = "[${format(testNum, formatLength)}/${format(tests.size - 1, formatLength)}]"
        if (testsPassed[testNum]) {
            System.out.println("$testResultPrefix PASS: $testFile")
        } else {
            System.err.println("$testResultPrefix FAILED: $testFile")
            System.err.println(testErrors[testNum])
        }
    })

    threads.forEach({ thread ->
        thread.join()
    })

    val numTestsPassed = testsPassed.filter({ it }).size

    println()
    println("Tests passed: ${format(numTestsPassed, formatLength)}/${format(tests.size, formatLength)}")
}

fun EmptySemaphore(n: Int): Semaphore {
    val sem = Semaphore(n)
    sem.acquire(n)
    return sem
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
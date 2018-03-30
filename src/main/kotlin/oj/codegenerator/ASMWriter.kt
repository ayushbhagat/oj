package oj.codegenerator

class ASMWriter {
    private var code = ""
    private var indent = 0

    private val bufferedComments = mutableListOf<String>()

    fun getCode(): String {
        return code
    }

    fun writeComment(comment: String = "") {
        writeLn(";; $comment".trim())
    }

    fun writeLn(line: String = "") {
        write(if (line.isNotEmpty()) "$line\n" else "\n")
    }

    fun write(line: String = "") {
        val indentString =
            if (line == "\n" || line.isEmpty()) "" else {
                IntRange(0, indent - 1).map({ '\t' }).joinToString("")
            }

        code += "$indentString$line"
    }

    fun withIndent(fn: () -> Unit) {
        indent += 1
        try {
            fn()
        } finally {
            indent -= 1
        }
    }
}
package oj.codegenerator

import oj.models.CSTNode
import oj.models.CSTNodeVisitor
import oj.models.PackageManager
import oj.models.getDeclarationName

class CodeGenerationVisitor(
    val packageManager: PackageManager
) : CSTNodeVisitor() {
    private var code = ""
    private var indent = 0

    fun getCode(): String {
        return code
    }

    fun writeComment(comment: String) {
        writeLn(";; $comment")
    }

    fun writeLn(line: String) {
        write(if (line.isNotEmpty()) "$line\n" else "\n")
    }

    fun write(line: String) {
        val indentString =
            if (line == "\n" || line.isEmpty()) "" else {
                IntRange(0, indent - 1).map({ '\t' }).joinToString(".")
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

    override fun visitClassDeclaration(node: CSTNode) {
        super.visitClassDeclaration(node)

        writeComment("Inside class ${getDeclarationName(node)}")
    }

    override fun visitInterfaceDeclaration(node: CSTNode) {
        super.visitClassDeclaration(node)

        writeComment("Inside interface ${getDeclarationName(node)}")
    }
}
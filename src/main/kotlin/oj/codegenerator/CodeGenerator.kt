package oj.codegenerator

import oj.models.*
import java.io.File

class CodeGenerator {
    companion object {
        fun generateCode(packageManager: PackageManager) {
            packageManager.packages.forEach({(_, pkg) ->
                pkg.forEach(fun (compilationUnit: CSTNode) {
                    val visitor = CodeGenerationVisitor(packageManager)
                    visitor.visit(compilationUnit)
                    val code = visitor.getCode()
                    val typeDeclaration = compilationUnit.getDescendant(isClassOrInterfaceDeclaration)

                    if (typeDeclaration.name != "ClassDeclaration") {
                        return
                    }

                    val qualifiedName = packageManager.getQualifiedDeclarationName(typeDeclaration)

                    File("./output").mkdir()
                    File("./output/$qualifiedName.s").printWriter().use({ it.print(code) })
                })
            })

            val asmWriter = ASMWriter()

            asmWriter.writeLn("section .text")
            /**
             * TODO: Call static initializers of all classes
             */
            asmWriter.writeLn()
            asmWriter.writeLn("section .data")

            primitiveTypes
                .map({ Type(CSTNode(it), true) })
                .forEach({ primitiveArrayType ->
                    val superTypeTableLabel = packageManager.getSuperTypeTableLabel(primitiveArrayType)
                    asmWriter.writeLn("$superTypeTableLabel:")
                    val superTypeTable = packageManager.getSuperTypeTable(primitiveArrayType)

                    asmWriter.withIndent({
                        superTypeTable.forEach({(_, isSuperType) ->
                            asmWriter.writeLn("dd ${if (isSuperType) 1 else 0}")
                        })
                    })
                })
        }
    }
}
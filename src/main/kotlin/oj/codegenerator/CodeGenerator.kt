package oj.codegenerator

import oj.models.CSTNode
import oj.models.PackageManager
import oj.nameresolver.isClassOrInterfaceDeclaration
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

                    File("./gen/asm").mkdir()
                    File("./gen/asm/$qualifiedName.s").printWriter().use({ it.print(code) })

                })
            })
        }
    }
}
package oj.codegenerator

import oj.models.*
import java.io.File

class CodeGenerator {
    companion object {
        fun generateCode(packageManager: PackageManager) {
            File("./output").deleteRecursively()
            File("./output").mkdir()

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

                    File("./output/$qualifiedName.s").printWriter().use({ it.print(code) })
                })
            })

            val asmWriter = ASMWriter()

            asmWriter.writeLn("section .text")

            asmWriter.writeLn("global _start:")
            asmWriter.writeLn("_start:")
            asmWriter.withIndent({
                val classes = packageManager.getAllClasses()

                classes
                    .forEach({ classDeclaration ->
                        val staticFieldInitializerLabel = packageManager.getStaticFieldInitializerLabel(classDeclaration)
                        val qualifiedClassName = packageManager.getQualifiedDeclarationName(classDeclaration)

                        asmWriter.writeComment("Initialize static fields of class $qualifiedClassName")
                        asmWriter.writeLn("extern $staticFieldInitializerLabel")
                        asmWriter.writeLn("call $staticFieldInitializerLabel")
                        asmWriter.writeLn()
                    })

                val entryPoints = classes.flatMap({ classDeclaration ->
                    classDeclaration.getDescendants(fun (descendant: CSTNode): Boolean {
                        if (descendant.name != "MethodDeclaration" || "static" !in getModifiers(descendant)) {
                            return false
                        }

                        if (getDeclarationName(descendant) != "test") {
                            return false
                        }

                        val methodHeader = descendant.getChild("MethodHeader")

                        val formalParametersListOpt = methodHeader.getDescendant("FormalParameterListOpt")

                        if (formalParametersListOpt.children.size != 0) {
                            return false
                        }

                        if (methodHeader.children[1].name == "void") {
                            return false
                        }

                        val methodReturnType = methodHeader.getChild("Type")

                        return methodReturnType.getDescendants("int").size == 1
                    })
                })

                check(entryPoints.size == 1) {
                    "There should be exactly one entry-point into the program. Found ${entryPoints.size}."
                }

                val entryPoint = entryPoints.first()
                val entryPointLabel = packageManager.getClassMemberLabel(entryPoint)

                asmWriter.writeComment("Start program")
                asmWriter.writeLn("extern $entryPointLabel")
                asmWriter.writeLn("call $entryPointLabel")
                asmWriter.writeLn()

                asmWriter.writeComment("Exit program")
                asmWriter.writeLn("mov ebx, eax")
                asmWriter.writeLn("mov eax, 1")
                asmWriter.writeLn("int 0x80")
                asmWriter.writeLn()
            })

            asmWriter.writeLn("section .data")

            primitiveTypes
                .map({ primitiveType -> Type(CSTNode(primitiveType), true) })
                .forEach({ type ->
                    val superTypeTableLabel = packageManager.getSuperTypeTableLabel(type)
                    asmWriter.writeLn("global $superTypeTableLabel")
                    asmWriter.writeLn("$superTypeTableLabel:")
                    val superTypeTable = packageManager.getSuperTypeTable(type)

                    asmWriter.withIndent({
                        superTypeTable.forEach({(type, isSuperType) ->
                            asmWriter.writeLn("dd ${if (isSuperType) 1 else 0} ;; $type")
                        })
                        asmWriter.writeLn()
                    })
                })

            File("./output/_main.s").printWriter().use({ it.print(asmWriter.getCode()) })
        }
    }
}
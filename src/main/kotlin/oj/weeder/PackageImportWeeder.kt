package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

class PackageImportWeeder : CSTNodeVisitor() {
    class SingleNameImportImportsSameTypeAsIsDeclared(typeName: String) : WeedError("Tried to import type \"$typeName\" in a file where a type with the same name is declared.")
    class DuplicateSingleNameImport(typeName: String) : WeedError("Two single-name-import declarations import the same type \"$typeName\"")
    class PackageNameIsTypeName(typeName: String): WeedError("Package has same name \"$typeName\" as type declared")

    val singleNameImportedTypes = mutableSetOf<List<String>>()
    var packageName = ""

    override fun visitPackageDeclaration(node: CSTNode) {
        val name = node.getChild("Name")
        packageName = name.getDescendants("IDENTIFIER").map({ it.lexeme }).joinToString(".")
    }

    override fun visitSingleTypeImportDeclaration(node: CSTNode) {
        val name = node.getChild("Name")
        val identifiers = name.getDescendants("IDENTIFIER").map({ it.lexeme })

        val typeName = identifiers.last()

        singleNameImportedTypes.forEach(fun (importedType: List<String>) {
            if (importedType.joinToString(".") == identifiers.joinToString(".")) {
                return
            }

            if (typeName == importedType.last()) {
                throw DuplicateSingleNameImport(typeName)
            }
        })

        singleNameImportedTypes.add(identifiers)
    }

    fun qualifyTypeName(typeName: String) : String {
        if (packageName.isEmpty()) {
            return typeName
        }

        return "$packageName.$typeName"
    }

    override fun visitClassDeclaration(node: CSTNode) {
        validateTypeDeclaration(node)
    }

    override fun visitInterfaceDeclaration(node: CSTNode) {
        validateTypeDeclaration(node)
    }

    private fun validateTypeDeclaration(node: CSTNode) {
        val typeName = node.getChild("IDENTIFIER").lexeme

        if (typeName == packageName) {
            throw PackageNameIsTypeName(typeName)
        }

        singleNameImportedTypes.forEach(fun (importedType: List<String>) {
            if (importedType.joinToString(".") == qualifyTypeName(typeName)) {
                return
            }

            if (importedType.last() == typeName) {
                throw SingleNameImportImportsSameTypeAsIsDeclared(typeName)
            }
        })
    }
}
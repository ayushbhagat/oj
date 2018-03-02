package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

class ImportWeeder: CSTNodeVisitor() {
    class SingleNameImportImportsSameTypeAsIsDeclared(typeName: String) : WeedError("Tried to import type \"$typeName\" in a file where a type with the same name is declared.")
    class DuplicateSingleNameImport(typeName: String) : WeedError("Two single-name-import declarations import the same type \"$typeName\"")

    val typeNamesSeen = mutableSetOf<String>()

    override fun visitSingleTypeImportDeclaration(node: CSTNode) {
        val name = node.getChild("Name")
        val lastIdentifier = name.getDescendants("IDENTIFIER").last()

        val typeName = lastIdentifier.lexeme

        if (typeName in typeNamesSeen) {
            throw DuplicateSingleNameImport(typeName)
        }

        typeNamesSeen.add(typeName)
    }

    override fun visitClassDeclaration(node: CSTNode) {
        val className = node.getChild("IDENTIFIER").lexeme
        if (className in typeNamesSeen) {
            throw SingleNameImportImportsSameTypeAsIsDeclared(className)
        }
    }

    override fun visitInterfaceDeclaration(node: CSTNode) {
        val interfaceName = node.getChild("IDENTIFIER").lexeme
        if (interfaceName in typeNamesSeen) {
            throw SingleNameImportImportsSameTypeAsIsDeclared(interfaceName)
        }
    }
}
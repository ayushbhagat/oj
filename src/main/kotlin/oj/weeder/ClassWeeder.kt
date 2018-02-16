package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor


/**
 * Rule:
 *  - Every class must contain at least one explicit constructor.
 */
class ClassWeeder : CSTNodeVisitor() {
    class NoConstructorFoundInClass(className: String) : WeedError("Class \"$className\" must have constructor; found none.")
    class ClassNameAndConstructorNameMismatch(className: String, constructorName: String) :
            WeedError("Class name \"$className\" does not match constructor name " +
                    "\"$constructorName\"")

    override fun visitClassDeclaration(node: CSTNode) {
        val className = node.children[2].lexeme
        val constructorDeclarationNodes = node.getDescendants("ConstructorDeclaration")
        if (constructorDeclarationNodes.isEmpty()) {
            throw NoConstructorFoundInClass(className)
        }
        constructorDeclarationNodes.forEach {
            val constructorDeclarator = it.children[1]
            val simpleName = constructorDeclarator.children[0]
            val identifier = simpleName.children[0]
            val constructorName = identifier.lexeme
            if (constructorName != className) {
                throw ClassNameAndConstructorNameMismatch(className, constructorName)
            }
        }
    }
}

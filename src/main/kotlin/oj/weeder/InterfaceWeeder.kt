package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

/**
 * Rule:
 *  - An interface method cannot be static, final, or native.
 */

class InterfaceWeeder : CSTNodeVisitor() {
    class InterfaceMethodIsProtectedError(methodName: String) : WeedError("Interface method $methodName is protected, which is not allowed.")
    class InterfaceMethodIsStaticError(methodName: String) : WeedError("Interface method $methodName is static, which is not allowed.")
    class InterfaceMethodIsFinalError(methodName: String) : WeedError("Interface method $methodName is final, which is not allowed.")
    class InterfaceMethodIsNativeError(methodName: String) : WeedError("Interface method $methodName is native, which is not allowed.")

    override fun visitInterfaceMemberDeclaration(node: CSTNode) {
        val abstractMethodDeclarationNode = node.children[0]
        val methodHeaderNode = abstractMethodDeclarationNode.children[0]
        val modifiersNode = methodHeaderNode.children[0]

        val modifiers = modifiersNode.getDescendants("Modifier").map({ it.children[0].name }).toSet()

        val isStatic = "static" in modifiers
        val isFinal = "final" in modifiers
        val isNative = "native" in modifiers
        val isProtected = "protected" in modifiers

        if (isStatic) {
            val methodDeclaratorNode = methodHeaderNode.children[2]
            val methodIdentifierNode = methodDeclaratorNode.children[0]
            val methodName = methodIdentifierNode.lexeme

            throw InterfaceMethodIsStaticError(methodName)
        }

        if (isFinal) {
            val methodDeclaratorNode = methodHeaderNode.children[2]
            val methodIdentifierNode = methodDeclaratorNode.children[0]
            val methodName = methodIdentifierNode.lexeme

            throw InterfaceMethodIsFinalError(methodName)
        }

        if (isNative) {
            val methodDeclaratorNode = methodHeaderNode.children[2]
            val methodIdentifierNode = methodDeclaratorNode.children[0]
            val methodName = methodIdentifierNode.lexeme

            throw InterfaceMethodIsNativeError(methodName)
        }

        if (isProtected) {
            val methodDeclaratorNode = methodHeaderNode.children[2]
            val methodIdentifierNode = methodDeclaratorNode.children[0]
            val methodName = methodIdentifierNode.lexeme

            throw InterfaceMethodIsProtectedError(methodName)
        }
    }
}

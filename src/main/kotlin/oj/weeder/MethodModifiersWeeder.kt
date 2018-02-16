package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

/**
 * Rules:
 *  - An abstract method cannot be static.
 *  - A static method cannot be final.
 *  - A native method must be static.
 */
class MethodModifiersWeeder : CSTNodeVisitor() {
    class AbstractMethodIsStaticError(methodName: String) : WeedError("Abstract method $methodName is also static")
    class StaticMethodIsFinalError(methodName: String) : WeedError("Static method $methodName is also final")
    class NativeMethodIsNotStaticError(methodName: String): WeedError("Native method $methodName is not static")

    override fun visitMethodHeader(node: CSTNode) {
        val methodHeaderNode = node
        val modifiersNode = methodHeaderNode.children[0]
        val modifiers = modifiersNode.getDescendants("Modifier").map({ it.children[0].name }).toSet()

        val isStatic = "static" in modifiers
        val isAbstract = "abstract" in modifiers

        // Rule: An abstract method cannot be static.
        if (isAbstract && isStatic) {
            val methodDeclaratorNode = methodHeaderNode.children[2]
            val methodIdentifierNode = methodDeclaratorNode.children[0]
            val methodName = methodIdentifierNode.lexeme

            throw AbstractMethodIsStaticError(methodName)
        }

        val isFinal = "final" in modifiers

        // Rule: A static method cannot be final.
        if (isStatic && isFinal) {
            val methodDeclaratorNode = methodHeaderNode.children[2]
            val methodIdentifierNode = methodDeclaratorNode.children[0]
            val methodName = methodIdentifierNode.lexeme

            throw StaticMethodIsFinalError(methodName)
        }

        val isNative = "native" in modifiers

        // Rule: A native method must be static.
        if (isNative && !isStatic) {
            val methodDeclaratorNode = methodHeaderNode.children[2]
            val methodIdentifierNode = methodDeclaratorNode.children[0]
            val methodName = methodIdentifierNode.lexeme

            throw NativeMethodIsNotStaticError(methodName)
        }
    }
}

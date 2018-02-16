package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

/**
 * Rule:
 *  - A method has a body if and only if it is neither abstract nor native.
 */
class MethodIsAbstractOrNativeIFFItHasNoMethodBodyWeeder : CSTNodeVisitor() {
    class AbstractOrNativeMethodHasBodyError(methodName: String, methodType: String): WeedError("Method with name \"$methodName\" is $methodType but has a body.")
    class NeitherAbstractNorNativeMethodHasNoBodyError(methodName: String): WeedError("Method with name \"$methodName\" is neither abstract nor native but has no body.")

    override fun visitMethodDeclaration(node: CSTNode) {
        val methodHeaderNode = node.children[0]
        val methodBodyNode = node.children[1]

        val modifiersNode = methodHeaderNode.children[0]
        val modifiers = modifiersNode.getDescendants("Modifier").map({ it.children[0].name }).toSet()

        val isAbstract = "abstract" in modifiers
        val isNative = "native" in modifiers

        val hasMethodBody = methodBodyNode.children.size == 1 && methodBodyNode.children[0].name == "Block"

        if (isAbstract || isNative) {
            if (hasMethodBody) {
                val methodDeclaratorNode = methodHeaderNode.children[2]
                val methodIdentifierNode = methodDeclaratorNode.children[0]
                val methodName = methodIdentifierNode.lexeme
                val methodType = if (isNative) { "native"} else { "abstract"}
                throw AbstractOrNativeMethodHasBodyError(methodName, methodType)
            }
        }

        if (!hasMethodBody) {
            if (!(isAbstract || isNative)) {
                val methodDeclaratorNode = methodHeaderNode.children[2]
                val methodIdentifierNode = methodDeclaratorNode.children[0]
                val methodName = methodIdentifierNode.lexeme
                throw NeitherAbstractNorNativeMethodHasNoBodyError(methodName)
            }
        }
    }
}

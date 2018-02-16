package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

/**
 * Rule:
 *  - No field can be final
 */
class FieldWeeder : CSTNodeVisitor() {
    class FieldIsFinalError(fieldName: String): WeedError("Field \"$fieldName\" is declared final.")

    override fun visitFieldDeclaration(node: CSTNode) {
        val modifiersNode = node.children[0]
        val modifiers = modifiersNode.getDescendants("Modifier").map({ it.children[0].name }).toSet()
        val isFinal = "final" in modifiers

        if (isFinal) {
            val child = node.children[2]
            val fieldName = if (child.name == "IDENTIFIER") {
                child.lexeme
            } else {
                val identifierNode = child.children[0]
                identifierNode.lexeme
            }

            throw FieldIsFinalError(fieldName)
        }
    }
}

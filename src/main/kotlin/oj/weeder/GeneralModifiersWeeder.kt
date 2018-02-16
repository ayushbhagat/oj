package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

class GeneralModifiersWeeder : CSTNodeVisitor() {
    class AbstractAndFinalInModifiersError : WeedError("Invalid Modifiers Detected")
    class VisibilityNotFoundError : WeedError("Visibility not found in modifiers. Should be one of \"protected\" or \"public\"")

    override fun visitModifiers(node: CSTNode) {
        val modifiers = node.getDescendants("Modifier").map({ it.children[0].name }).toSet()

        val isFinal = "final" in modifiers
        val isAbstract = "abstract" in modifiers

        if (isFinal && isAbstract) {
            throw AbstractAndFinalInModifiersError()
        }

        // TODO: This isn't true of interfaces
        // Interfaces default to public

        val isPublic = "public" in modifiers
        val isProtected = "protected" in modifiers

        if (!isPublic && !isProtected) {
            throw VisibilityNotFoundError()
        }
    }
}

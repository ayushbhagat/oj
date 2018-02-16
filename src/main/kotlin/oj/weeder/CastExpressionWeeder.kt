package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

class CastExpressionWeeder : CSTNodeVisitor() {
    class CastExpressionError(reason: String) : WeedError("Illegal cast expression: $reason")

    override fun visitCastExpression(node: CSTNode) {
        val expression = node.children[1]
        if (expression.name == "Expression") {
            var curChild = expression
            while (curChild.name != "Name") {
                if (curChild.children.size != 1) {
                    throw CastExpressionError(
                            "Size is not 1, current size: ${curChild.children.size}")
                }
                curChild = curChild.children[0]
            }
        }
    }
}

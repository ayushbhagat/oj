package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

class IntegerRangeWeeder : CSTNodeVisitor() {
    class IntLessThanLowerBoundError(num: String) : WeedError("Integer < " +
            "lower bound: $num")
    class IntGreaterThanUpperBoundError(num: String) : WeedError("Integer > " +
            "upper bound: $num")

    override fun visitUnaryExpression(node: CSTNode) {
        var isNegative = false
        if (node.children.size == 2 && node.children[0].name == "-") {
            val unaryExpression = node.children[1]
            if (unaryExpression.name == "UnaryExpression") {
                val unaryExpressionNotPlusMinus = unaryExpression.children[0]
                if (unaryExpressionNotPlusMinus.name == "UnaryExpressionNotPlusMinus") {
                    val primary = unaryExpressionNotPlusMinus.children[0]
                    if (primary.name == "Primary") {
                        val primaryNotNewArray = primary.children[0]
                        if (primaryNotNewArray.name == "PrimaryNoNewArray") {
                            val literal = primaryNotNewArray.children[0]
                            if (literal.name == "Literal") {
                                val integer = literal.children[0]
                                if (integer.name == "INTEGER") {
                                    val num = integer.lexeme
                                    isNegative = true
                                    if ("-$num".toLong() < Int.MIN_VALUE) {
                                        throw IntLessThanLowerBoundError(num)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isNegative) {
            super.visitUnaryExpression(node)
        }
    }

    override fun visitInteger(node: CSTNode) {
        // Since we short cut exit for negative integers, we can assume this integer is positive.
        val num = node.lexeme
        if (num.toLong() > Int.MAX_VALUE) {
            throw IntGreaterThanUpperBoundError(num)
        }
        super.visitInteger(node)
    }
}

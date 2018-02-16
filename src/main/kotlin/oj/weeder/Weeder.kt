package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

open class WeedError(reason: String) : Exception(reason)

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

/**
 * Rule:
 *  - An interface method cannot be static, final, or native.
 */

class InterfaceWeeder : CSTNodeVisitor() {
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
    }
}

/**
 * Rule:
 *  - Every class must contain at least one explicit constructor.
 */
class ClassWeeder : CSTNodeVisitor() {
    class NoConstructorFoundInClass(className: String) : WeedError("Class \"$className\" must have constructor; found none.")

    override fun visitClassDeclaration(node: CSTNode) {
        val className = node.children[2].lexeme
        val constructorDeclarationNodes = node.getDescendants("ConstructorDeclaration")
        if (constructorDeclarationNodes.isEmpty()) {
            throw NoConstructorFoundInClass(className)
        }
    }
}

/**
 * Rule:
 *  - No field can be final
 */

class FieldWeeder : CSTNodeVisitor() {
    class FieldIsFinalError(fieldName: String): WeedError("Field \"$fieldName\" is declared final.")
    class UnexpectedError(reason: String) : Exception(reason)

    override fun visitFieldDeclaration(node: CSTNode) {
        val modifiersNode = node.children[0]
        val modifiers = modifiersNode.getDescendants("Modifier").map({ it.children[0].name }).toSet()
        val isFinal = "final" in modifiers

        if (isFinal) {
            val nodeContainingVariableName = node.children[2].children[0]
            val identifierNodes = nodeContainingVariableName.getDescendants("IDENTIFIER")

            if (identifierNodes.size != 1) {
                throw UnexpectedError("Found more than one identifier when searching for field")
            }

            val identifierNode = identifierNodes.first()
            val fieldName = identifierNode.lexeme

            throw FieldIsFinalError(fieldName)
        }
    }
}

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

class Weeder {
    companion object {
        fun weed(root: CSTNode) : CSTNode {
            ClassWeeder().visit(root)
            GeneralModifiersWeeder().visit(root)
            MethodIsAbstractOrNativeIFFItHasNoMethodBodyWeeder().visit(root)
            InterfaceWeeder().visit(root)
            MethodModifiersWeeder().visit(root)
            FieldWeeder().visit(root)
            IntegerRangeWeeder().visit(root)

            /**
             * TODO: Do we satisfy the following rule?
             *  - A formal parameter of a method must not have an initializer.
             */

            return root
        }
    }
}
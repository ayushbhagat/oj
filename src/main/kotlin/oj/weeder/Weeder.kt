package oj.weeder

import oj.models.CSTNode
import oj.models.CSTNodeVisitor
import java.util.*

open class WeedError(reason: String) : Exception(reason)

class GeneralModifiersWeeder : CSTNodeVisitor() {
    class AbstractAndFinalInModifiersError : WeedError("Invalid Modifiers Detected")
    class VisibilityNotFoundError : WeedError("Visibility not found in modifiers. Should be one of \"protected\" or \"public\"")

    override fun visitModifiers(node: CSTNode) {
        val modifiers = node.getDescendents("Modifier").map({ it.children[0].name }).toSet()

        val isFinal = "final" in modifiers
        val isAbstract = "abstract" in modifiers

        if (isFinal && isAbstract) {
            throw AbstractAndFinalInModifiersError()
        }

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
        val modifiers = modifiersNode.getDescendents("Modifier").map({ it.children[0].name }).toSet()

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
        val modifiers = modifiersNode.getDescendents("Modifier").map({ it.children[0].name }).toSet()

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

class Weeder {
    companion object {
        fun weed(root: CSTNode) : CSTNode {
            GeneralModifiersWeeder().visit(root)
            MethodIsAbstractOrNativeIFFItHasNoMethodBodyWeeder().visit(root)
            MethodModifiersWeeder().visit(root)

            /**
             * TODO: Do we satisfy the following rule?
             *  - A formal parameter of a method must not have an initializer.
             */


            return root
        }
    }
}
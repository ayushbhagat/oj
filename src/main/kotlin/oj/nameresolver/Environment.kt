package oj.nameresolver

import oj.models.CSTNode
import java.util.*

data class Declaration(val name: String, val node: CSTNode)

data class Environment(
    private val scopes: LinkedList<LinkedList<Declaration>> = LinkedList()
) {
    class LookupFailed(name: String): Exception("Declaration $name not found in environment.")

    fun pushScope() {
        scopes.push(LinkedList())
    }

    fun popScope() {
        scopes.pop()
    }

    fun push(name: String, declarationNode: CSTNode) {
        if (scopes.isEmpty()) {
            pushScope()
        }

        scopes.peek().push(Declaration(name, declarationNode))
    }

    fun lookup(name: String) : CSTNode {
        for (scope in scopes) {
            for (declaration in scope) {
                if (declaration.name == name) {
                    return declaration.node
                }
            }
        }

        throw LookupFailed(name)
    }
}
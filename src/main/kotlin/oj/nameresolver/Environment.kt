package oj.nameresolver

import oj.models.CSTNode
import java.util.*

data class Environment(
    private val scopes: LinkedList<LinkedList<Entry>> = LinkedList()
) {
    data class Entry(val name: String, val node: CSTNode)

    class LookupFailed: Exception {
        constructor(name: String): super("Lookup failed: Declaration $name not found in environment.")
        constructor(): super("Lookup failed")
    }

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

        scopes.peek().push(Entry(name, declarationNode))
    }

    fun find(name: String) : CSTNode {
        for (scope in scopes) {
            for (entry in scope) {
                if (entry.name == name) {
                    return entry.node
                }
            }
        }

        throw LookupFailed(name)
    }

    fun find(predicate: (Entry) -> Boolean) : CSTNode {
        for (scope in scopes) {
            for (entry in scope) {
                if (predicate(entry)) {
                    return entry.node
                }
            }
        }

        throw LookupFailed()
    }

    fun findAll(name: String) : List<CSTNode> {
        return findAll({ it.name == name})
    }

    fun findAll(predicate: (Entry) -> Boolean) : List<CSTNode> {
        val found = mutableListOf<CSTNode>()

        for (scope in scopes) {
            for (entry in scope) {
                if (predicate(entry)) {
                    found.add(entry.node)
                }
            }
        }

        return found
    }

    fun contains(name: String): Boolean {
        return contains({ it.name == name })
    }

    fun contains(predicate: (Entry) -> Boolean): Boolean {
        for (scope in scopes) {
            for (entry in scope) {
                if (predicate(entry)) {
                    return true
                }
            }
        }

        return false
    }
}
package oj.models

import java.util.*

class FoundNoChild: Exception("Found no descendants, expected 1.")
class FoundManyChildren: Exception("Found many descendants, expected 1.")

class FoundNoDescendant : Exception("Found no descendants, expected 1.")
class FoundManyDescendants: Exception("Found many descendants, expected 1.")

data class CSTNode(
    val name: String,
    val lexeme: String,
    val children: MutableList<CSTNode> = mutableListOf(),
    var declaration: CSTNode? = null
) {

    fun getDescendant(name: String): CSTNode {
        return getDescendant({ it.name == name })
    }

    fun getChild(name: String): CSTNode {
        return getChild({ it.name == name })
    }

    fun getChildren(name: String): List<CSTNode> {
        return getChildren({ it.name == name })
    }

    fun getDescendants(name: String): List<CSTNode> {
        return getDescendants({ it.name == name})
    }

    fun getChild(predicate: (CSTNode) -> Boolean): CSTNode {
        val matchingChildren = getChildren(predicate)

        if (matchingChildren.isEmpty()) {
            throw FoundNoChild()
        }

        if (matchingChildren.size > 1) {
            throw FoundManyChildren()
        }

        return matchingChildren[0]
    }

    fun getDescendant(predicate: (CSTNode) -> Boolean): CSTNode {
        val descendants = getDescendants(predicate)
        if (descendants.isEmpty()) {
            throw FoundNoDescendant()
        }

        if (descendants.size > 1) {
            throw FoundManyDescendants();
        }

        return descendants[0]
    }

    fun getChildren(predicate: (CSTNode) -> Boolean) : List<CSTNode> {
        return children.filter(predicate)
    }

    fun getDescendants(predicate: (CSTNode) -> Boolean): List<CSTNode> {
        val descendants = mutableListOf<CSTNode>()
        val stack = LinkedList<CSTNode>()
        stack.add(this)

        while (stack.isNotEmpty()) {
            val child = stack.pop()

            if (predicate(child)) {
                descendants.add(child)
            } else {
                child.children.reversed().forEach({ grandChild -> stack.push(grandChild) })
            }
        }

        return descendants
    }
}
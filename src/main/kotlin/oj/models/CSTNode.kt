package oj.models

import java.util.*

data class CSTNode(
    val name: String,
    val lexeme: String,
    val children: MutableList<CSTNode> = mutableListOf()
) {
    fun getDescendants(name: String): List<CSTNode> {
        return getDescendants({ it.name == name})
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
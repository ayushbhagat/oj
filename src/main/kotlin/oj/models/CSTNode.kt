package oj.models

import java.util.*

data class CSTNode(
    val name: String,
    val lexeme: String,
    val children: MutableList<CSTNode> = mutableListOf()
) {
    fun getDescendants(name: String): Set<CSTNode> {
        return getDescendants({ it.name == name})
    }

    fun getDescendants(predicate: (CSTNode) -> Boolean): Set<CSTNode> {
        val descendants = mutableSetOf<CSTNode>()
        val queue: Queue<CSTNode> = LinkedList<CSTNode>()
        queue.offer(this)

        while (queue.isNotEmpty()) {
            val child = queue.remove()

            if (predicate(child)) {
                descendants.add(child)
            } else {
                child.children.forEach({ grandChild -> queue.offer(grandChild) })
            }
        }

        return descendants
    }
}
package oj.models

import java.util.*

open class CSTNodeError(reason: String): Exception(reason)

class FoundNoChild: CSTNodeError("Found no descendants, expected 1.")
class FoundManyChildren: CSTNodeError("Found many descendants, expected 1.")

class FoundNoDescendant : CSTNodeError("Found no descendants, expected 1.")
class FoundManyDescendants: CSTNodeError("Found many descendants, expected 1.")

class RanOutOfIds: CSTNodeError("Tried to assign unique id to CSTNode, but ran out.")

data class CSTNode(
    val name: String,
    val lexeme: String = "",
    val children: MutableList<CSTNode> = mutableListOf()
) {
    private var id = CSTNode.nextId()
    companion object {
        val declarations: MutableMap<CSTNode, CSTNode> = mutableMapOf()
        val types: MutableMap<CSTNode, Type> = mutableMapOf()
        var id: Int = 0

        fun nextId(): Int {
            if (CSTNode.id == Int.MAX_VALUE) {
                throw RanOutOfIds()
            }
            CSTNode.id += 1
            return CSTNode.id
        }
    }

    fun getId(): Int {
        return id
    }

    // TODO: Two names that are value equal will resolve to the same declaration. This is incorrect. Fix.
    fun setDeclaration(node: CSTNode) {
        if (name != "Name") {
            throw CSTNodeError("Tried to assign a declaration to a \"$name\" != \"Name\" node.")
        }

        CSTNode.declarations[this] = node
    }

    fun getDeclaration(): CSTNode {
        if (name != "Name") {
            throw CSTNodeError("Tried to retrieve a declaration for a \"$name\" != \"Name\" node.")
        }

        val declaration = CSTNode.declarations[this]
        if (declaration == null) {
            throw CSTNodeError("\"$name\" node doesn't have a declaration assigned to it.")
        }

        return declaration
    }

    fun setType(type: Type) {
        if (name != "Name") {
            throw CSTNodeError("Tried to assign a type to a \"$name\" != \"Name\" node.")
        }

        CSTNode.types[this] = type
    }

    fun getType(): Type {
        if (name != "Name") {
            throw CSTNodeError("Tried to retrieve a type for a \"$name\" != \"Name\" node.")
        }

        val type = CSTNode.types[this]
        if (type == null) {
            throw CSTNodeError("\"$name\" node doesn't have a type assigned to it.")
        }

        return type
    }

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
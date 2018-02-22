package oj.nameresolver

import oj.models.CSTNode
import oj.models.CSTNodeVisitor

class InvalidNameResolutionInsertion(reason: String): Exception(reason)

class NameResolutionVisitor(private val packages: Map<String, Set<CSTNode>>) : CSTNodeVisitor() {
    private val environment = Environment()
    private val nameResolution = mutableMapOf<CSTNode, CSTNode>()

    fun getNameResolution(): Map<CSTNode, CSTNode> {
        return nameResolution
    }

    fun addNameResolution(nameNode: CSTNode, declaration: CSTNode) {
        if (nameNode.name != "Name") {
            println(nameNode)
            throw InvalidNameResolutionInsertion("Tried to assign a declaration to a non-Name node: ${nameNode.name}")
        }

        if (nameResolution.contains(nameNode)) {
            println(nameNode)
            throw InvalidNameResolutionInsertion("Tried to re-define the declaration assigned with a node")
        }

        nameResolution[nameNode] = declaration
    }
}

class NameResolver {
    companion object {
        fun resolveNames(packages: Map<String, Set<CSTNode>>) : Map<CSTNode, CSTNode> {
            val visitor = NameResolutionVisitor(packages)

            packages.forEach({(_, compilationUnits) ->
                compilationUnits.forEach({ compilationUnit ->
                    visitor.visit(compilationUnit)
                })
            })

            return visitor.getNameResolution()
        }
    }
}
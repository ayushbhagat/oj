package oj.nameresolver

import oj.models.CSTNode
import oj.models.CSTNodeVisitor
import oj.models.FoundNoChild
import oj.models.FoundNoDescendant

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

    /**
     * TypeName:
     * In a single-type-import declaration (§7.5.1)
     */
    override fun visitSingleTypeImportDeclaration(node: CSTNode) {
        val nameNode = node.getChild("Name")
    }

    /**
     * TypeName:
     * In an extends clause in a class declaration (§8.1.3)
     */
    override fun visitSuper(node: CSTNode) {
        val nameNode = node.getDescendant("Name")
    }

    /**
     * TypeName:
     * In an implements clause in a class declaration (§8.1.4)
     * In an extends clause in an interface declaration (§9.1.2)
     */
    override fun visitInterfaceType(node: CSTNode) {
        val nameNode = node.getDescendant("Name")
    }

    /**
     * TypeName:
     * As a Type In a field declaration (§8.3, §9.3)
     */
    override fun visitFieldDeclaration(node: CSTNode) {
        val typeNode = node.getChild("Type")

        try {
            val nameNode = typeNode.getDescendant("Name")

        } catch (ex: FoundNoDescendant) {
            super.visitFieldDeclaration(node)
        }
    }

    /**
     * TypeName:
     * As the result type of a method (§8.4, §9.4)
     */
    override fun visitMethodHeader(node: CSTNode) {
        try {
            val typeNode = node.getChild("Type")
            val nameNode = typeNode.getDescendant("Name")


        } catch (ex: FoundNoChild) {
            super.visitMethodHeader(node)
        } catch (ex: FoundNoDescendant) {
            super.visitMethodHeader(node)
        }
    }

    /**
     * TypeName:
     * As the type of a formal parameter of a method or constructor
     * (§8.4.1, §8.8.1, §9.4)
     */
    override fun visitFormalParameter(node: CSTNode) {
        val typeNode = node.getChild("Type")

        try {
            val nameNode = typeNode.getDescendant("Name")


        } catch (ex: FoundNoDescendant) {
            super.visitFormalParameter(node)
        }
    }

    /**
     * TypeName:
     * As the type of a local variable (§14.4)
     */
    override fun visitLocalVariableDeclaration(node: CSTNode) {
        val typeNode = node.getChild("Type")

        try {
            val nameNode = typeNode.getDescendant("Name")


        } catch (ex: FoundNoDescendant) {
            super.visitLocalVariableDeclaration(node)
        }
    }

    /**
     * TypeName:
     * As the class type which is to be instantiated in an unqualified class instance
     * creation expression (§15.9)
     */
    override fun visitClassInstanceCreationExpression(node: CSTNode) {
        val classTypeNode = node.getChild("ClassType")
        val nameNode = classTypeNode.getDescendant("Name")
    }

    /**
     * TypeName:
     * As the element type of an array to be created in an array creation expression (§15.10)
     */
    override fun visitArrayCreationExpression(node: CSTNode) {
        try {
            val classOrInterfaceTypeNode = node.getChild("ClassOrInterfaceType")
            val nameNode = classOrInterfaceTypeNode.getChild("Name")

        } catch (ex: FoundNoChild) {
            super.visitArrayCreationExpression(node)
        }
    }

    /**
     * TypeName:
     * As the type mentioned in the cast operator of a cast expression (§15.16)
     */
    override fun visitCastExpression(node: CSTNode) {
        val nameNode = when (node.children[1].name) {
            "Name" -> node.children[1]
            "Expression" -> node.children[1].getDescendant("Name")
            else -> null
        }

        if (nameNode == null) {
            super.visitCastExpression(node)
            return
        }
    }

    /**
     * TypeName:
     * As the type that follows the instanceof relational operator (§15.20.2)
     */
    override fun visitRelationalExpression(node: CSTNode) {
        try {
            val referenceTypeNode = node.getChild("ReferenceType")
            val nameNode = referenceTypeNode.getDescendant("Name")

        } catch (ex: FoundNoChild) {
            super.visitRelationalExpression(node)
        }
    }

    /**
     * ExpressionName:
     * As the array reference expression in an array access expression (§15.13)
     */
    override fun visitArrayAccess(node: CSTNode) {
        try {
            val nameNode = node.getChild("Name")

        } catch (ex: FoundNoChild) {
            super.visitArrayAccess(node)
        }
    }

    /**
     * ExpressionName:
     * As a PostfixExpression (§15.14)
     *
     * Note: We merged PostfixExpression into UnaryExpressionNotPlusMinus
     */
    override fun visitUnaryExpressionNotPlusMinus(node: CSTNode) {
        try {
            val nameNode = node.getChild("Name")

        } catch (ex: FoundNoChild) {
            super.visitUnaryExpressionNotPlusMinus(node)
        }
    }

    /**
     * ExpressionName:
     * As the left-hand operand of an assignment operator (§15.26)
     */
    override fun visitLeftHandSide(node: CSTNode) {
        try {
            val nameNode = node.getChild("Name")

        } catch (ex: FoundNoChild) {
            super.visitLeftHandSide(node)
        }
    }

    /**
     * MethodName
     * Before the “(” in a method invocation expression (§15.12)
     */
    override fun visitMethodInvocation(node: CSTNode) {
        try {
            val nameNode = node.getChild("Name")


        } catch (ex: FoundNoChild) {
//            try {
//                /**
//                 * Rule:
//                 * MethodInvocation -> Primary . IDENTIFIER ( ArgumentListOpt )
//                 *
//                 * Can we resolve this method name without type information?
//                 * We have to know what type Primary resolves to so that we can check if
//                 * a method with name Identifier exists within that Type
//                 */
//                val nameNode = node.getChild("IDENTIFIER")
//                // Can we resolve this without type checking? We'll have to know what type
//                // Primary is associated with
//
//            } catch (ex: FoundNoChild) {
//                super.visitMethodInvocation(node)
//            }

            super.visitMethodInvocation(node)
        }
    }

    /**
     * PackageOrTypeName
     * In a type-import-on-demand declaration (§7.5.2)
     */
    override fun visitTypeImportOnDemandDeclaration(node: CSTNode) {
        val nameNode = node.getChild("Name")
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
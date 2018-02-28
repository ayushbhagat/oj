package oj.nameresolver

import oj.models.CSTNode
import oj.models.CSTNodeVisitor
import oj.models.FoundNoChild
import oj.models.FoundNoDescendant

open class NameResolutionError(reason: String) : Exception(reason)
class InvalidNameResolutionInsertion(reason: String): NameResolutionError(reason)
class TypeImportedFromTwoImportOnDemandDeclarations(typeName: String)
    : NameResolutionError("Type $typeName was imported from two different import on demand declarations")

val isClassOrInterfaceDeclaration = { node: CSTNode ->
    node.name == "ClassDeclaration" || node.name == "InterfaceDeclaration"
}

class NameResolutionVisitor(
    private val environment: Environment,
    val typesDeclaredInPackages: Map<String, Map<String, CSTNode>>
) : CSTNodeVisitor() {
    private val nameResolution = mutableMapOf<CSTNode, CSTNode>()
    var importOnDemandDeclarationEnvironment: Environment = Environment()

    fun attachImportOnDemandEnvironment(declarations: Environment) {
        importOnDemandDeclarationEnvironment = declarations
    }

    fun detachImportOnDemandEnvironment() {
        importOnDemandDeclarationEnvironment = Environment()
    }

    fun getNameResolution(): Map<CSTNode, CSTNode> {
        return nameResolution
    }

    fun addNameResolution(nameNode: CSTNode, declaration: CSTNode) {
        /**
         * TODO:
         * Consider adding declaration as a field to nameNode
         * This won't work when our program has two Name nodes that are value equal to each other.
         */
    }

    fun resolveTypeName(nameNode: CSTNode) {
        val name = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme })

        if (name.size == 1) {
            val typeName = name[0]
            val predicate = { entry: Environment.Entry ->
                entry.name == typeName && isClassOrInterfaceDeclaration(entry.node)
            }

            if (environment.contains(predicate)) {
                addNameResolution(nameNode, environment.find(predicate))
            } else {
                val iodLookup = importOnDemandDeclarationEnvironment.findAll(typeName)
                if (iodLookup.size == 1) {
                    addNameResolution(nameNode, iodLookup[0])
                } else if (iodLookup.size > 1) {
                    throw NameResolutionError("TypeName \"$typeName\" maps to types in more than one import on demand declarations")
                } else {
                    throw NameResolutionError("TypeName \"$typeName\" doesn't exist.")
                }
            }
        } else {
            val typeName = name.last()
            val packageName = name.subList(0, name.size - 1).joinToString(".")
            if (!typesDeclaredInPackages.containsKey(packageName)) {
                throw NameResolutionError("Tried to access type \"$typeName\" in package \"$packageName\", but package \"$packageName\" doesn't exist.")
            }

            val typesInPackage = typesDeclaredInPackages[packageName]!!
            if (!typesInPackage.containsKey(typeName)) {
                throw NameResolutionError("Tried to access type \"$typeName\" in package \"$packageName\", but type \"$typeName\" doesn't exist.")
            }

            addNameResolution(nameNode, typesInPackage[typeName]!!)
        }
    }

    fun resolveExpressionName(nameNode: CSTNode) {
        val name = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme })
        if (name.size == 1) {
            val expressionName = name[0]
            // Ensure that the expression name is defined. If so, add it to the name resolution, otherwise throw error.
            val declarationNode = environment.find(expressionName)
            addNameResolution(nameNode, declarationNode)
        } else {
            // TODO: We don't currently support resolution of ambiguous names. This is A3.
        }
    }

    fun resolveMethodName(nameNode: CSTNode) {
        val name = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme })
        if (name.size == 1) {
            val methodName = name[0]
            // Ensure that the method name is defined. If so, add it to the name resolution, otherwise throw error.
            val declarationNode = environment.find({ it.name == methodName && it.node.name == "MethodDeclaration" })
            addNameResolution(nameNode, declarationNode)
        } else {
            // TODO: We don't currently support resolution of ambiguous names. This is A3.
        }
    }

    override fun visitClassDeclaration(node: CSTNode) {
        val typeName = node.getChild("IDENTIFIER").lexeme
        environment.push(typeName, node)

        super.visitClassDeclaration(node)
    }

    override fun visitInterfaceDeclaration(node: CSTNode) {
        val typeName = node.getChild("IDENTIFIER").lexeme
        environment.push(typeName, node)

        super.visitInterfaceDeclaration(node)
    }

    /**
     * TypeName:
     * In a single-type-import declaration (§7.5.1)
     */
    override fun visitSingleTypeImportDeclaration(node: CSTNode) {
        // TODO: Do we need to do anything for this?
        val nameNode = node.getChild("Name")
        val name = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme })

        if (name.size > 1) {

        }
    }

    /**
     * TypeName:
     * In an extends clause in a class declaration (§8.1.3)
     */
    override fun visitSuper(node: CSTNode) {
        val nameNode = node.getDescendant("Name")
        resolveTypeName(nameNode)

        super.visitSuper(node)
    }

    /**
     * TypeName:
     * In an implements clause in a class declaration (§8.1.4)
     * In an extends clause in an interface declaration (§9.1.2)
     */
    override fun visitInterfaceType(node: CSTNode) {
        val nameNode = node.getDescendant("Name")
        resolveTypeName(nameNode)

        super.visitInterfaceType(node)
    }

    /**
     * TypeName:
     * As a Type In a field declaration (§8.3, §9.3)
     */
    override fun visitFieldDeclaration(node: CSTNode) {
        /**
         * Resolve the type in this declaration
         */
        val typeNode = node.getChild("Type")

        try {
            val nameNode = typeNode.getDescendant("Name")
            resolveTypeName(nameNode)
        } catch (ex: FoundNoDescendant) {
            // Do nothing
        }

        super.visitFieldDeclaration(node)

        /**
         * Add declaration to environment
         */
        val fieldDeclarationName = when {
            node.children[2].name == "IDENTIFIER" -> node.children[2].lexeme
            else -> {
                node.getChild("VariableDeclarator").getChild("IDENTIFIER").lexeme
            }
        }

        if (environment.contains({ it.name == fieldDeclarationName && it.node.name == "FieldDeclaration" })) {
            throw NameResolutionError("Tried to declare field \"$fieldDeclarationName\" twice in the same class.")
        }

        environment.push(fieldDeclarationName, node)
    }

    override fun visitClassBodyDeclarations(node: CSTNode) {
        /**
         * Step 1: Add all static fields to environment
         */
        val fieldDeclarations = node.getDescendants("FieldDeclaration")
        val staticFieldDeclarations = fieldDeclarations.filter({ declaration ->
            val modifiers = declaration.getChild("Modifiers")
            modifiers.getDescendants("static").isNotEmpty()
        })

        staticFieldDeclarations.forEach({ this.visit(it) })

        /**
         * Step 2: Get all static methods.
         */
        val methodDeclarations = node.getDescendants("MethodDeclaration")
        val staticMethodDeclarations = methodDeclarations.filter({ methodDeclaration ->
            val methodHeader = methodDeclaration.getChild("MethodHeader")
            val modifiers = methodHeader.getChild("Modifiers")

            modifiers.getDescendants("static").isNotEmpty()
        })

        /**
         * Step 3: Add all static methods to the environment.
         */

        staticMethodDeclarations.forEach({ staticMethodDeclaration ->
            val methodHeader = staticMethodDeclaration.getChild("MethodHeader")
            this.visit(methodHeader)

            val methodDeclarator = methodHeader.getChild("MethodDeclarator")
            val methodName = methodDeclarator.getChild("IDENTIFIER").lexeme

            environment.push(methodName, staticMethodDeclaration)
        })

        /**
         * Step 4: Do name resolution on static method bodies.
         * This is done before we adding any non-static fields because static method bodies can't use instance fields.
         */
        staticMethodDeclarations.forEach({ staticMethodDeclaration ->
            val methodBody = staticMethodDeclaration.getChild("MethodBody")
            this.visit(methodBody)
        })

        /**
         * Step 5: Add all instance fields to the environment.
         */
        val instanceFieldDeclarations = fieldDeclarations - staticFieldDeclarations
        instanceFieldDeclarations.forEach({ this.visit(it) })

        /**
         * Step 6: Add all instance methods to the environment.
         */
        val instanceMethodDeclarations = methodDeclarations - staticMethodDeclarations
        instanceMethodDeclarations.forEach({ instanceMethodDeclaration ->
            val methodHeader = instanceMethodDeclaration.getChild("MethodHeader")
            this.visit(methodHeader)

            val methodDeclarator = methodHeader.getChild("MethodDeclarator")
            val methodName = methodDeclarator.getChild("IDENTIFIER").lexeme

            environment.push(methodName, instanceMethodDeclaration)
        })

        /**
         * Step 7: Do name resolution on constructors and instance methods.
         */
        instanceMethodDeclarations.forEach({ instanceMethodDeclaration ->
            val methodBody = instanceMethodDeclaration.getChild("MethodBody")
            this.visit(methodBody)
        })

        val constructorDeclarations = node.getDescendants("ConstructorDeclaration")
        constructorDeclarations.forEach({ this.visit(it) })
    }

    override fun visitBlock(node: CSTNode) {
        environment.pushScope()
        super.visitBlock(node)
        environment.popScope()
    }

    /**
     * TypeName:
     * As the result type of a method (§8.4, §9.4)
     */
    override fun visitMethodHeader(node: CSTNode) {
        try {
            val typeNode = node.getChild("Type")
            val nameNode = typeNode.getDescendant("Name")
            resolveTypeName(nameNode)
        } catch (ex: FoundNoChild) {
            // Do nothing
        } catch (ex: FoundNoDescendant) {
            // Do nothing
        }

        super.visitMethodHeader(node)
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
            resolveTypeName(nameNode)
        } catch (ex: FoundNoDescendant) {
            // Do nothing
        }

        super.visitFormalParameter(node)

        val name = node.getChild("IDENTIFIER").lexeme
        environment.push(name, node)
    }

    /**
     * TypeName:
     * As the type of a local variable (§14.4)
     */
    override fun visitLocalVariableDeclaration(node: CSTNode) {
        val typeNode = node.getChild("Type")

        try {
            val nameNode = typeNode.getDescendant("Name")
            resolveTypeName(nameNode)
        } catch (ex: FoundNoDescendant) {
        }

        super.visitLocalVariableDeclaration(node)

        val variableDeclaratorNode = node.getChild("VariableDeclarator")
        val variableName = variableDeclaratorNode.getChild("IDENTIFIER").lexeme

        if (environment.contains({ it.name == variableName && (it.node.name == "LocalVariableDeclaration" || it.node.name == "FormalParameter") })) {
            throw NameResolutionError("Tried to declare variable \"$variableName\" twice in the same scope.")
        }

        environment.push(variableName, node)
    }

    /**
     * TypeName:
     * As the class type which is to be instantiated in an unqualified class instance
     * creation expression (§15.9)
     */
    override fun visitClassInstanceCreationExpression(node: CSTNode) {
        val classTypeNode = node.getChild("ClassType")
        val nameNode = classTypeNode.getDescendant("Name")
        resolveTypeName(nameNode)

        super.visitClassInstanceCreationExpression(node)
    }

    /**
     * TypeName:
     * As the element type of an array to be created in an array creation expression (§15.10)
     */
    override fun visitArrayCreationExpression(node: CSTNode) {
        try {
            val classOrInterfaceTypeNode = node.getChild("ClassOrInterfaceType")
            val nameNode = classOrInterfaceTypeNode.getChild("Name")
            resolveTypeName(nameNode)
        } catch (ex: FoundNoChild) {
        }

        super.visitArrayCreationExpression(node)
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

        if (nameNode != null) {
            resolveTypeName(nameNode)
        }

        super.visitCastExpression(node)
    }

    /**
     * TypeName:
     * As the type that follows the instanceof relational operator (§15.20.2)
     */
    override fun visitRelationalExpression(node: CSTNode) {
        try {
            val referenceTypeNode = node.getChild("ReferenceType")
            val nameNode = referenceTypeNode.getDescendant("Name")
            resolveTypeName(nameNode)
        } catch (ex: FoundNoChild) {
        }

        super.visitRelationalExpression(node)
    }

    /**
     * ExpressionName:
     * As the array reference expression in an array access expression (§15.13)
     */
    override fun visitArrayAccess(node: CSTNode) {
        try {
            val nameNode = node.getChild("Name")
            resolveExpressionName(nameNode)
        } catch (ex: FoundNoChild) {
        }
        super.visitArrayAccess(node)
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
            resolveExpressionName(nameNode)
        } catch (ex: FoundNoChild) {
        }
        super.visitUnaryExpressionNotPlusMinus(node)
    }

    /**
     * ExpressionName:
     * As the left-hand operand of an assignment operator (§15.26)
     */
    override fun visitLeftHandSide(node: CSTNode) {
        try {
            val nameNode = node.getChild("Name")
            resolveExpressionName(nameNode)
        } catch (ex: FoundNoChild) {
        }

        super.visitLeftHandSide(node)
    }

    /**
     * MethodName
     * Before the “(” in a method invocation expression (§15.12)
     */
    override fun visitMethodInvocation(node: CSTNode) {
        try {
            val nameNode = node.getChild("Name")
            resolveMethodName(nameNode)
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
        }
        super.visitMethodInvocation(node)
    }

    /**
     * PackageOrTypeName
     * In a type-import-on-demand declaration (§7.5.2)
     */
    override fun visitTypeImportOnDemandDeclaration(node: CSTNode) {
        val nameNode = node.getChild("Name")
    }
}

class DetectedTwoTypesWithSameNameInSamePackage(packageName: String, typeName: String)
    : Exception("Detected two types named \"$typeName\" within package \"$packageName\".")

class ImportOnDemandDeclarationDetectedForNonExistentPackage(packageName: String)
    : Exception("Detected an import on demand declaration for package \"$packageName\", when no such package exists.")

class SingleTypeImportDeclarationDetectedForNonExistentPackage(packageName: String)
    : Exception("Detected a single type import declaration for package \"$packageName\", when no such package exists.")

class SingleTypeImportDeclarationDetectedForNonExistentType(packageName: String, typeName: String)
    : Exception("Detected a single type import declaration \"$packageName.$typeName\", when no type \"$typeName\" exists in package \"$packageName\".")

class NameResolver {
    companion object {
        fun resolveNames(packages: Map<String, List<CSTNode>>) : Map<CSTNode, CSTNode> {
            val globalEnvironment = Environment()


            val typesDeclaredInPackage = packages.mapValues({(packageName, compilationUnits) ->
                val packageTypeDeclarations = compilationUnits
                    .flatMap({ compilationUnit -> compilationUnit.getDescendants(isClassOrInterfaceDeclaration) })
                    .fold(mutableMapOf<String, CSTNode>(), { map, typeDeclarationNode ->
                        val typeName = typeDeclarationNode.getChild("IDENTIFIER").lexeme

                        if (map.contains(typeName)) {
                            throw DetectedTwoTypesWithSameNameInSamePackage(packageName, typeName)
                        }

                        map[typeName] = typeDeclarationNode
                        map
                    })

            packageTypeDeclarations
            })

            val getTypesDeclaredInPackage = { packageName: String -> typesDeclaredInPackage[packageName]!! }

            val visitor = NameResolutionVisitor(globalEnvironment, typesDeclaredInPackage)

            packages.forEach({(packageName, compilationUnits) ->
                compilationUnits.forEach({ compilationUnit ->
                    // TODO:
                    // Type import on demand declarations can import things from types, so it's not as simple as this
                    val iodEnvironment = compilationUnit
                        .getDescendants("TypeImportOnDemandDeclaration")
                        .map({ node -> node.getDescendants("IDENTIFIER") })
                        .map({ identifiers -> identifiers.map({ it.lexeme }).joinToString(".")})
                        .filter({ it != packageName })
                        .fold(Environment(), { iodEnvironment, iodPackageName ->
                            if (!packages.contains(iodPackageName)) {
                                // TODO: Only throw when iodPackageName is not a prefix of any package names
                                throw ImportOnDemandDeclarationDetectedForNonExistentPackage(iodPackageName)
                            }

                            val typesDeclaredInThisPackage = getTypesDeclaredInPackage(iodPackageName)

                            typesDeclaredInThisPackage.forEach({(typeName, typeDeclarationNode) ->
                                iodEnvironment.push(typeName, typeDeclarationNode)
                            })

                            iodEnvironment
                        })

                    visitor.attachImportOnDemandEnvironment(iodEnvironment)
                    globalEnvironment.pushScope()

                    getTypesDeclaredInPackage(packageName)
                        .forEach({(typeName, typeDeclaration) ->
                            globalEnvironment.push(typeName, typeDeclaration)
                        })

                    // TODO: Output error when a single type import declaration makes available a type that we have available locally
                    compilationUnit
                        .getDescendants("SingleTypeImportDeclaration")
                        .forEach({ stiDeclaration ->
                            val name = stiDeclaration.getDescendants("IDENTIFIER").map({ it.lexeme })
                            val stiPackageName = name.subList(0, name.size - 1).joinToString(".")
                            val typeName = name.last()

                            if (!typesDeclaredInPackage.contains(stiPackageName)) {
                                throw SingleTypeImportDeclarationDetectedForNonExistentPackage(stiPackageName)
                            }

                            val typesInPackage = typesDeclaredInPackage[stiPackageName]!!

                            if (!typesInPackage.contains(typeName)) {
                                throw SingleTypeImportDeclarationDetectedForNonExistentType(stiPackageName, typeName)
                            }

                            globalEnvironment.push(typeName, typesInPackage[typeName]!!)
                        })

                    visitor.visit(compilationUnit)

                    globalEnvironment.popScope()
                    visitor.detachImportOnDemandEnvironment()
                })
            })

            return visitor.getNameResolution()
        }
    }
}
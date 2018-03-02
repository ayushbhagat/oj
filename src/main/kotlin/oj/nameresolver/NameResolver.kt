package oj.nameresolver

import oj.models.CSTNode
import oj.models.CSTNodeVisitor
import oj.models.FoundNoChild
import oj.models.FoundNoDescendant

open class NameResolutionError(reason: String) : Exception(reason)
open class HierarchyCheckingError(reason: String) : NameResolutionError(reason)
open class UnimplementedInterfaceException(interfaceName: String, interfaceMethodName: String, className: String) : HierarchyCheckingError("Interface method \"$interfaceName.$interfaceMethodName\" not implemented in \"$className\"")

class InvalidNameResolutionInsertion(reason: String): NameResolutionError(reason)
class TypeImportedFromTwoImportOnDemandDeclarations(typeName: String)
    : NameResolutionError("Type $typeName was imported from two different import on demand declarations")

val isClassOrInterfaceDeclaration = { node: CSTNode ->
    node.name == "ClassDeclaration" || node.name == "InterfaceDeclaration"
}


fun areTypesTheSame(type: CSTNode, otherType: CSTNode) : Boolean {
    if (type.name != "Type" || otherType.name != "Type") {
        throw NameResolutionError("Tried to compare non-types")
    }

    if (type.children[0].name != otherType.children[0].name) {
        return false
    }

    if (type.children[0].name == "PrimitiveType") {
        val primitiveType = type.children[0]
        val otherPrimitiveType = otherType.children[0]

        if (primitiveType.children[0].name != otherPrimitiveType.children[0].name) {
            return false
        }
    } else {
        val referenceType = type.children[0]
        val otherReferenceType = otherType.children[0]

        if (referenceType.children[0].name != otherReferenceType.children[0].name) {
            return false
        }

        if (referenceType.children[0].name == "ClassOrInterfaceType") {
            val typeName = referenceType.children[0].getChild("Name")
            val otherTypeName = otherReferenceType.children[0].getChild("Name")

            if (typeName.getDeclaration() != otherTypeName.getDeclaration()) {
                return false
            }
        } else {
            val arrayType = referenceType.children[0]
            val otherArrayType = otherReferenceType.children[0]

            if (arrayType.children[0].name != otherArrayType.children[0].name) {
                return false
            }

            if (arrayType.children[0].name == "PrimitiveType") {
                if (arrayType.children[0] != otherArrayType.children[0]) {
                    return false
                }
            } else {
                val typeName = arrayType.children[0]
                val otherTypeName = otherArrayType.children[0]

                if (typeName.getDeclaration() != otherTypeName.getDeclaration()) {
                    return false
                }
            }
        }
    }

    return true
}

fun areMethodSignaturesTheSame(methodDeclarator: CSTNode, otherMethodDeclarator: CSTNode): Boolean {
    if (methodDeclarator.name != "MethodDeclarator" || otherMethodDeclarator.name != "MethodDeclarator") {
        throw NameResolutionError("Tried to compare non-method declarators")
    }

    val methodName = methodDeclarator.getChild("IDENTIFIER").lexeme
    val otherMethodName = otherMethodDeclarator.getChild("IDENTIFIER").lexeme

    if (methodName != otherMethodName) {
        return false
    }

    val formalParameters = methodDeclarator.getDescendants("FormalParameter")
    val otherFormalParameters = otherMethodDeclarator.getDescendants("FormalParameter")

    if (otherFormalParameters.size != formalParameters.size) {
        return false
    }

    for (i in IntRange(0, otherFormalParameters.size - 1)) {
        val formalParameter = formalParameters[i]
        val otherFormalParameter = otherFormalParameters[i]

        val formalParameterType = formalParameter.getChild("Type")
        val otherFormalParameterType = otherFormalParameter.getChild("Type")

        if (!areTypesTheSame(formalParameterType, otherFormalParameterType)) {
            return false
        }
    }

    return true
}

fun areMethodReturnTypesTheSame(methodHeader: CSTNode, otherMethodHeader: CSTNode): Boolean {
    if (methodHeader.name != "MethodHeader" || otherMethodHeader.name != "MethodHeader") {
        throw NameResolutionError("Tried to compare non-method headers")
    }

    val methodReturnType = methodHeader.children[1]
    val otherMethodReturnType = otherMethodHeader.children[1]

    if (methodReturnType.name != otherMethodReturnType.name) {
        return false
    }

    if (methodReturnType.name == "Type") {
        if (!areTypesTheSame(methodReturnType, otherMethodReturnType)) {
            return false
        }
    }

    return true
}

class NameResolutionVisitor(
    private val environment: Environment,
    val typesDeclaredInPackages: Map<String, Map<String, CSTNode>>
) : CSTNodeVisitor() {
    private val nameResolution = mutableMapOf<CSTNode, CSTNode>()
    var importOnDemandDeclarationEnvironment: Environment = Environment()
    private var shouldResolveNamesInTypeBodies: Boolean = true

    fun attachImportOnDemandEnvironment(declarations: Environment) {
        importOnDemandDeclarationEnvironment = declarations
    }

    fun detachImportOnDemandEnvironment() {
        importOnDemandDeclarationEnvironment = Environment()
    }

    fun getNameResolution(): Map<CSTNode, CSTNode> {
        return nameResolution
    }

    fun setShouldResolveNamesInTypeBodies(shouldResolveNamesInTypeBodies: Boolean) {
        this.shouldResolveNamesInTypeBodies = shouldResolveNamesInTypeBodies
    }

    fun addNameResolution(nameNode: CSTNode, declaration: CSTNode) {
        /**
         * TODO:
         * Consider adding declaration as a field to nameNode
         * This won't work when our program has two Name nodes that are value equal to each other.
         */

        nameNode.setDeclaration(declaration)
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
            val pkg = name.subList(0, name.size - 1)
            val packageName = pkg.joinToString(".")

            if (!typesDeclaredInPackages.containsKey(packageName)) {
                throw NameResolutionError("Tried to access type \"$typeName\" in package \"$packageName\", but package \"$packageName\" doesn't exist.")
            }

            for (i in IntRange(1, pkg.size - 1)) {
                val prefix = pkg.subList(0, i)
                val prefixPackageName = prefix.joinToString(".")

                if (typesDeclaredInPackages.containsKey(prefixPackageName)) {
                    throw NameResolutionError("Prefix \"$prefixPackageName\" of a package \"$packageName\" used to resolve type \"$typeName\" contained a type.")
                }
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

        for (child in node.children) {
            if (child.name == "ClassBody") {
                continue
            }

            this.visit(child)
        }

        if (!shouldResolveNamesInTypeBodies) {
            return
        }

        val interfaceTypes = node.getChild("InterfacesOpt").getDescendants("InterfaceType")
        val interfaceNames = interfaceTypes.map({ it.getDescendant("Name") })
        val isNameAnInterfaceDeclaration = { interfaceName: CSTNode ->
            interfaceName.getDeclaration().name == "InterfaceDeclaration"
        }

        val className = node.getChild("IDENTIFIER").lexeme

        if (interfaceNames.isNotEmpty() && !interfaceNames.all(isNameAnInterfaceDeclaration)) {
            throw HierarchyCheckingError("Class \"$className\" tried to implement a class.")
        }

        val interfaceDeclarations = interfaceNames.map({ it.getDeclaration() })

        for (i in IntRange(0, interfaceDeclarations.size - 1)) {
            for (j in IntRange(0, interfaceDeclarations.size - 1)) {
                if (i == j) {
                    continue
                }

                if (interfaceDeclarations[i] === interfaceDeclarations[j]) {
                    val interfaceDeclaration = interfaceDeclarations[i]
                    val interfaceName = interfaceDeclaration.getChild("IDENTIFIER").lexeme
                    throw HierarchyCheckingError("Class $className implements interface $interfaceName more than once")
                }
            }
        }

        fun getInterfaceInheritedMethods(interfaceDeclaration: CSTNode, visited: MutableSet<CSTNode>): List<CSTNode> {
            if (interfaceDeclaration.name != "InterfaceDeclaration") {
                throw HierarchyCheckingError("Expected an interface declaration but found ${interfaceDeclaration.name} instead.")
            }

            if (visited.filter({ it === interfaceDeclaration }).isNotEmpty()) {
                val interfaceName = interfaceDeclaration.getChild("IDENTIFIER").lexeme
                throw HierarchyCheckingError("Encountered a cycle in the interface hierarchy. Saw interface \"$interfaceName\" twice when analyzing the interfaces that class \"$className\" implements.")
            }

            val interfaceBody = interfaceDeclaration.getChild("InterfaceBody")
            val abstractMethodDeclarations = interfaceBody.getDescendants("AbstractMethodDeclaration")

            val extendedInterfaceNames = interfaceDeclaration.getChild("ExtendsInterfacesOpt").getDescendants("Name")
            val extendedInterfaceDeclarations = extendedInterfaceNames.map({ it.getDeclaration() })

            visited.add(interfaceDeclaration)
            val extendedAbstractMethods = extendedInterfaceDeclarations.flatMap({
                val temp = getInterfaceInheritedMethods(it, visited)
                temp
            })

            visited.remove(interfaceDeclaration)

            return extendedAbstractMethods + abstractMethodDeclarations
        }

        val allInterfaceMethods = interfaceDeclarations.map({interfaceDeclaration ->
            val interfaceName = interfaceDeclaration.getChild("IDENTIFIER").lexeme
            Pair(interfaceName, getInterfaceInheritedMethods(interfaceDeclaration, mutableSetOf()))
        })

        fun getInheritedDescendants(classDeclaration: CSTNode, descendantName: String) : List<CSTNode> {
            if (classDeclaration.name != "ClassDeclaration") {
                throw HierarchyCheckingError("Expected a class declaration, but found ${classDeclaration.name} instead.")
            }

            val classBody = classDeclaration.getChild("ClassBody")
            val descendantNodes = classBody.getDescendants(descendantName)

            val superOptNode = classDeclaration.getChild("SuperOpt")
            val superNode = if (superOptNode.children.size == 1) superOptNode.getChild("Super") else null

            return (
                if (superNode != null) {
                    getInheritedDescendants(superNode.getDescendant("Name").getDeclaration(), descendantName)
                } else {
                    if (importOnDemandDeclarationEnvironment.contains("Object")) {
                        val baseSuperClass = importOnDemandDeclarationEnvironment.find("Object")
                        val baseSuperClassClassBody = baseSuperClass.getChild("ClassBody")
                        baseSuperClassClassBody.getDescendants(descendantName)
                    } else {
                        listOf()
                    }
                } + descendantNodes
            )
        }

        val classBody = node.getChild("ClassBody")

        val fieldDeclarations = getInheritedDescendants(node, "FieldDeclaration")
        val ownFieldDeclarations = classBody.getDescendants("FieldDeclaration")
        fun isOwnFieldDeclaration(otherFieldDeclaration: CSTNode) : Boolean {
            return ownFieldDeclarations.filter({ it === otherFieldDeclaration }).isNotEmpty()
        }

        val visitAndInsertFieldDeclarationIntoEnvironment = { fieldDeclaration: CSTNode ->
            if (isOwnFieldDeclaration(fieldDeclaration)) {
                this.visit(fieldDeclaration)
            }

            val fieldDeclarationName = when {
                fieldDeclaration.children[2].name == "IDENTIFIER" -> fieldDeclaration.children[2].lexeme
                else -> {
                    fieldDeclaration.getChild("VariableDeclarator").getChild("IDENTIFIER").lexeme
                }
            }

            if (isOwnFieldDeclaration(fieldDeclaration)) {
                if (environment.contains({ it.name == fieldDeclarationName && it.node.name == "FieldDeclaration" && isOwnFieldDeclaration(it.node) })) {
                    throw NameResolutionError("Tried to declare field \"$fieldDeclarationName\" twice in the same class.")
                }
            }

            environment.push(fieldDeclarationName, fieldDeclaration)
        }

        val staticFieldDeclarations = fieldDeclarations.filter({ declaration ->
            val modifiers = declaration.getChild("Modifiers")
            modifiers.getDescendants("static").isNotEmpty()
        })

        /**
         * Step 1: Add all static fields to environment
         */
        staticFieldDeclarations.forEach(visitAndInsertFieldDeclarationIntoEnvironment)

        /**
         * Step 2: Get all static methods.
         */
        val methodDeclarations = getInheritedDescendants(node, "MethodDeclaration")
        val ownMethodDeclarations = classBody.getDescendants("MethodDeclaration")

        fun isOwnMethodDeclaration(otherDeclaration: CSTNode): Boolean {
            return ownMethodDeclarations.filter({ it === otherDeclaration }).isNotEmpty()
        }

        val staticMethodDeclarations = methodDeclarations.filter({ methodDeclaration ->
            val methodHeader = methodDeclaration.getChild("MethodHeader")
            val modifiers = methodHeader.getChild("Modifiers")

            modifiers.getDescendants("static").isNotEmpty()
        })

        /**
         * Resolve the method header and insert method into environment.
         *
         * A method body can use use methods from the entire class. Therefore, we need to have all method headers
         * by the time we resolve our first method body.
         *
         * This is also useful because we need to compare signatures.
         */
        val visitMethodHeaderAndInsertMethodDeclarationIntoEnvironment = { methodDeclaration: CSTNode ->
            val methodHeader = methodDeclaration.getChild("MethodHeader")
            if (isOwnMethodDeclaration(methodDeclaration)) {
                /**
                 * This will push the formals to the environment, so we have to pop them off.
                 */
                environment.pushScope()
                this.visit(methodHeader)
                environment.popScope()
            }

            val methodDeclarator = methodHeader.getChild("MethodDeclarator")
            val methodName = methodDeclarator.getChild("IDENTIFIER").lexeme

            environment.push(methodName, methodDeclaration)
        }

        /**
         * Step 3: Add all static methods names to the environment.
         */
        staticMethodDeclarations.forEach(visitMethodHeaderAndInsertMethodDeclarationIntoEnvironment)

        /**
         * Resolve the method body. Since we need access to the method formals in the body, we need to push them to
         * the environment before visiting the body, and thus we resolve the whole MethodDeclaration, instead of only
         * the body.
         */
        val pushFormalParametersAndVisitMethodBody = fun (methodDeclaration: CSTNode) {
            if (!isOwnMethodDeclaration(methodDeclaration)) {
                return
            }

            environment.pushScope()
            val methodDeclarator = methodDeclaration.getChild("MethodHeader").getChild("MethodDeclarator")
            val methodName = methodDeclarator.getChild("IDENTIFIER").lexeme

            val isMethodAlreadyDefined = fun (entry: Environment.Entry): Boolean {
                if (entry.name != methodName || !isOwnMethodDeclaration(entry.node) || entry.node === methodDeclaration) {
                    return false
                }

                val otherMethodDeclaration = entry.node
                val otherMethodDeclarator = otherMethodDeclaration.getChild("MethodHeader").getChild("MethodDeclarator")
                return areMethodSignaturesTheSame(methodDeclarator, otherMethodDeclarator)
            }
            if (environment.contains(isMethodAlreadyDefined)) {
                throw NameResolutionError("Tried to define a method \"$methodName\" in a class with the same type signature as one already defined.")
            }

            this.visit(methodDeclaration)
            environment.popScope()
        }

        /**
         * Step 4: Resolve static the method body.
         */
        staticMethodDeclarations.forEach(pushFormalParametersAndVisitMethodBody)

        /**
         * Step 5: Add all instance fields to the environment.
         */
        val instanceFieldDeclarations = fieldDeclarations.filter({ it !in staticFieldDeclarations })
        instanceFieldDeclarations.forEach(visitAndInsertFieldDeclarationIntoEnvironment)

        /**
         * Step 6: Add all instance methods to the environment.
         */
        val instanceMethodDeclarations = methodDeclarations.filter({ it !in staticMethodDeclarations })
        instanceMethodDeclarations.forEach(visitMethodHeaderAndInsertMethodDeclarationIntoEnvironment)


        /**
         * Step 7: Do name resolution on constructors and instance methods.
         */
        instanceMethodDeclarations.forEach(pushFormalParametersAndVisitMethodBody)

        val constructorDeclarations = classBody.getDescendants("ConstructorDeclaration")
        constructorDeclarations.forEach({
            environment.pushScope()
            this.visit(it)
            environment.popScope()
        })

        val ownInstanceMethodDeclarations = instanceMethodDeclarations.filter({ isOwnMethodDeclaration(it) })
        val classModifiers = node.getChild("Modifiers").getDescendants("Modifier").map({ it.children[0].name }).toSet()
        val isClassAbstract = "abstract" in classModifiers

        allInterfaceMethods.forEach({ (interfaceName, abstractMethodDeclarations) ->
            abstractMethodDeclarations.forEach({abstractMethodDeclaration ->
                val interfaceMethodHeader = abstractMethodDeclaration.getChild("MethodHeader")
                val interfaceMethodDeclarator = interfaceMethodHeader.getChild("MethodDeclarator")
                val interfaceMethodName = interfaceMethodDeclarator.getChild("IDENTIFIER").lexeme

                val instanceMethodsThatImplementInterfaceMethod = ownInstanceMethodDeclarations
                    .filter(fun(instanceMethodDeclaration: CSTNode): Boolean {
                        val instanceMethodHeader = instanceMethodDeclaration.getChild("MethodHeader")
                        val instanceMethodDeclarator = instanceMethodHeader.getChild("MethodDeclarator")

                        val instanceMethodName = instanceMethodDeclarator.getChild("IDENTIFIER").lexeme

                        if (areMethodSignaturesTheSame(interfaceMethodDeclarator, instanceMethodDeclarator)) {
                            if (!areMethodReturnTypesTheSame(interfaceMethodHeader, instanceMethodHeader)) {
                                throw HierarchyCheckingError(
                                    "Instance method \"$instanceMethodName\" in class \"$className\" tried to implement a method in interface \"$interfaceName\" with the same signature, but a different return type"
                                )
                            }

                            return true
                        }

                        return false
                    })



                if (!isClassAbstract && instanceMethodsThatImplementInterfaceMethod.isEmpty()) {
                    throw UnimplementedInterfaceException(interfaceName, interfaceMethodName, className)
                }
            })
        })

    }

    override fun visitInterfaceDeclaration(node: CSTNode) {
        val typeName = node.getChild("IDENTIFIER").lexeme
        environment.push(typeName, node)

        for (child in node.children) {
            if (child.name == "InterfaceBody") {
                continue
            }

            this.visit(child)
        }

        if (!shouldResolveNamesInTypeBodies) {
            return
        }

        val interfaceBody = node.getChild("InterfaceBody")
        val abstractMethodDeclarations = interfaceBody.getDescendants("AbstractMethodDeclaration")

        abstractMethodDeclarations.forEach({ abstractMethodDeclaration ->
            val methodHeader = abstractMethodDeclaration.getChild("MethodHeader")
            environment.pushScope()
            this.visit(methodHeader)
            environment.popScope()

            val methodDeclarator = methodHeader.getChild("MethodDeclarator")
            val methodName = methodDeclarator.getChild("IDENTIFIER").lexeme

            val isAbstractMethodAlreadyDefined = fun (entry: Environment.Entry) : Boolean {
                if (entry.name != methodName || entry.node.name != "AbstractMethodDeclaration") {
                    return false
                }

                val otherMethodHeader = entry.node.getChild("MethodHeader")
                val otherMethodDeclarator = otherMethodHeader.getChild("MethodDeclarator")

                return areMethodSignaturesTheSame(methodDeclarator, otherMethodDeclarator)
            }

            if (environment.contains(isAbstractMethodAlreadyDefined)) {
                throw NameResolutionError("Tried to define a method \"$methodName\" in an interface with the same type signature as one already defined.")
            }

            environment.push(methodName, abstractMethodDeclaration)
        })

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
        if (environment.contains({ it.name == name && it.node.name == "FormalParameter"})) {
            throw NameResolutionError("Tried to declare two formal parameters with the name \"$name\"")
        }

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
            this.visit(node.getChild("UnaryExpressionNotPlusMinus"))
        } else {
            super.visitCastExpression(node)
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

            val doesPackageExist = fun (packageName: String): Boolean {
                for ((pkgName, _) in packages) {
                    if (pkgName.startsWith(packageName)) {
                        return true
                    }
                }

                return false
            }

            val getTypesDeclaredInPackage = fun (packageName: String) : Map<String, CSTNode> {
                val typesInPackage = typesDeclaredInPackage[packageName]
                if (typesInPackage == null) {
                    if (doesPackageExist(packageName)) {
                        return mapOf()
                    }

                    throw NameResolutionError("Tried to get Types declared in non-existent package \"$packageName\"")
                }

                return typesInPackage
            }

            val visitor = NameResolutionVisitor(globalEnvironment, typesDeclaredInPackage)

            val visitPackages = { shouldResolveNamesInTypeBodies : Boolean ->
                packages.forEach({(packageName, compilationUnits) ->
                    compilationUnits.forEach({ compilationUnit ->
                        val iodPackages = compilationUnit
                            .getDescendants("TypeImportOnDemandDeclaration")
                            .map({ node -> node.getDescendants("IDENTIFIER") })
                            .map({ identifiers -> identifiers.map({ it.lexeme }).joinToString(".")})
                            .filter({ it != packageName })
                            .toMutableSet()

                        if (doesPackageExist("java.lang")) {
                            iodPackages.add("java.lang")
                        }

                        val iodEnvironment = iodPackages
                            .fold(Environment(), { iodEnvironment, iodPackageName ->
                                if (!doesPackageExist(iodPackageName)) {
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

                        visitor.setShouldResolveNamesInTypeBodies(shouldResolveNamesInTypeBodies)
                        visitor.visit(compilationUnit)

                        globalEnvironment.popScope()
                        visitor.detachImportOnDemandEnvironment()
                    })
                })
            }

            visitPackages(false)
            visitPackages(true)

            return visitor.getNameResolution()
        }
    }
}
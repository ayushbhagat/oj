package oj.nameresolver

import oj.models.CSTNode
import oj.models.CSTNodeVisitor
import oj.models.FoundNoChild
import oj.models.FoundNoDescendant

open class NameResolutionError(reason: String) : Exception(reason)
open class HierarchyCheckingError(reason: String) : NameResolutionError(reason)
open class UnimplementedAbstractMethodException(nonAbstractTypeName: String, abstractTypeName: String, abstractMethodName: String) : HierarchyCheckingError("Abstract method \"$abstractTypeName.$abstractMethodName\" is not implemented in class hierarchy of \"$nonAbstractTypeName\"")
open class AbstractMethodOverridesConcreteImplementation(abstractTypeName: String, abstractMethodName: String) : HierarchyCheckingError("Abstract method \"$abstractTypeName.$abstractMethodName\" overrides non-abstract method in own class hierarchy")
open class ClassExtendsNonClass(className: String, interfaceName: String) : HierarchyCheckingError("Class \"$className\" extends \"$interfaceName\"")
open class ClassImplementsNonInterface(className: String, nonInterfaceName: String) : HierarchyCheckingError("Class \"$className\" implements \"$nonInterfaceName\"")
open class ClassImplementsAnInterfaceMoreThanOnce(className: String, interfaceName: String): HierarchyCheckingError("Class \"$className\" implements interface \"$interfaceName\" more than once.")
open class ClassExtendsFinalSuperClass(className: String, finalClassName: String): HierarchyCheckingError("Class \"$className\" extends \"$finalClassName\"")
open class ClassHierarchyIsCyclic(className: String, firstDuplicatedClassName: String): HierarchyCheckingError("Detected a duplicate class \"$firstDuplicatedClassName\" in class \"$className\"'s hierarchy.")
open class DuplicateMethodsDetectedInClass(className: String, methodName: String): HierarchyCheckingError("Method \"$methodName\" is duplicated within class \"$className\"")
open class DuplicateConstructorsDetectedInClass(className: String): HierarchyCheckingError("Constructor is duplicated within class \"$className\"")
open class TwoMethodsInClassHierarchyWithSameSignatureButDifferentReturnTypes(className: String, methodName: String): HierarchyCheckingError("Two methods with the same name, \"$methodName\", but different return type exist in the class hierarchy (including interface methods) of class \"$className\"")
open class IllegalMethodReplacement(className: String, methodName: String): HierarchyCheckingError("A method \"$methodName\" in class \"$className\" illegally overrides a method it inherits")
open class InterfaceExtendsAnotherMoreThanOnce(interfaceName: String, duplicateInterfaceName: String): HierarchyCheckingError("Interface \"$interfaceName\" extends interface \"$duplicateInterfaceName\" more than once.")
open class InterfaceExtendsNonInterface(interfaceName: String, nonInterfaceName: String) : HierarchyCheckingError("Interface \"$interfaceName\" extends a non-interface \"$nonInterfaceName\"")
open class InterfaceHierarchyIsCyclic(interfaceName: String, firstDuplicateInterface: String) : HierarchyCheckingError("Detected a duplicate interface \"$firstDuplicateInterface\" in interface \"$interfaceName\"'s hierarchy.")
open class DuplicateMethodsDetectedInInterface(interfaceName: String, methodName: String): HierarchyCheckingError("Detected a duplicate method \"$methodName\" in interface \"$interfaceName\"")
open class TwoMethodsInInterfaceHierarchyWithSameSignatureButDifferentReturnTypes(interfaceName: String, methodName: String): HierarchyCheckingError("Two methods with the same name, \"$methodName\", but different return type exist in the interface hierarchy (including interface methods) of interface \"$interfaceName\"")


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

fun areMethodSignaturesTheSame(declarator: CSTNode, otherDeclarator: CSTNode): Boolean {
    val isComparingMethodDeclarators = declarator.name == "MethodDeclarator" && otherDeclarator.name == "MethodDeclarator"
    val isComparingConstructorDeclarators = declarator.name == "ConstructorDeclarator" && otherDeclarator.name == "ConstructorDeclarator"

    if (!isComparingMethodDeclarators && !isComparingConstructorDeclarators) {
        throw NameResolutionError("Tried to compare non-method declarators")
    }

    val methodName = if (isComparingMethodDeclarators)
        declarator.getChild("IDENTIFIER").lexeme
    else
        declarator.getChild("SimpleName").getChild("IDENTIFIER").lexeme


    val otherMethodName = if (isComparingMethodDeclarators)
        otherDeclarator.getChild("IDENTIFIER").lexeme
    else
        otherDeclarator.getChild("SimpleName").getChild("IDENTIFIER").lexeme

    if (methodName != otherMethodName) {
        return false
    }

    val formalParameters = declarator.getDescendants("FormalParameter")
    val otherFormalParameters = otherDeclarator.getDescendants("FormalParameter")

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

fun canMethodHeadersReplaceOneAnother(methodHeader1: CSTNode, methodHeader2: CSTNode): Boolean {
    val methodDeclarator1 = methodHeader1.getChild("MethodDeclarator")
    val methodDeclarator2 = methodHeader2.getChild("MethodDeclarator")

    val methodName1 = methodDeclarator1.getChild("IDENTIFIER").lexeme
    val methodName2 = methodDeclarator2.getChild("IDENTIFIER").lexeme

    if (areMethodSignaturesTheSame(methodDeclarator1, methodDeclarator2)) {
        if (!areMethodReturnTypesTheSame(methodHeader1, methodHeader2)) {
            throw HierarchyCheckingError("Methods \"$methodName1\" and \"$methodName2\" have the same signature but different return types, which is illegal.")
        }

        return true
    }

    return false
}

fun getInterfaceInheritedMethods(interfaceDeclaration: CSTNode, typeName: String, visited: MutableSet<CSTNode>): List<CSTNode> {
    if (interfaceDeclaration.name != "InterfaceDeclaration") {
        throw HierarchyCheckingError("Expected an interface declaration but found ${interfaceDeclaration.name} instead.")
    }

    if (visited.filter({ it === interfaceDeclaration }).isNotEmpty()) {
        val interfaceName = interfaceDeclaration.getChild("IDENTIFIER").lexeme
        throw InterfaceHierarchyIsCyclic(typeName, interfaceName)
    }

    val interfaceBody = interfaceDeclaration.getChild("InterfaceBody")
    val abstractMethodDeclarations = interfaceBody.getDescendants("AbstractMethodDeclaration")

    val extendedInterfaceNames = interfaceDeclaration.getChild("ExtendsInterfacesOpt").getDescendants("Name")
    val extendedInterfaceDeclarations = extendedInterfaceNames.map({ it.getDeclaration() })

    visited.add(interfaceDeclaration)
    val extendedAbstractMethods = extendedInterfaceDeclarations.flatMap({
        getInterfaceInheritedMethods(it, typeName, visited)
    })

    visited.remove(interfaceDeclaration)

    return extendedAbstractMethods + abstractMethodDeclarations
}

val nodesThatCanHaveModifiers = setOf("Modifiers", "ClassDeclaration", "FieldDeclaration", "MethodHeader", "MethodDeclaration", "AbstractMethodDeclaration", "ConstructorDeclaration", "InterfaceDeclaration")

class TriedToAccessModifiersOfAUnupportedCSTNode(nodeName: String): Exception("Tried to access modifiers in a non-supported CSTNode: $nodeName")

fun getModifiers(declaration: CSTNode): Set<String> {
    if (!nodesThatCanHaveModifiers.contains(declaration.name)) {
        throw TriedToAccessModifiersOfAUnupportedCSTNode(declaration.name)
    }

    val modifiersNode =
        if (declaration.name == "MethodDeclaration" || declaration.name == "AbstractMethodDeclaration") {
            declaration.getChild("MethodHeader").getChild("Modifiers")
        } else {
            declaration.getChild("Modifiers")
        }

    val modifierNodes = modifiersNode.getDescendants("Modifier")

    return modifierNodes.map({ modifierNode -> modifierNode.children[0].name }).toSet()
}

class TriedToGetNameOfAnUnsupportedCSTNode(nodeName: String): Exception("Tried to get name of of an unsupported CSTNode: $nodeName")

fun getDeclarationName(declaration: CSTNode): String {
    if (declaration.name == "ClassDeclaration" || declaration.name == "InterfaceDeclaration") {
        return declaration.getChild("IDENTIFIER").lexeme
    }

    if (declaration.name == "AbstractMethodDeclaration" || declaration.name == "MethodDeclaration") {
        val methodHeader = declaration.getChild("MethodHeader")
        val methodDeclarator = methodHeader.getChild("MethodDeclarator")
        return methodDeclarator.getChild("IDENTIFIER").lexeme
    }

    if (declaration.name == "LocalVariableDeclaration") {
        val variableDeclarator = declaration.getChild("VariableDeclarator")
        return variableDeclarator.getChild("IDENTIFIER").lexeme
    }


    if (declaration.name == "FieldDeclaration") {
        val thirdChild = declaration.children[2]
        if (thirdChild.name == "IDENTIFIER") {
            return thirdChild.lexeme
        }

        val variableDeclarator = thirdChild
        return variableDeclarator.getChild("IDENTIFIER").lexeme
    }

    throw TriedToGetNameOfAnUnsupportedCSTNode(declaration.name)
}

fun ensureThatAbstractMethodsHaveImplementations(nonAbstractTypeName: String, abstractTypeName: String, abstractMethodDeclarations: List<CSTNode>, allNonAbstractInstanceMethodDeclarations: List<CSTNode>) {
    abstractMethodDeclarations.forEach({ abstractMethodDeclaration ->
        val abstractMethodHeader = abstractMethodDeclaration.getChild("MethodHeader")

        val nonAbstractMethodsThatImplementAbstractMethod = allNonAbstractInstanceMethodDeclarations
            .filter(fun(nonAbstractMethodDeclaration: CSTNode): Boolean {
                val nonAbstractMethodHeader = nonAbstractMethodDeclaration.getChild("MethodHeader")
                return canMethodHeadersReplaceOneAnother(abstractMethodHeader, nonAbstractMethodHeader)
            })

        if (nonAbstractMethodsThatImplementAbstractMethod.isEmpty()) {
            val abstractMethodName = getDeclarationName(abstractMethodDeclaration)
            throw UnimplementedAbstractMethodException(nonAbstractTypeName, abstractTypeName, abstractMethodName)
        }
    })
}

class NameResolutionVisitor(
    private val environment: Environment,
    val typesDeclaredInPackages: Map<String, Map<String, CSTNode>>
) : CSTNodeVisitor() {

    enum class ResolutionDepth {
        TypeDeclaration, MethodDeclaration, All
    }

    private var resolutionDepth = ResolutionDepth.All

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

    fun setResolutionDepth(depth: ResolutionDepth) {
        resolutionDepth = depth
    }

    fun addNameResolution(nameNode: CSTNode, declaration: CSTNode) {
        /**
         * TODO:
         * Consider adding declaration as a field to nameNode
         * This won't work when our program has two Name nodes that are value equal to each other.
         */

        nameNode.setDeclaration(declaration)
    }

    private fun getClassInheritedDescendants(classDeclaration: CSTNode, predicate: (CSTNode) -> Boolean) : List<CSTNode> {
        if (classDeclaration.name != "ClassDeclaration") {
            throw HierarchyCheckingError("Expected a class declaration, but found ${classDeclaration.name} instead.")
        }

        val classBody = classDeclaration.getChild("ClassBody")
        val descendantNodes = classBody.getDescendants(predicate)

        val superOptNode = classDeclaration.getChild("SuperOpt")
        val superNode = if (superOptNode.children.size == 1) superOptNode.getChild("Super") else null

        return (
            if (superNode != null) {
                getClassInheritedDescendants(superNode.getDescendant("Name").getDeclaration(), predicate)
            } else {
                if (importOnDemandDeclarationEnvironment.contains("Object")) {
                    val baseSuperClass = importOnDemandDeclarationEnvironment.find("Object")
                    val baseSuperClassClassBody = baseSuperClass.getChild("ClassBody")
                    baseSuperClassClassBody.getDescendants(predicate)
                } else {
                    listOf()
                }
            } + descendantNodes
        )
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

        if (resolutionDepth == ResolutionDepth.TypeDeclaration) {
            return
        }

        val className = getDeclarationName(node)
        val classBody = node.getChild("ClassBody")
        val ownMethodDeclarations = classBody.getDescendants("MethodDeclaration")

        ownMethodDeclarations.forEach({ methodDeclaration ->
            val methodHeader = methodDeclaration.getChild("MethodHeader")
            environment.pushScope()
            this.visit(methodHeader)
            environment.popScope()
        })

        val constructorDeclarations = node.getDescendants("ConstructorDeclaration")

        constructorDeclarations.forEach({ constructorDeclaration ->
            val constructorDeclarator = constructorDeclaration.getChild("ConstructorDeclarator")
            environment.pushScope()
            this.visit(constructorDeclarator)
            environment.popScope()
        })

        if (resolutionDepth == ResolutionDepth.MethodDeclaration) {
            return
        }

        /**
         * TEST:
         *
         * Ensure classes only extend classes.
         */
        val superClassNameNodes = node.getChild("SuperOpt").getDescendants("Name")
        if (superClassNameNodes.size == 1) {
            val superClassDeclaration = superClassNameNodes[0].getDeclaration()
            if (superClassDeclaration.name != "ClassDeclaration") {
                val interfaceName = getDeclarationName(superClassDeclaration)
                throw ClassExtendsNonClass(className, interfaceName)
            }
        }

        /**
         * TEST:
         *
         * Ensure classes only implement interfaces.
         */
        val allImplementedInterfaces = node.getChild("InterfacesOpt").getDescendants("Name").map({ it.getDeclaration() })
        allImplementedInterfaces.forEach({  implementedInterface ->
            if (implementedInterface.name != "InterfaceDeclaration") {
                val nonInterfaceName = getDeclarationName(implementedInterface)
                throw ClassImplementsNonInterface(className, nonInterfaceName)
            }
        })

        /**
         * TEST:
         *
         * Ensure a class doesn't implement an interface more than once.
         */
        for (i in IntRange(0, allImplementedInterfaces.size - 1)) {
            for (j in IntRange(0, allImplementedInterfaces.size - 1)) {
                if (i == j) {
                    continue
                }

                if (allImplementedInterfaces[i] === allImplementedInterfaces[j]) {
                    val interfaceName = getDeclarationName(allImplementedInterfaces[i])
                    throw ClassImplementsAnInterfaceMoreThanOnce(className, interfaceName)
                }
            }
        }

        /**
         * TEST
         *
         * A class must not extend a final class.
         */
        if (superClassNameNodes.size == 1) {
            val superClassDeclaration = superClassNameNodes[0].getDeclaration()
            if ("final" in getModifiers(superClassDeclaration)) {
                val finalClassName = getDeclarationName(superClassDeclaration)
                throw ClassExtendsFinalSuperClass(className, finalClassName)
            }
        }

        /**
         * TEST
         *
         * The hierarchy of a class must be acyclic.
         */
        if (superClassNameNodes.size == 1) {
            val visited: MutableSet<CSTNode> = mutableSetOf()
            var currentClassDeclaration = node
            while (true) {
                if (visited.filter({ it === currentClassDeclaration }).isNotEmpty()) {
                    throw ClassHierarchyIsCyclic(className, getDeclarationName(currentClassDeclaration))
                }
                visited.add(currentClassDeclaration)

                val superClassNameNodesOfCurrentClass = currentClassDeclaration.getChild("SuperOpt").getDescendants("Name")
                if (superClassNameNodesOfCurrentClass.isEmpty()) {
                    break
                }

                val superClassDeclaration = superClassNameNodesOfCurrentClass[0].getDeclaration()

                if (superClassDeclaration.name == "ClassDeclaration") {
                    currentClassDeclaration = superClassDeclaration
                } else {
                    throw ClassExtendsNonClass(
                        getDeclarationName(currentClassDeclaration),
                        getDeclarationName(superClassDeclaration)
                    )
                }
            }
        }

        /**
         * TEST:
         *
         * Ensure no two methods in the same class have the same signature.
         * It also throws if two methods in the same class have the same signature but different return types.
         */
        ownMethodDeclarations.forEach({ methodDeclaration1 ->
            ownMethodDeclarations.forEach(fun (methodDeclaration2: CSTNode) {
                if (methodDeclaration1 === methodDeclaration2) {
                    return
                }

                val methodHeader1 = methodDeclaration1.getChild("MethodHeader")
                val methodHeader2 = methodDeclaration2.getChild("MethodHeader")

                if (canMethodHeadersReplaceOneAnother(methodHeader1, methodHeader2)) {
                    throw DuplicateMethodsDetectedInClass(className, getDeclarationName(methodDeclaration1))
                }
            })
        })

        /**
         * TEST:
         *
         * A class must not declare more than 1 constructor with the same parameter type.
         */
        constructorDeclarations.forEach({ constructorDeclaration ->
            constructorDeclarations.forEach(fun (otherConstructorDeclaration: CSTNode) {
                if (constructorDeclaration === otherConstructorDeclaration) {
                    return
                }
                val constructorDeclarator = constructorDeclaration.getChild("ConstructorDeclarator")
                val otherConstructorDeclarator = otherConstructorDeclaration.getChild("ConstructorDeclarator")
                if (areMethodSignaturesTheSame(constructorDeclarator, otherConstructorDeclarator)) {
                    throw DuplicateConstructorsDetectedInClass(className)
                }
            })
        })

        /**
         * TEST:
         *
         * No two methods (abstract or otherwise) associated with this class can have the same signature, but
         * a different type.
         */
        val allMethodDeclarations = getClassInheritedDescendants(node, { it.name == "MethodDeclaration" })
        val allInterfaceMethodDeclarations = allImplementedInterfaces.flatMap({ getInterfaceInheritedMethods(it, className, mutableSetOf()) })
        val allMethodAndAbstractDeclarations = allMethodDeclarations + allInterfaceMethodDeclarations
        allMethodAndAbstractDeclarations.forEach({ methodDeclaration ->
            allMethodAndAbstractDeclarations.forEach(fun (otherMethodDeclaration: CSTNode) {
                if (methodDeclaration === otherMethodDeclaration) {
                    return
                }
                val methodHeader = methodDeclaration.getChild("MethodHeader");
                val otherMethodHeader = otherMethodDeclaration.getChild("MethodHeader")
                val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                val otherMethodDeclarator = otherMethodHeader.getChild("MethodDeclarator")

                if (areMethodSignaturesTheSame(methodDeclarator, otherMethodDeclarator)) {
                    if (!areMethodReturnTypesTheSame(methodHeader, otherMethodHeader)) {
                        throw TwoMethodsInClassHierarchyWithSameSignatureButDifferentReturnTypes(className, getDeclarationName(methodDeclaration))
                    }
                }
            })
        })

        /**
         * TEST
         *
         * For every method of every interface that this class implements, there exists an implementation
         * in this class or its parents.
         */
        val allNonAbstractInstanceMethodDeclarations = allMethodDeclarations.filter({ descendant ->
            val modifiers = getModifiers(descendant.getChild("MethodHeader"))
            "abstract" !in modifiers && "static" !in modifiers
        })

        val isClassAbstract = "abstract" in getModifiers(node)

        if (!isClassAbstract) {
            allImplementedInterfaces.forEach({ implementedInterface ->
                val interfaceName = getDeclarationName(implementedInterface)
                val abstractMethodDeclarations = getInterfaceInheritedMethods(implementedInterface, className, mutableSetOf())
                ensureThatAbstractMethodsHaveImplementations(className, interfaceName, abstractMethodDeclarations, allNonAbstractInstanceMethodDeclarations)
            })
        }

        /**
         * TEST
         *
         * For every abstract method in this class's hierarchy, there exists a concrete implementation
         * in this class or its parents.
         */
        val allAbstractMethodDeclarations = allMethodDeclarations.filter({ descendant ->
            "abstract" in getModifiers(descendant.getChild("MethodHeader"))
        })

        if (!isClassAbstract) {
            ensureThatAbstractMethodsHaveImplementations(className, "SOME ABSTRACT SUPER CLASS OF \"${className}\"", allAbstractMethodDeclarations, allNonAbstractInstanceMethodDeclarations)
        }

        /**
         * TEST
         *
         * If this class is abstract class:
         *  For every abstract method in this class, there should not exist a concrete implementation in this class's
         *  parents.
         */
        val allInheritedMethodDeclarations = allMethodDeclarations.filter({ descendant ->
            ownMethodDeclarations.filter({ ownMethodDeclaration ->
                ownMethodDeclaration !== descendant
            }).isEmpty()
        })

        if (isClassAbstract) {
            val ownAbstractMethodDeclarations = ownMethodDeclarations.filter({ "abstract" in getModifiers(it)})
            ownAbstractMethodDeclarations.forEach({ ownAbstractMethodDeclaration ->
                val ownAbstractMethodHeader = ownAbstractMethodDeclaration.getChild("MethodHeader")

                allInheritedMethodDeclarations.forEach(fun(inheritedMethodDeclaration: CSTNode) {
                    val inheritedMethodHeader = inheritedMethodDeclaration.getChild("MethodHeader")

                    if (ownAbstractMethodDeclaration === inheritedMethodDeclaration) {
                        return
                    }

                    if (canMethodHeadersReplaceOneAnother(inheritedMethodHeader, ownAbstractMethodHeader)) {
                        val inheritedMethodName = getDeclarationName(inheritedMethodDeclaration)
                        throw AbstractMethodOverridesConcreteImplementation(className, inheritedMethodName)
                    }
                })
            })
        }


        /**
         * TEST
         *
         * A non-static method must not replace a static method.
         */
        val allInheritedMethodAndAbstractDeclarations = allMethodAndAbstractDeclarations.filter({ declaration ->
            ownMethodDeclarations.filter({ it === declaration }).isEmpty()
        })

        fun ownMethodsMustNotReplaceInheritedMethods(ownPredicate: (CSTNode) -> Boolean, allPredicate: (CSTNode) -> Boolean) {
            val ownFilteredMethodDeclarations = ownMethodDeclarations.filter(ownPredicate)
            val allFilteredInheritedMethodDeclarations = allInheritedMethodAndAbstractDeclarations.filter(allPredicate)

            ownFilteredMethodDeclarations.forEach({ ownFilteredMethodDeclaration ->
                val ownFilteredMethodHeader = ownFilteredMethodDeclaration.getChild("MethodHeader")
                val ownFilteredMethodName = getDeclarationName(ownFilteredMethodDeclaration)

                allFilteredInheritedMethodDeclarations.forEach({ filteredInheritedMethodDeclaration ->
                    val filteredInheritedMethodHeader = filteredInheritedMethodDeclaration.getChild("MethodHeader")

                    if (canMethodHeadersReplaceOneAnother(ownFilteredMethodHeader, filteredInheritedMethodHeader)) {
                        throw IllegalMethodReplacement(className, ownFilteredMethodName)
                    }
                })
            })
        }

        ownMethodsMustNotReplaceInheritedMethods({ "static" !in getModifiers(it)}, { "static" in getModifiers(it)})

        /**
         * TEST
         *
         * A static method must not replace a non-static method.
         */
        ownMethodsMustNotReplaceInheritedMethods({ "static" in getModifiers(it) }, { "static" !in getModifiers(it) })

        /**
         * TEST
         *
         * A protected method must not replace a public method
         */
        ownMethodsMustNotReplaceInheritedMethods({ "protected" in getModifiers(it)}, { "public" in getModifiers(it) })

        /**
         * TEST
         *
         * A method must not replace a final method.
         */
        ownMethodsMustNotReplaceInheritedMethods({ true }, { "final" in getModifiers(it) })


        /**
         *
         *
         * NAME RESOLUTION BELOW HERE
         *
         *
         */


        val allFieldDeclarations = getClassInheritedDescendants(node, { it.name == "FieldDeclaration" })

        /**
         * 1. Do name resolution on current field if it's an own field.
         * 2. Add this field to the environment.
         */
        val ownFieldDeclarations = classBody.getDescendants("FieldDeclaration")
        val isOwnFieldDeclaration = { otherFieldDeclaration: CSTNode ->
            ownFieldDeclarations.filter({ it === otherFieldDeclaration }).isNotEmpty()
        }

        val resolveAndInsertFieldDeclarationIntoEnvironment = { fieldDeclaration: CSTNode ->
            if (isOwnFieldDeclaration(fieldDeclaration)) {
                this.visit(fieldDeclaration)
            }

            val fieldDeclarationName = getDeclarationName(fieldDeclaration)
            if (isOwnFieldDeclaration(fieldDeclaration)) {
                if (environment.contains({ it.name == fieldDeclarationName && it.node.name == "FieldDeclaration" && isOwnFieldDeclaration(it.node) })) {
                    throw NameResolutionError("Tried to declare field \"$fieldDeclarationName\" twice in the same class.")
                }
            }

            environment.push(fieldDeclarationName, fieldDeclaration)
        }

        /**
         * Static fields.
         */
        val allStaticFieldDeclarations = allFieldDeclarations.filter({ "static" in getModifiers(it) })
        allStaticFieldDeclarations.forEach(resolveAndInsertFieldDeclarationIntoEnvironment)

        /**
         * Static method names.
         */
        val addMethodToEnvironment = { methodDeclaration: CSTNode ->
            val methodName = getDeclarationName(methodDeclaration)
            environment.push(methodName, methodDeclaration)
        }

        val allStaticMethodDeclarations = allMethodDeclarations.filter({ "static" in getModifiers(it) })
        allStaticMethodDeclarations.forEach(addMethodToEnvironment)

        /**
         * Static methods.
         */
        val resolveMethodBody = fun (methodDeclaration: CSTNode) {
            environment.pushScope()
            this.visit(methodDeclaration)
            environment.popScope()
        }

        val ownStaticMethodDeclarations = allStaticMethodDeclarations.filter({ otherDeclaration ->
            ownMethodDeclarations.filter({ it === otherDeclaration }).isNotEmpty()
        })
        ownStaticMethodDeclarations.forEach(resolveMethodBody)

        /**
         * Instance fields.
         */
        val allInstanceFieldDeclarations = allFieldDeclarations.filter({ "static" !in getModifiers(it) })
        allInstanceFieldDeclarations.forEach(resolveAndInsertFieldDeclarationIntoEnvironment)

        /**
         * Instance method names.
         */
        val allInstanceMethodDeclarations = allMethodDeclarations.filter({ "static" !in getModifiers(it) })
        allInstanceMethodDeclarations.forEach(addMethodToEnvironment)

        /**
         * Instance methods.
         */
        val ownInstanceMethodDeclarations = allStaticMethodDeclarations.filter({ otherDeclaration ->
            ownMethodDeclarations.filter({ it === otherDeclaration }).isNotEmpty()
        })
        ownInstanceMethodDeclarations.forEach(resolveMethodBody)

        /**
         * Constructors
         */
        constructorDeclarations.forEach(resolveMethodBody)
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

        if (resolutionDepth == ResolutionDepth.TypeDeclaration) {
            return
        }

        val interfaceBody = node.getChild("InterfaceBody")
        val abstractMethodDeclarations = interfaceBody.getDescendants("AbstractMethodDeclaration")

        abstractMethodDeclarations.forEach({ abstractMethodDeclaration ->
            val methodHeader = abstractMethodDeclaration.getChild("MethodHeader")
            environment.pushScope()
            this.visit(methodHeader)
            environment.popScope()
        })

        if (resolutionDepth == ResolutionDepth.MethodDeclaration) {
            return
        }

        /**
         * TEST:
         *
         * An interface can not be repeated in an extends clause of an interface.
         */
        val allExtendedInterfaces = node.getChild("ExtendsInterfacesOpt").getDescendants("Name").map({ it.getDeclaration() })
        val interfaceName = getDeclarationName(node)

        for (i in IntRange(0, allExtendedInterfaces.size - 1)) {
            for (j in IntRange(0, allExtendedInterfaces.size - 1)) {
                if (i == j) {
                    continue
                }

                if (allExtendedInterfaces[i] === allExtendedInterfaces[j]) {
                    val duplicateInterfaceName = getDeclarationName(allExtendedInterfaces[i])
                    throw InterfaceExtendsAnotherMoreThanOnce(interfaceName, duplicateInterfaceName)
                }
            }
        }

        /**
         * TEST:
         *
         * An interface can only extend an interface.
         */
        allExtendedInterfaces.forEach({ extendedInterface ->
            if (extendedInterface.name != "InterfaceDeclaration") {
                throw InterfaceExtendsNonInterface(interfaceName, getDeclarationName(extendedInterface))
            }
        })

        /**
         * TEST:
         *
         * The interface hierarchy must be acyclic.
         */
        val allMethodDeclarations = getInterfaceInheritedMethods(node, interfaceName, mutableSetOf())

        /**
         * TEST:
         *
         * An interface must not declare two methods with the same signature.
         */
        abstractMethodDeclarations.forEach({ abstractMethodDeclaration ->
            abstractMethodDeclarations.forEach(fun (otherAbstractMethodDeclaration: CSTNode) {
                if (abstractMethodDeclaration === otherAbstractMethodDeclaration) {
                    return
                }

                val abstractMethodHeader = abstractMethodDeclaration.getChild("MethodHeader")
                val otherAbstractMethodHeader = otherAbstractMethodDeclaration.getChild("MethodHeader")

                if (canMethodHeadersReplaceOneAnother(abstractMethodHeader, otherAbstractMethodHeader)) {
                    val abstractMethodDeclarationName = getDeclarationName(abstractMethodDeclaration)
                    throw DuplicateMethodsDetectedInInterface(interfaceName, abstractMethodDeclarationName)
                }
            })
        })

        /**
         * TEST:
         *
         * For any two methods contained within an interface's hierarchy, if they have the same signature,
         * they must have the same return type.
         */
        allMethodDeclarations.forEach({ methodDeclaration ->
            allMethodDeclarations.forEach(fun (otherMethodDeclaration: CSTNode) {
                if (methodDeclaration === otherMethodDeclaration) {
                    return
                }

                val methodHeader = methodDeclaration.getChild("MethodHeader");
                val otherMethodHeader = otherMethodDeclaration.getChild("MethodHeader")
                val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                val otherMethodDeclarator = otherMethodHeader.getChild("MethodDeclarator")

                if (areMethodSignaturesTheSame(methodDeclarator, otherMethodDeclarator)) {
                    if (!areMethodReturnTypesTheSame(methodHeader, otherMethodHeader)) {
                        throw TwoMethodsInInterfaceHierarchyWithSameSignatureButDifferentReturnTypes(interfaceName, getDeclarationName(methodDeclaration))
                    }
                }
            })
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

class EitherPackageNameOrQualifiedType(canonicalName: String)
    : Exception("Qualified identifier \"$canonicalName\" is both package name and a canonical type name.")

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
                    if (pkgName.startsWith(packageName + ".") || pkgName == packageName) {
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

            packages.forEach({(packageName, _) ->
                getTypesDeclaredInPackage(packageName).forEach({(typeName, _) ->
                    if (doesPackageExist("$packageName.$typeName")) {
                        throw EitherPackageNameOrQualifiedType("$packageName.$typeName")
                    }
                })
            })

            val visitPackages = { resolutionDepth: NameResolutionVisitor.ResolutionDepth ->
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

                                if (!doesPackageExist(stiPackageName)) {
                                    throw SingleTypeImportDeclarationDetectedForNonExistentPackage(stiPackageName)
                                }

                                val typesInPackage = getTypesDeclaredInPackage(stiPackageName)

                                if (!typesInPackage.contains(typeName)) {
                                    throw SingleTypeImportDeclarationDetectedForNonExistentType(stiPackageName, typeName)
                                }

                                globalEnvironment.push(typeName, typesInPackage[typeName]!!)
                            })

                        visitor.setResolutionDepth(resolutionDepth)
                        visitor.visit(compilationUnit)

                        globalEnvironment.popScope()
                        visitor.detachImportOnDemandEnvironment()
                    })
                })
            }

            visitPackages(NameResolutionVisitor.ResolutionDepth.TypeDeclaration)
            visitPackages(NameResolutionVisitor.ResolutionDepth.MethodDeclaration)
            visitPackages(NameResolutionVisitor.ResolutionDepth.All)

            return visitor.getNameResolution()
        }
    }
}
package oj.nameresolver

import oj.models.*
import java.util.*

open class NameResolutionError(reason: String) : Exception(reason)
open class TypeCheckingError(reason: String): Exception(reason)
open class HierarchyCheckingError(reason: String) : NameResolutionError(reason)
open class UnimplementedAbstractOrInterfaceMethodException(className: String) : HierarchyCheckingError("Non-abstract class \"$className\" has not implemented all interface or abstract methods.")
open class ClassExtendsNonClass(className: String, interfaceName: String) : HierarchyCheckingError("Class \"$className\" extends \"$interfaceName\"")
open class ClassImplementsNonInterface(className: String, nonInterfaceName: String) : HierarchyCheckingError("Class \"$className\" implements \"$nonInterfaceName\"")
open class ClassImplementsAnInterfaceMoreThanOnce(className: String, interfaceName: String): HierarchyCheckingError("Class \"$className\" implements interface \"$interfaceName\" more than once.")
open class ClassExtendsFinalSuperClass(className: String, finalClassName: String): HierarchyCheckingError("Class \"$className\" extends \"$finalClassName\"")
open class ClassHierarchyIsCyclic(className: String, firstDuplicatedClassName: String): HierarchyCheckingError("Detected a duplicate class \"$firstDuplicatedClassName\" in class \"$className\"'s hierarchy.")
open class DuplicateMethodsDetectedInClass(className: String, methodName: String): HierarchyCheckingError("Method \"$methodName\" is duplicated within class \"$className\"")
open class DuplicateConstructorsDetectedInClass(className: String): HierarchyCheckingError("Constructor is duplicated within class \"$className\"")
open class TwoMethodsInClassHierarchyWithSameSignatureButDifferentReturnTypes(className: String, methodName: String): HierarchyCheckingError("Two methods with the same name, \"$methodName\", but different return type exist in the class hierarchy (including interface methods) of class \"$className\"")
open class IllegalMethodReplacement(typeName: String, methodName: String): HierarchyCheckingError("A method \"$methodName\" in type \"$typeName\" illegally overrides a method it inherits")
open class InterfaceExtendsAnotherMoreThanOnce(interfaceName: String, duplicateInterfaceName: String): HierarchyCheckingError("Interface \"$interfaceName\" extends interface \"$duplicateInterfaceName\" more than once.")
open class InterfaceExtendsNonInterface(interfaceName: String, nonInterfaceName: String) : HierarchyCheckingError("Interface \"$interfaceName\" extends a non-interface \"$nonInterfaceName\"")
open class DuplicateMethodsDetectedInInterface(interfaceName: String, methodName: String): HierarchyCheckingError("Detected a duplicate method \"$methodName\" in interface \"$interfaceName\"")
open class TwoMethodsInInterfaceHierarchyWithSameSignatureButDifferentReturnTypes(interfaceName: String, methodName: String): HierarchyCheckingError("Two methods with the same name, \"$methodName\", but different return type exist in the interface hierarchy (including interface methods) of interface \"$interfaceName\"")
open class ConcreteMethodImplementsPublicMethodWithProtectedVisibility(className: String, methodName: String) : HierarchyCheckingError("Concrete method \"$className.$methodName\" has \"protected\" visibility, but it implements a \"public\" method.")
open class TriedToAccessStaticMemberOnNonClassDeclaration(nodeType: String, declarationName: String) : NameResolutionError("Tried to access static field or method on type \"$nodeType\" node called \"$declarationName\". Statics are only supported on classes.")
open class TriedToAccessFieldOfPrimitiveType(expressionName: String, expressionType: String) : NameResolutionError("Tried to access field of \"$expressionName\", which is of primitive type \"$expressionType\"")

val PRIMITIVE_TYPE_NAMES = setOf("byte", "short", "int", "char", "boolean")

class NameResolutionVisitor(
    private val environment: Environment,
    private val packageManager: PackageManager
) : CSTNodeVisitor() {

    enum class ResolutionDepth {
        TypeDeclaration, MemberDeclaration, All
    }

    private var resolutionDepth = ResolutionDepth.All
    private val nameResolution = mutableMapOf<CSTNode, CSTNode>()
    private var importOnDemandDeclarationEnvironment: Environment = Environment()
    private var currentFieldDeclaration: CSTNode? = null
    private var currentMethodDeclaration: CSTNode? = null
    private var leftHandSideStack = LinkedList<CSTNode>()
    private var currentLocalVariableDeclaration: CSTNode? = null
    private var isCurrentLocalVariableDeclarationInitialized: Boolean = false

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
        nameNode.setDeclaration(declaration)
    }

    fun findTypeDeclarationByName(typeName: String): CSTNode? {
        val globalEnvironmentLookup = environment.findAll({(name, node) -> name == typeName && isClassOrInterfaceDeclaration(node)})
        if (globalEnvironmentLookup.isNotEmpty()) {
            return globalEnvironmentLookup.first()
        }

        val iodLookup = importOnDemandDeclarationEnvironment.findAll({(name, node) -> name == typeName && isClassOrInterfaceDeclaration(node)})
        if (iodLookup.size == 1) {
            return iodLookup.first()
        } else if (iodLookup.size > 1) {
            throw NameResolutionError("Detected more than one types with name $typeName inside import on demand declaration")
        }

        return null
    }

    fun resolveTypeName(nameNode: CSTNode) {
        nameNode.setNameNodeClassification(NameNodeClassification.Type)

        when (nameNode.children[0].name) {
            "SimpleName" -> {
                val simpleName = nameNode.getChild("SimpleName")
                val typeName = simpleName.getChild("IDENTIFIER").lexeme
                val typeDeclaration = findTypeDeclarationByName(typeName)

                if (typeDeclaration == null) {
                    throw NameResolutionError("TypeName \"$typeName\" doesn't exist.")
                }

                addNameResolution(nameNode, typeDeclaration)
                nameNode.setType(Type(typeDeclaration, false))
            }

            else -> {
                val qualifiedName = nameNode.getChild("QualifiedName")
                val typeName = qualifiedName.getChild("IDENTIFIER").lexeme
                val packageName = serializeName(qualifiedName.getChild("Name"))
                val packageNameIdentifiers = packageName.split(".")

                if (!packageManager.doesPackageExist(packageName)) {
                    throw NameResolutionError(
                        "Tried to access type \"$typeName\" in non-existent package \"$packageName\"."
                    )
                }

                /**
                 * Ensure that no strict prefix of a qualified type is a package that contains types.
                 *
                 * If foo.bar.baz.A a = new foo.bar.baz.A() then:
                 *  - foo can't have types
                 *  - foo.bar can't have types
                 *  - foo.bar.baz can have types (ex: foo.bar.baz.A)
                 */

                for (i in IntRange(1, packageNameIdentifiers.size - 1)) {
                    val prefix = packageNameIdentifiers.subList(0, i)
                    val prefixPackageName = prefix.joinToString(".")

                    if (
                        packageManager.doesPackageExist(prefixPackageName) &&
                        packageManager.getTypesDeclaredInPackage(prefixPackageName).isNotEmpty()
                    ) {
                        throw NameResolutionError(
                            "Prefix \"$prefixPackageName\" of a package \"$packageName\" used to resolve type \"$typeName\" contained a type."
                        )
                    }
                }

                /**
                 * Ensure that no strict prefix of a qualified type is a type itself.
                 *
                 * If foo.bar.baz.A a = new foo.bar.baz.A() then:
                 *  - foo can't be a type
                 *  - foo.bar can't be a type
                 *  - foo.bar.baz can't be a type
                 */
                for (i in IntRange(0, packageNameIdentifiers.size - 1)) {
                    val prefix = packageNameIdentifiers.subList(0, i + 1)

                    if (prefix.size == 1) {
                        val typeDeclaration = findTypeDeclarationByName(prefix[0])

                        if (typeDeclaration != null) {
                            val serializedQualifiedName = serializeName(nameNode)
                            throw NameResolutionError(
                                "Prefix \"${prefix[0]}\" of qualified type \"$serializedQualifiedName\" is a type"
                            )
                        }
                    } else {
                        val prefixPrefixPackage = prefix.subList(0, prefix.size - 1)
                        val prefixPrefixPackageName = prefixPrefixPackage.joinToString(".")
                        val prefixType = prefix.last()

                        if (
                            packageManager.doesPackageExist(prefixPrefixPackageName) &&
                            packageManager.getTypesDeclaredInPackage(prefixPrefixPackageName).contains(prefixType)
                        ) {
                            val serializedPrefix = prefix.joinToString(".")
                            val serializedQualifiedName = serializeName(qualifiedName)
                            throw NameResolutionError(
                                "Prefix \"$serializedPrefix\" of qualified type \"$serializedQualifiedName\" is a type."
                            )
                        }
                    }
                }

                val typesInPackage = packageManager.getTypesDeclaredInPackage(packageName)
                if (!typesInPackage.containsKey(typeName)) {
                    throw NameResolutionError(
                        "Tried to access non-existent type \"$typeName\" in package \"$packageName\"."
                    )
                }

                addNameResolution(nameNode, typesInPackage[typeName]!!)
                nameNode.setType(Type(typesInPackage[typeName]!!, false))
            }
        }
    }

    fun validateUsageOfFieldDeclaration(fieldDeclaration: CSTNode) {
        if (getCurrentMethodDeclaration() != null) {
            val currentMethodDeclaration = getCurrentMethodDeclaration()!!

            val isMethodStatic = "static" in getModifiers(currentMethodDeclaration)
            val isFieldStatic =  "static" in getModifiers(fieldDeclaration)

            if (isMethodStatic && !isFieldStatic) {
                val fieldName = getDeclarationName(fieldDeclaration)
                val methodName = getDeclarationName(currentMethodDeclaration)
                throw NameResolutionError("Tried to access an instance field \"$fieldName\" in a static method \"$methodName\"")
            }
        }

        if (getCurrentFieldDeclaration() != null) {
            val currentFieldDeclaration = getCurrentFieldDeclaration()!!
            val fieldBeingUsedInDeclaration = fieldDeclaration

            val isCurrentFieldDeclarationStatic = "static" in getModifiers(currentFieldDeclaration)
            val isFieldBeingUsedInDeclarationStatic = "static" in getModifiers(fieldBeingUsedInDeclaration)

            if (isCurrentFieldDeclarationStatic && !isFieldBeingUsedInDeclarationStatic) {
                val currentFieldDeclarationName = getDeclarationName(currentFieldDeclaration)
                val fieldBeingUsedInDeclarationName = getDeclarationName(fieldBeingUsedInDeclaration)

                throw NameResolutionError("Tried to use an instance field \"$fieldBeingUsedInDeclarationName\" in a static field declaration \"$currentFieldDeclarationName\"")
            }

            var shouldCheckForwardReference = true

            if (getCurrentLeftHandSide() != null) {
                val currentLeftHandSide = getCurrentLeftHandSide()!!

                if (currentLeftHandSide.children[0].name == "Name") {
                    val nameNode = currentLeftHandSide.getChild("Name")
                    val identifiers = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme })

                    if (identifiers.size == 1) {
                        if (currentFieldDeclaration.name == "FieldDeclaration") {
                            shouldCheckForwardReference = false
                        }
                    }
                }
            }

            if (shouldCheckForwardReference && isCurrentFieldDeclarationStatic == isFieldBeingUsedInDeclarationStatic) {
                val currentClassDeclaration = getCurrentClassDeclaration()

                val allFieldDeclarationsInClassDeclaration = currentClassDeclaration.getDescendants("FieldDeclaration")

                val currentFieldDeclarationIndex = allFieldDeclarationsInClassDeclaration.indexOf(currentFieldDeclaration)
                val fieldBeingUsedInDeclarationIndex = allFieldDeclarationsInClassDeclaration.indexOf(fieldBeingUsedInDeclaration)

                if (currentFieldDeclarationIndex <= fieldBeingUsedInDeclarationIndex) {
                    val currentFieldDeclarationName = getDeclarationName(currentFieldDeclaration)
                    val fieldBeingUsedInDeclarationName = getDeclarationName(fieldBeingUsedInDeclaration)

                    throw NameResolutionError("Illegal forward reference. Field \"$fieldBeingUsedInDeclarationName\" is declared after it's used in field declaration of \"$currentFieldDeclarationName\"")
                }
            }
        }
    }

    fun validateUsageOfLocalVariableDeclaration(localVariableDeclaration: CSTNode) {
        if (getCurrentLocalVariableDeclaration() != null) {
            val currentLocalVariableDeclaration = getCurrentLocalVariableDeclaration()!!
            val currentLocalVariableDeclarationName = getDeclarationName(currentLocalVariableDeclaration)

            if (getCurrentLeftHandSide() != null) {
                val currentLeftHandSide = getCurrentLeftHandSide()!!

                if (currentLeftHandSide.children[0].name == "Name") {
                    val nameNode = currentLeftHandSide.getChild("Name")
                    val identifiers = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme })

                    if (identifiers.size == 1) {
                        if (identifiers[0] == currentLocalVariableDeclarationName) {
                            isCurrentLocalVariableDeclarationInitialized = true
                        }
                    }
                }
            }

            if (!isCurrentLocalVariableDeclarationInitialized && getDeclarationName(localVariableDeclaration) == currentLocalVariableDeclarationName) {
                throw NameResolutionError("Local variable \"${currentLocalVariableDeclarationName}\" used to initialize itself")
            }

        }
    }

    fun resolveExpressionName(nameNode: CSTNode) {
        nameNode.setNameNodeClassification(NameNodeClassification.Expression)

        when (nameNode.children[0].name) {
            "SimpleName" -> {
                val simpleName = nameNode.getChild("SimpleName")
                val identifier = simpleName.getChild("IDENTIFIER").lexeme

                val declarationNode = environment.find(identifier)
                addNameResolution(nameNode, declarationNode)

                if (declarationNode.name == "FieldDeclaration") {
                    validateUsageOfFieldDeclaration(declarationNode)
                }

                if (declarationNode.name == "LocalVariableDeclaration") {
                    validateUsageOfLocalVariableDeclaration(declarationNode)
                }

                nameNode.setType(getDeclarationType(declarationNode))
            }

            else -> {
                val qualifiedName = nameNode.getChild("QualifiedName")
                val ambiguousPrefixName = qualifiedName.getChild("Name")
                val identifier = qualifiedName.getChild("IDENTIFIER").lexeme
                resolveAmbiguousName(ambiguousPrefixName)

                when (ambiguousPrefixName.getNameNodeClassification()) {
                    NameNodeClassification.Package -> {
                        val serializedFullName = serializeName(qualifiedName)
                        val packageName = serializeName(ambiguousPrefixName)
                        throw NameResolutionError(
                            "In ExpressionName \"$serializedFullName\", ambiguous prefix \"$packageName\" resolved to a package, which is illegal."
                        )
                    }

                    NameNodeClassification.Type -> {
                        val classDeclaration = ambiguousPrefixName.getDeclaration()
                        if (classDeclaration.name != "ClassDeclaration") {
                            throw TriedToAccessStaticMemberOnNonClassDeclaration(classDeclaration.name, getDeclarationName(classDeclaration))
                        }

                        val fields = packageManager.getAccessibleStaticMembers(getCurrentClassDeclaration(), classDeclaration)
                            .filter({ it.name == "FieldDeclaration" && getDeclarationName(it) == identifier })

                        if (fields.isEmpty()) {
                            val qualifiedTypeName = serializeName(qualifiedName)
                            throw NameResolutionError(
                                "Prefix \"$qualifiedTypeName\" is TypeName, but static field \"$identifier\" isn't accessible in type \"$qualifiedTypeName\"."
                            )
                        }

                        val fieldDeclaration = fields[0]
                        if ("static" !in getModifiers(fieldDeclaration)) {
                            val fieldDeclarationName = getDeclarationName(fieldDeclaration)
                            throw NameResolutionError("Tried to access non-static field \"$fieldDeclarationName\" from a static context.")
                        }

                        addNameResolution(nameNode, fieldDeclaration)
                        nameNode.setType(getDeclarationType(fieldDeclaration))
                    }

                    NameNodeClassification.Expression -> {
                        val typeOfAmbiguousPrefix = ambiguousPrefixName.getType()

                        if (typeOfAmbiguousPrefix.isArray) {
                            if (identifier != "length") {
                                val qualifiedArrayName = serializeName(ambiguousPrefixName)
                                // Only the length field is defined on arrays.
                                throw NameResolutionError(
                                    "Tried to access field \"$identifier\" on the array \"$qualifiedArrayName\"."
                                )
                            }

                            if (isLValue(nameNode)) {
                                val serializedFullName = serializeName(qualifiedName)
                                throw TypeCheckingError("Tried to assign to $serializedFullName, which is final.")
                            }

                            val arrayLengthType = Type(CSTNode("int"), false)
                            nameNode.setType(arrayLengthType)
                        } else {
                            val ambiguousPrefixTypeDeclaration = typeOfAmbiguousPrefix.type

                            if (ambiguousPrefixTypeDeclaration.name in PRIMITIVE_TYPE_NAMES) {
                                val serializedAmbiguousPrefixName = serializeName(ambiguousPrefixName)
                                throw TriedToAccessFieldOfPrimitiveType(serializedAmbiguousPrefixName, ambiguousPrefixTypeDeclaration.name)
                            }

                            val currentClass = getCurrentClassDeclaration()
                            val accessibleInstanceFieldDeclarations = packageManager.getAccessibleInstanceMembers(currentClass, ambiguousPrefixTypeDeclaration)
                                .filter({ it.name == "FieldDeclaration" && getDeclarationName(it) == identifier })

                            if (accessibleInstanceFieldDeclarations.isEmpty()) {
                                val expressionTypeName = getDeclarationName(ambiguousPrefixTypeDeclaration)
                                throw NameResolutionError("Prefix \"$ambiguousPrefixTypeDeclaration\" is ExpressionName which refers to type \"$expressionTypeName\", but failed to find instance field \"$identifier\" said type.")
                            }

                            val fieldDeclarationNode = accessibleInstanceFieldDeclarations.first()
                            addNameResolution(nameNode, fieldDeclarationNode)
                            nameNode.setType(getDeclarationType(fieldDeclarationNode))
                        }
                    }

                    NameNodeClassification.Method -> {
                        val serializedAmbiguousPrefix = serializeName(ambiguousPrefixName)
                        throw TypeCheckingError("Ambiguous name \"$serializedAmbiguousPrefix\" illegally resolved to Method.")
                    }
                }
            }
        }
    }

    fun resolveAmbiguousName(nameNode: CSTNode) {
        when (nameNode.children[0].name) {
            "SimpleName" -> {
                val simpleName = nameNode.getChild("SimpleName")
                val identifier = simpleName.getChild("IDENTIFIER").lexeme
                val declarationTypes = setOf("LocalVariableDeclaration", "FormalParameter", "FieldDeclaration")

                val declarationLookup = environment.findAll({ (name, node) -> name == identifier && node.name in declarationTypes })
                if (declarationLookup.isNotEmpty()) {
                    val declaration = declarationLookup[0]
                    if (declaration.name == "FieldDeclaration") {
                        validateUsageOfFieldDeclaration(declaration)
                    }

                    addNameResolution(nameNode, declaration)

                    val type = getDeclarationType(declaration)
                    nameNode.setType(type)

                    nameNode.setNameNodeClassification(NameNodeClassification.Expression)
                    return
                }

                val typeDeclaration = findTypeDeclarationByName(identifier)
                if (typeDeclaration != null) {
                    val type = Type(typeDeclaration, false)
                    nameNode.setType(type)
                    addNameResolution(nameNode, typeDeclaration)

                    nameNode.setNameNodeClassification(NameNodeClassification.Type)
                    return
                }

                nameNode.setNameNodeClassification(NameNodeClassification.Package)
                return
            }

            else -> {
                val qualifiedName = nameNode.getChild("QualifiedName")
                val ambiguousPrefixName = qualifiedName.getChild("Name")

                resolveAmbiguousName(ambiguousPrefixName)
                val identifier = qualifiedName.getChild("IDENTIFIER").lexeme

                when (ambiguousPrefixName.getNameNodeClassification()) {
                    NameNodeClassification.Package -> {
                        val packageName = serializeName(ambiguousPrefixName)

                        if (
                            packageManager.doesPackageExist(packageName) &&
                            packageManager.getTypesDeclaredInPackage(packageName).contains(identifier)
                        ) {
                            val typeDeclaration = packageManager.getTypesDeclaredInPackage(packageName)[identifier]!!
                            addNameResolution(nameNode, typeDeclaration)

                            val type = Type(typeDeclaration, false)
                            nameNode.setType(type)

                            nameNode.setNameNodeClassification(NameNodeClassification.Type)
                            return
                        }

                        nameNode.setNameNodeClassification(NameNodeClassification.Package)
                        return
                    }

                    NameNodeClassification.Type -> {
                        val classDeclaration = ambiguousPrefixName.getDeclaration()

                        if (classDeclaration.name != "ClassDeclaration") {
                            throw TriedToAccessStaticMemberOnNonClassDeclaration(classDeclaration.name, getDeclarationName(classDeclaration))
                        }

                        // TODO: Does MethodDeclaration below make sense?
                        val isStaticMemberDeclaration = { node: CSTNode ->
                            (node.name == "FieldDeclaration" || node.name == "MethodDeclaration") && "static" in getModifiers(node)
                        }

                        val staticMemberDeclarations = packageManager.getClassInheritedDescendants(classDeclaration, isStaticMemberDeclaration)

                        val matchingStaticMemberDeclarations = staticMemberDeclarations.filter({ getDeclarationName(it) == identifier })
                        if (matchingStaticMemberDeclarations.isNotEmpty()) {
                            val matchingStaticMemberDeclaration = matchingStaticMemberDeclarations.first()
                            addNameResolution(nameNode, matchingStaticMemberDeclaration)

                            val type = getDeclarationType(matchingStaticMemberDeclaration)
                            nameNode.setType(type)

                            nameNode.setNameNodeClassification(NameNodeClassification.Expression)
                            return
                        }

                        val qualifiedClassName = serializeName(ambiguousPrefixName)
                        throw NameResolutionError("Prefix \"$qualifiedClassName\" is TypeName, but member \"$identifier\" doesn't exist in the type.")
                    }

                    NameNodeClassification.Expression -> {
                        val expressionType = ambiguousPrefixName.getType()
                        val currentClass = getCurrentClassDeclaration()

                        if (expressionType.isArray) {
                            val qualifiedAmbiguousPrefixName = serializeName(ambiguousPrefixName)
                            throw NameResolutionError("Ambiguous names cannot resolve to arrays, but \"$qualifiedAmbiguousPrefixName\" did.")
                        } else {
                            val expressionTypeDeclaration = expressionType.type

                            if (expressionTypeDeclaration.name in PRIMITIVE_TYPE_NAMES) {
                                throw NameResolutionError("Tried to access member \"$identifier\" of a primitive type \"${expressionTypeDeclaration.name}\"")
                            }

                            val fieldDeclarations = packageManager.getAccessibleInstanceMembers(currentClass, expressionTypeDeclaration)
                                .filter({ it.name == "FieldDeclaration" })

                            val matchingFieldDeclarations = fieldDeclarations.filter({ getDeclarationName(it) == identifier })
                            if (matchingFieldDeclarations.isNotEmpty()) {
                                val matchingFieldDeclaration = matchingFieldDeclarations.first()
                                addNameResolution(nameNode, matchingFieldDeclaration)

                                val type = getDeclarationType(matchingFieldDeclaration)
                                nameNode.setType(type)

                                nameNode.setNameNodeClassification(NameNodeClassification.Expression)
                                return
                            }

                            val expressionTypeName = getDeclarationName(expressionTypeDeclaration)
                            val qualifiedAmbiguousPrefixName = serializeName(ambiguousPrefixName)
                            throw NameResolutionError(
                                "Prefix \"$qualifiedAmbiguousPrefixName\" is an ExpressionName that refers to " +
                                    "type \"$expressionTypeName\", but failed to find instance method or field " +
                                    "\"$identifier\" of said type."
                            )
                        }
                    }

                    NameNodeClassification.Method -> {
                        val serializedAmbiguousPrefix = serializeName(ambiguousPrefixName)
                        throw TypeCheckingError("Ambiguous name \"$serializedAmbiguousPrefix\" illegally resolved to Method.")
                    }
                }
            }
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
            environment.withNewScope({
                this.visit(methodHeader)
            })
        })

        val ownFieldDeclarations = classBody.getDescendants("FieldDeclaration")

        ownFieldDeclarations.forEach({ fieldDeclaration ->
            val type = fieldDeclaration.getChild("Type")
            environment.withNewScope({
                this.visit(type)
            })
        })

        val constructorDeclarations = node.getDescendants("ConstructorDeclaration")

        constructorDeclarations.forEach({ constructorDeclaration ->
            val constructorDeclarator = constructorDeclaration.getChild("ConstructorDeclarator")
            environment.withNewScope({
                this.visit(constructorDeclarator)
            })
        })

        if (resolutionDepth == ResolutionDepth.MemberDeclaration) {
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

                if (canMethodsReplaceOneAnother(methodHeader1, methodHeader2)) {
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
         * a different return type.
         */
        val allMethodDeclarations = packageManager.getClassInheritedDescendants(node, { it.name == "MethodDeclaration" })
        val allInterfaceMethodDeclarations = allImplementedInterfaces.flatMap({ packageManager.getInterfaceInheritedMethods(it, className, mutableSetOf()) })
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
         * If a class hasn't implemented implemented all its interface methods, then it must be abstract.
         * A class that contains any abstract methods must be abstract.
         */

        fun areInterfaceMethodsImplemented(classDeclaration: CSTNode, descendantConcreteMethods: List<CSTNode>, descendantUnimplementedInterfaceMethods: List<CSTNode>): Boolean {
            val implementedInterfaces = classDeclaration.getChild("InterfacesOpt").getDescendants("Name").map({ it.getDeclaration() })
            val allInterfaceInheritedMethods = implementedInterfaces.flatMap({ packageManager.getInterfaceInheritedMethods(it, getDeclarationName(classDeclaration), mutableSetOf()).toSet() })
            val potentiallyUnimplementedInterfaceMethods = allInterfaceInheritedMethods + descendantUnimplementedInterfaceMethods

            val abstractMethods = classDeclaration.getDescendants("MethodDeclaration").filter({ "abstract" in getModifiers(it) })
            val ownConcreteMethods = classDeclaration.getDescendants("MethodDeclaration").filter({ "static" !in getModifiers(it) && "abstract" !in getModifiers(it) })
            val concreteMethods = ownConcreteMethods + descendantConcreteMethods

            val abstractMethodsThatArentConcretelyImplemented = abstractMethods.filter({ abstractMethod ->
                concreteMethods.filter({ concreteMethod ->
                    canMethodsReplaceOneAnother(abstractMethod.getChild("MethodHeader"), concreteMethod.getChild("MethodHeader"))
                }).isEmpty()
            })

            if (abstractMethodsThatArentConcretelyImplemented.isNotEmpty()) {
                return false
            }

            /**
             * If I have abstract methods, then there is a concrete method to implement them (in my descendents).
             * For every abstract method
             */

            val unshadowedPotentiallyUnimplementedInterfaceMethods = potentiallyUnimplementedInterfaceMethods.filter({ unimplementedInterfaceMethod ->
                abstractMethods.filter({ abstractMethod ->
                    canMethodsReplaceOneAnother(abstractMethod.getChild("MethodHeader"), unimplementedInterfaceMethod.getChild("MethodHeader"))
                }).isEmpty()
            })

            // Apply concrete methods to unshadowed unimplemented methods

            val canConcreteMethodImplementAbstractMethod = { concreteMethod: CSTNode, abstractMethod: CSTNode ->
                if (canMethodsReplaceOneAnother(concreteMethod, abstractMethod)) {
                    if ("protected" in getModifiers(concreteMethod) && "public" in getModifiers(abstractMethod)) {
                        throw ConcreteMethodImplementsPublicMethodWithProtectedVisibility(className, getDeclarationName(concreteMethod))
                    }
                    true
                } else {
                    false
                }
            }

            val remainingUnimplementedAbstractInterfaceMethods = unshadowedPotentiallyUnimplementedInterfaceMethods.filter({ unimplementedInterfaceMethod ->
                concreteMethods.filter({ concreteMethod -> canConcreteMethodImplementAbstractMethod(concreteMethod, unimplementedInterfaceMethod) }).isEmpty()
            })

            val superNames = classDeclaration.getChild("SuperOpt").getDescendants("Name")

            if (superNames.isEmpty()) {
                val baseSuperClassConcreteMethods =
                    if (importOnDemandDeclarationEnvironment.contains("Object")) {
                        val baseSuperClass = importOnDemandDeclarationEnvironment.find("Object")
                        val baseSuperClassClassBody = baseSuperClass.getChild("ClassBody")
                        baseSuperClassClassBody.getDescendants("MethodDeclaration")
                    } else {
                        listOf()
                    }

                val finalUnimplementedAbstractInterfaceMethods = remainingUnimplementedAbstractInterfaceMethods.filter({ unimplementedInterfaceMethod ->
                    baseSuperClassConcreteMethods.filter({ concreteMethod ->
                        canConcreteMethodImplementAbstractMethod(concreteMethod, unimplementedInterfaceMethod)
                    }).isEmpty()
                })

                return finalUnimplementedAbstractInterfaceMethods.isEmpty()
            } else {
                val superClassDeclaration = superNames[0].getDeclaration()
                return areInterfaceMethodsImplemented(superClassDeclaration, concreteMethods, remainingUnimplementedAbstractInterfaceMethods)
            }
        }


        if (!areInterfaceMethodsImplemented(node, listOf(), listOf())) {
            if ("abstract" !in getModifiers(node)) {
                throw UnimplementedAbstractOrInterfaceMethodException(className)
            }
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

                    if (canMethodsReplaceOneAnother(ownFilteredMethodHeader, filteredInheritedMethodHeader)) {
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

        val allFieldDeclarations = packageManager.getClassInheritedDescendants(node, { it.name == "FieldDeclaration" })

        /**
         * 1. Do name resolution on current field if it's an own field.
         * 2. Add this field to the environment.
         */
        val isOwnFieldDeclaration = { otherFieldDeclaration: CSTNode ->
            ownFieldDeclarations.filter({ it === otherFieldDeclaration }).isNotEmpty()
        }

        val addFieldDeclarationToEnvironment = { fieldDeclaration: CSTNode ->
            if (isOwnFieldDeclaration(fieldDeclaration)) {
                this.visit(fieldDeclaration.getChild("Modifiers"))
                this.visit(fieldDeclaration.getChild("Type"))
            }

            val fieldDeclarationName = getDeclarationName(fieldDeclaration)
            if (isOwnFieldDeclaration(fieldDeclaration)) {
                if (environment.contains({ it.name == fieldDeclarationName && it.node.name == "FieldDeclaration" && isOwnFieldDeclaration(it.node) })) {
                    throw NameResolutionError("Tried to declare field \"$fieldDeclarationName\" twice in the same class.")
                }
            }

            environment.push(fieldDeclarationName, fieldDeclaration)
        }

        val resolveFieldDeclarationRvalues = { fieldDeclaration: CSTNode ->
            if (isOwnFieldDeclaration(fieldDeclaration)) {
                this.visit(fieldDeclaration)
            }
        }

        val isOwnMethodDeclaration = { otherDeclaration: CSTNode ->
            ownMethodDeclarations.filter({ it === otherDeclaration }).isNotEmpty()
        }


        val addMethodDeclarationToEnvironment = { methodDeclaration: CSTNode ->
            val methodName = getDeclarationName(methodDeclaration)
            environment.push(methodName, methodDeclaration)
        }

        val resolveMethodDeclarationBody = fun (methodDeclaration: CSTNode) {
            if (!(isOwnMethodDeclaration(methodDeclaration) || methodDeclaration.name == "ConstructorDeclaration")) {
                return
            }

            environment.withNewScope({
                if (methodDeclaration.name == "MethodDeclaration") {
                    val methodName = getDeclarationName(methodDeclaration)
                    environment.push(methodName, methodDeclaration)
                }
                this.visit(methodDeclaration)
            })
        }

        /**
         * Add Fields Names to Env
         */
        allFieldDeclarations.forEach(addFieldDeclarationToEnvironment)

        /**
         * Add Method Names to Env
         */
        if ("abstract" in getModifiers(node)) {
            packageManager.getAllMethodsOfAllInterfacesImplementedInClassHierarchyOf(node).reversed().forEach(addMethodDeclarationToEnvironment)
        }
        allMethodDeclarations.forEach(addMethodDeclarationToEnvironment)

        /**
         * Resolve Field bodies
         */
        allFieldDeclarations.forEach(resolveFieldDeclarationRvalues)

        /**
         * Resolve Method bodies.
         */
        allMethodDeclarations.forEach(resolveMethodDeclarationBody)

        /**
         * Resolve constructors' bodies
         */
        constructorDeclarations.forEach(resolveMethodDeclarationBody)
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
            environment.withNewScope({
                this.visit(methodHeader)
            })
        })

        if (resolutionDepth == ResolutionDepth.MemberDeclaration) {
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
        val allMethodDeclarations = packageManager.getInterfaceInheritedMethods(node, interfaceName, mutableSetOf())

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

                if (canMethodsReplaceOneAnother(abstractMethodHeader, otherAbstractMethodHeader)) {
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

        fun ownMethodsMustNotReplaceInheritedMethods(ownPredicate: (CSTNode) -> Boolean, inheritedPredicate: (CSTNode) -> Boolean) {
            val filteredOwnMethods = abstractMethodDeclarations.filter({ ownMethod -> ownPredicate(ownMethod) })
            val filteredAllMethods = allMethodDeclarations.filter({ inheritedOrOwnMethod -> inheritedPredicate(inheritedOrOwnMethod) })

            val inheritedAndFinalMethodDeclarations = filteredAllMethods - filteredOwnMethods

            filteredOwnMethods.forEach({interfaceMethod ->
                inheritedAndFinalMethodDeclarations.forEach(fun (inheritedInterfaceMethod: CSTNode) {
                    if (canMethodsReplaceOneAnother(interfaceMethod, inheritedInterfaceMethod)) {
                        throw IllegalMethodReplacement(interfaceName, getDeclarationName(interfaceMethod))
                    }
                })

            })
        }

        /**
         * TEST
         *
         * A method must not replace a final method.
         */
        ownMethodsMustNotReplaceInheritedMethods({ true }, { "final" in getModifiers(it) })

        /**
         * TEST
         *
         * A protected method must not replace a public method
         */
        ownMethodsMustNotReplaceInheritedMethods({ "protected" in getModifiers(it)}, { "public" in getModifiers(it) })
    }

    private fun getCurrentClassDeclaration(): CSTNode {
        return environment.find({(_, node) -> node.name == "ClassDeclaration" })
    }

    private fun getCurrentMethodDeclaration(): CSTNode? {
        return currentMethodDeclaration
    }

    private fun getCurrentFieldDeclaration(): CSTNode? {
        return currentFieldDeclaration
    }

    override fun visitPackageDeclaration(node: CSTNode) {
        val nameNode = node.getChild("Name")
        nameNode.setNameNodeClassification(NameNodeClassification.Package)
    }

    /**
     * TypeName:
     * In a single-type-import declaration (§7.5.1)
     */
    override fun visitSingleTypeImportDeclaration(node: CSTNode) {
        // Do nothing
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
        currentFieldDeclaration = node
        super.visitFieldDeclaration(node)

        // Type checking for field declaration.
        if (node.children[2].name == "VariableDeclarator") {
            val variableDeclarator = node.getChild("VariableDeclarator")
            val fieldType = getDeclarationType(node)
            val expressionType = variableDeclarator.getDescendant("Expression").getType()
            if (!packageManager.colonEquals(fieldType, expressionType)) {
                throw TypeCheckingError("FieldDeclaration initializer is not type assignable to the field type.")
            }
        }
        currentFieldDeclaration = null
    }

    override fun visitBlock(node: CSTNode) {
        environment.withNewScope({
            super.visitBlock(node)
        })
    }


    override fun visitMethodDeclaration(node: CSTNode) {
        currentMethodDeclaration = node
        super.visitMethodDeclaration(node)
        currentMethodDeclaration = null
    }

    /**
     * TypeName:
     * As the type of a formal parameter of a method or constructor
     * (§8.4.1, §8.8.1, §9.4)
     */
    override fun visitFormalParameter(node: CSTNode) {
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
        currentLocalVariableDeclaration = node
        isCurrentLocalVariableDeclarationInitialized = false
        val variableDeclaratorNode = node.getChild("VariableDeclarator")
        val variableName = variableDeclaratorNode.getChild("IDENTIFIER").lexeme

        if (environment.contains({ it.name == variableName && (it.node.name == "LocalVariableDeclaration" || it.node.name == "FormalParameter") })) {
            throw NameResolutionError("Tried to declare variable \"$variableName\" twice in the same scope.")
        }

        environment.push(variableName, node)

        super.visitLocalVariableDeclaration(node)

        // Do type checking on the local variable declaration.
        val localVariableType = getDeclarationType(node)
        val expressionType = variableDeclaratorNode.getDescendant("Expression").getType()
        if (!packageManager.colonEquals(localVariableType, expressionType)) {
            throw TypeCheckingError("LocalVariableDeclaration initializer is not type assignable to the variable type.")
        }

        isCurrentLocalVariableDeclarationInitialized = false
        currentLocalVariableDeclaration = null
    }

    fun getCurrentLocalVariableDeclaration(): CSTNode? {
        return currentLocalVariableDeclaration
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

        val classBeingInstantiated = nameNode.getDeclaration()
        if ("abstract" in getModifiers(classBeingInstantiated)) {
            throw TypeCheckingError("Cannot instantiate an abstract class.")
        }

        val actualParameterTypes = getActualParameterTypes(node.getChild("ArgumentListOpt"))

        val listOfConstructorDeclarations = classBeingInstantiated.getDescendants("ConstructorDeclaration")

        val listOfConstructorFormalParameterTypes = listOfConstructorDeclarations
            .map({ it.getChild("ConstructorDeclarator") })
            .map({ it.getChild("FormalParameterListOpt") })
            .map({ getFormalParameterTypes(it) })

        val name = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme }).joinToString(".")

        if (!listOfConstructorFormalParameterTypes.contains(actualParameterTypes)) {
            throw TypeCheckingError("No constructor matches the argument types of ClassInstanceCreationExpression for \"$name\"")
        }

        val indexOfSelectedConstructorDeclaration = listOfConstructorFormalParameterTypes.indexOf(actualParameterTypes)
        val selectedConstructorDeclaration = listOfConstructorDeclarations[indexOfSelectedConstructorDeclaration]

        node.setDeclaration(selectedConstructorDeclaration)

        if ("protected" in getModifiers(selectedConstructorDeclaration)) {
            val currentClassDeclaration = getCurrentClassDeclaration()
            val isConstructorInSamePackage = packageManager.getPackageNameOfType(classBeingInstantiated) == packageManager.getPackageNameOfType(currentClassDeclaration)

            if (!isConstructorInSamePackage) {
                val currentClassDeclarationName = getDeclarationName(currentClassDeclaration)
                val instantiationClassName = getDeclarationName(classBeingInstantiated)
                throw TypeCheckingError("Tried to instantiate class \"$instantiationClassName\" within class \"$currentClassDeclarationName\" using a protected constructor, when it's not in the same package as \"$currentClassDeclarationName\"")
            }
        }

        node.setType(nameNode.getType())
    }

    /**
     * TypeName:
     * As the element type of an array to be created in an array creation expression (§15.10)
     */
    override fun visitArrayCreationExpression(node: CSTNode) {
        if (node.children[1].name == "ClassOrInterfaceType") {
            val classOrInterfaceTypeNode = node.getChild("ClassOrInterfaceType")
            val nameNode = classOrInterfaceTypeNode.getChild("Name")
            resolveTypeName(nameNode)

            super.visitArrayCreationExpression(node)

            node.setType(Type(nameNode.getType().type, true))
        } else {
            super.visitArrayCreationExpression(node)
            val primitiveType = node.getChild("PrimitiveType").children[0]
            node.setType(Type(primitiveType, true))
        }

        val dimExpr = node.getChild("DimExpr")
        val expression = dimExpr.getChild("Expression")

        if (!expression.getType().isNumeric()) {
            throw TypeCheckingError("Tried to create an array using an expression that doesn't resolve to a number.")
        }
    }

    /**
     * TypeName:
     * As the type mentioned in the cast operator of a cast expression (§15.16)
     */
    override fun visitCastExpression(node: CSTNode) {
        when (node.children[1].name) {
            "Name" -> {
                val nameNode = node.getChild("Name")
                resolveTypeName(nameNode)
                visit(node.getChild("UnaryExpressionNotPlusMinus"))
            }

            "Expression" -> {
                val expressionNode = node.getChild("Expression")
                val nameNode = expressionNode.getDescendant("Name")
                resolveTypeName(nameNode)

                expressionNode.setType(nameNode.getType())

                visit(node.getChild("UnaryExpressionNotPlusMinus"))
            }
            else -> {
                super.visitCastExpression(node)
            }
        }

        when (node.children[1].name) {
            "Expression" -> {
                // Cast to some reference type since the weeder ensures that Expression contains Name.
                val typeOfExpression = node.getChild("Expression").getType()
                val typeOfUnaryExpressionNotPlusMinus = node.getChild("UnaryExpressionNotPlusMinus").getType()

                if (packageManager.colonEquals(typeOfExpression, typeOfUnaryExpressionNotPlusMinus)) {
                    node.setType(typeOfExpression)
                } else if (packageManager.colonEquals(typeOfUnaryExpressionNotPlusMinus, typeOfExpression)) {
                    node.setType(typeOfExpression)
                    node.markDowncast()
                } else {
                    throw TypeCheckingError("Tried to cast non-related types")
                }
            }
            "Name" -> {
                // Cast to an array of some reference type.
                val typeOfCast = Type(node.getChild("Name").getType().type, true)
                val typeOfUnaryExpressionNotPlusMinus = node.getChild("UnaryExpressionNotPlusMinus").getType()

                if (packageManager.colonEquals(typeOfCast, typeOfUnaryExpressionNotPlusMinus)) {
                    node.setType(typeOfCast)
                } else if (packageManager.colonEquals(typeOfUnaryExpressionNotPlusMinus, typeOfCast)) {
                    node.setType(typeOfCast)
                    node.markDowncast()
                } else {
                    throw TypeCheckingError("Tried to cast non-related type arrays")
                }
            }
            "PrimitiveType" -> {
                // Cast to an array or non-array of some primitive type.
                val typeOfCast = Type(node.getChild("PrimitiveType").children[0], node.getChild("DimOpt").children.size == 1)
                val typeOfUnaryExpression = node.getChild("UnaryExpression").getType()

                if (typeOfCast.isNumeric() && typeOfUnaryExpression.isNumeric()) {
                    node.setType(typeOfCast)
                } else if (packageManager.colonEquals(typeOfCast, typeOfUnaryExpression)) {
                    node.setType(typeOfCast)
                } else if (packageManager.colonEquals(typeOfUnaryExpression, typeOfCast)) {
                    node.setType(typeOfCast)
                    node.markDowncast()
                } else {
                    throw TypeCheckingError("Tried to cast non-related type arrays")
                }

            }
        }
    }

    /**
     * TypeName:
     * As the type that follows the instanceof relational operator (§15.20.2)
     */
    override fun visitRelationalExpression(node: CSTNode) {
        if (node.children.size > 1 && node.children[2].name == "ReferenceType") {
            val referenceType = node.getChild("ReferenceType")

            if (referenceType.children[0].name == "ArrayType") {
                val arrayType = referenceType.getChild("ArrayType")
                if (arrayType.children[0].name == "PrimitiveType") {
                    val primitiveType = arrayType.getChild("PrimitiveType")
                    referenceType.setType(Type(primitiveType.children[0], true))
                } else {
                    // Name
                    val nameNode = arrayType.getDescendant("Name")
                    resolveTypeName(nameNode)
                    referenceType.setType(Type(nameNode.getType().type, true))
                }
            } else {
                // ClassOrInterfaceType
                val nameNode = referenceType.getDescendant("Name")
                resolveTypeName(nameNode)
                referenceType.setType(nameNode.getType())
            }
        }

        super.visitRelationalExpression(node)

        if (node.children[0].name == "AdditiveExpression") {
            node.setType(node.getChild("AdditiveExpression").getType())
        } else if (node.children[1].name in listOf("<", ">", "<=", ">=")) {
            val operator = node.children[1].name
            val typeOfRelationalExpression = node.getChild("RelationalExpression").getType()
            val typeOfAdditiveExpression = node.getChild("AdditiveExpression").getType()

            if (!packageManager.colonEquals(typeOfRelationalExpression, typeOfAdditiveExpression) && !packageManager.colonEquals(typeOfAdditiveExpression, typeOfRelationalExpression)) {
                throw TypeCheckingError("Tried to compare non-type assignable expressions using operator \"$operator\"")
            }

            node.setType(Type(CSTNode("boolean"), false))
        } else {
            val typeOfRelationalExpression = node.getChild("RelationalExpression").getType()
            val typeOfReferenceType = node.getChild("ReferenceType").getType()

            if (!(typeOfRelationalExpression.isReference() || typeOfRelationalExpression.isNull())) {
                throw TypeCheckingError("RelationalExpression operand of \"instanceof\" operator was neither a reference type nor null.")
            }

            if (!typeOfReferenceType.isReference()) {
                throw TypeCheckingError("ReferenceType operand of \"instanceof\" operator was not a reference type")
            }

            if (!packageManager.colonEquals(typeOfRelationalExpression, typeOfReferenceType) && !packageManager.colonEquals(typeOfReferenceType, typeOfRelationalExpression)) {
                throw TypeCheckingError("Operands of \"instanceof\" operator are not type-assignable to each other")
            }

            node.setType(Type(CSTNode("boolean"), false))
        }
    }

    /**
     * ExpressionName:
     * As the array reference expression in an array access expression (§15.13)
     */
    override fun visitArrayAccess(node: CSTNode) {
        val arrayAccessType =
            if (node.children[0].name == "Name") {
                val nameNode = node.getChild("Name")
                resolveExpressionName(nameNode)

                super.visitArrayAccess(node)

                val qualifiedIdentifier = getQualifiedIdentifierFromName(nameNode)
                val nameDeclaration = nameNode.getDeclaration()
                val nameType = getDeclarationType(nameDeclaration)

                if (!nameType.isArray) {
                    throw TypeCheckingError("Tried to access a name \"$qualifiedIdentifier\" that is not an array")
                }

                Type(nameType.type, false)
            } else {
                // First child is PrimaryNoNewArray
                super.visitArrayAccess(node)

                val primaryNoNewArray = node.getChild("PrimaryNoNewArray")
                val primaryNoNewArrayType = primaryNoNewArray.getType()

                if (!primaryNoNewArrayType.isArray) {
                    throw TypeCheckingError("Tried to access a PrimaryNoNewArray that is not an array")
                }

                Type(primaryNoNewArrayType.type, false)
            }

        val expression = node.getChild("Expression")
        val expressionType = expression.getType()

        if (!expressionType.isNumeric()) {
            throw TypeCheckingError("Tried to access non-number index of an array")
        }

        node.setType(arrayAccessType)
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

        when (node.children[0].name) {
            "!" -> {
                val unaryExressionType = node.getChild("UnaryExpression").getType()
                if (unaryExressionType.type.name != "boolean") {
                    throw TypeCheckingError("Tried to call the \"!\" operator on a non-boolean.")
                }
                node.setType(unaryExressionType)
            }
            "CastExpression" -> {
                node.setType(node.getChild("CastExpression").getType())
            }
            "Primary" -> {
                node.setType(node.getChild("Primary").getType())
            }
            "Name" -> {
                node.setType(node.getChild("Name").getType())
            }
        }
    }

    fun isLValue(node: CSTNode): Boolean {
        val leftHandSide = getCurrentLeftHandSide()
        if (leftHandSide == null) {
            return false
        }

        return leftHandSide.children[0] == node
    }

    fun getCurrentLeftHandSide(): CSTNode? {
        if (leftHandSideStack.isEmpty()) {
            return null
        }

        return leftHandSideStack.peek()
    }

    /**
     * ExpressionName:
     * As the left-hand operand of an assignment operator (§15.26)
     */
    override fun visitLeftHandSide(node: CSTNode) {
        leftHandSideStack.push(node)
        try {
            val nameNode = node.getChild("Name")
            resolveExpressionName(nameNode)
        } catch (ex: FoundNoChild) {
        }

        super.visitLeftHandSide(node)
        node.setType(node.children[0].getType())
        leftHandSideStack.pop()
    }

    fun validateUsageOfMethodInvocation(methodDeclarationNode: CSTNode) {
        if (getCurrentMethodDeclaration() != null) {
            val currentMethodDeclaration = getCurrentMethodDeclaration()!!

            val isCurrentMethodDeclarationStatic = "static" in getModifiers(currentMethodDeclaration)
            val isMethodBeingUsedStatic = "static" in getModifiers(methodDeclarationNode)

            if (isCurrentMethodDeclarationStatic && !isMethodBeingUsedStatic) {
                val currentMethodName = getDeclarationName(currentMethodDeclaration)
                val methodBeingUsedName = getDeclarationName(methodDeclarationNode)

                throw NameResolutionError("Tried to use instance method \"$methodBeingUsedName\" within static method \"$currentMethodName\"")
            }
        }

        if (getCurrentFieldDeclaration() != null) {
            val currentFieldDeclaration = getCurrentFieldDeclaration()!!

            val isCurrentFieldDeclarationStatic = "static" in getModifiers(currentFieldDeclaration)
            val isMethodBeingUsedStatic = "static" in getModifiers(methodDeclarationNode)

            if (isCurrentFieldDeclarationStatic && !isMethodBeingUsedStatic) {
                val currentFieldDeclarationName = getDeclarationName(currentFieldDeclaration)
                val methodBeingUsedName = getDeclarationName(methodDeclarationNode)

                throw NameResolutionError("Tried to use instance method \"$methodBeingUsedName\" in initialization of static field \"$currentFieldDeclarationName\"")
            }
        }
    }

    /**
     * MethodName
     * Before the “(” in a method invocation expression (§15.12)
     */
    override fun visitMethodInvocation(node: CSTNode) {
        super.visitMethodInvocation(node)
        val actualParameterTypes = getActualParameterTypes(node.getChild("ArgumentListOpt"))

        if (node.children[0].name == "Name") {
            val nameNode = node.getChild("Name")
            nameNode.setNameNodeClassification(NameNodeClassification.Method)

            if (nameNode.children[0].name == "SimpleName") {
                val simpleName = nameNode.getChild("SimpleName")
                val methodName = simpleName.getChild("IDENTIFIER").lexeme
                val currentClassDeclaration = getCurrentClassDeclaration()
                val isCurrentClassAbstract = "abstract" in getModifiers(currentClassDeclaration)

                val methodDeclarationNode = environment.find(fun (entry: Environment.Entry): Boolean {
                    val (identifier, methodDeclaration) = entry

                    if (identifier != methodName) {
                        return false
                    }

                    if (methodDeclaration.name == "MethodDeclaration" || methodDeclaration.name == "AbstractMethodDeclaration" && isCurrentClassAbstract) {
                        if ("static" in getModifiers(methodDeclaration)) {
                            return false
                        }

                        val methodHeader = methodDeclaration.getChild("MethodHeader")
                        val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                        val formalParameterListOpt = methodDeclarator.getChild("FormalParameterListOpt")
                        return getFormalParameterTypes(formalParameterListOpt) == actualParameterTypes
                    }

                    return false
                })

                validateUsageOfMethodInvocation(methodDeclarationNode)

                addNameResolution(nameNode, methodDeclarationNode)
                nameNode.setNameNodeClassification(NameNodeClassification.Method)
                node.setType(getDeclarationType(methodDeclarationNode))
            } else {
                val qualifiedName = nameNode.getChild("QualifiedName")
                val methodName = qualifiedName.getChild("IDENTIFIER").lexeme
                val ambiguousPrefixName = qualifiedName.getChild("Name")
                resolveAmbiguousName(ambiguousPrefixName)

                val serializedAmbiguousPrefix = serializeName(ambiguousPrefixName)

                when (ambiguousPrefixName.getNameNodeClassification()) {
                    NameNodeClassification.Package -> {
                        val packageName = serializedAmbiguousPrefix
                        throw NameResolutionError("Tried to call method \"$methodName\" on package \"$packageName\"")
                    }

                    NameNodeClassification.Type -> {
                        val resolvedType = ambiguousPrefixName.getType()
                        val typeDeclaration = resolvedType.type
                        val currentClass = getCurrentClassDeclaration()

                        if (typeDeclaration.name != "ClassDeclaration") {
                            throw TypeCheckingError("Tried to call static method \"$methodName\" on a non-class ${typeDeclaration.name}")
                        }

                        val accessibleStaticMethods = packageManager.getAccessibleStaticMembers(currentClass, typeDeclaration)
                            .filter({ it.name in listOf("MethodDeclaration") })
                        val resolvedStaticMethod = accessibleStaticMethods.find({ methodDeclaration ->
                            if (getDeclarationName(methodDeclaration) != methodName) {
                                false
                            } else {
                                val methodHeader = methodDeclaration.getChild("MethodHeader")
                                val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                                val formalParameterListOpt = methodDeclarator.getChild("FormalParameterListOpt")
                                getFormalParameterTypes(formalParameterListOpt) == actualParameterTypes
                            }
                        })

                        if (resolvedStaticMethod == null) {
                            throw TypeCheckingError("Tried to invoke static method \"${getDeclarationName(typeDeclaration)}.$methodName\" from class ${getDeclarationName(currentClass)}, but none found.")
                        }

                        addNameResolution(nameNode, resolvedStaticMethod)
                        node.setType(getDeclarationType(resolvedStaticMethod))
                    }

                    NameNodeClassification.Expression -> {
                        val resolvedType = ambiguousPrefixName.getType()
                        val resolvedTypeDeclaration = resolvedType.type
                        val currentClass = getCurrentClassDeclaration()

                        val accessibleInstanceMethods =
                            if (!resolvedType.isArray) {
                                if (resolvedType.isNumeric() || resolvedType.isBoolean() || resolvedType.isNull()) {
                                    throw TypeCheckingError("Tried to invoke method \"$methodName\" on an expression with a primitive type \"${resolvedTypeDeclaration.name}\"")
                                }

                                packageManager.getAccessibleInstanceMembers(currentClass, resolvedTypeDeclaration)
                                    .filter({ it.name in listOf("MethodDeclaration", "AbstractMethodDeclaration") })
                            } else {
                                val baseClass = packageManager.getJavaLangType("Object")
                                val cloneableInterface = packageManager.getJavaLangType("Cloneable")
                                val serializableInterface = packageManager.getJavaIOType("Serializable")

                                listOf(baseClass, cloneableInterface, serializableInterface)
                                    .filterNotNull()
                                    .flatMap({ type ->
                                        type.getDescendants({ it.name in listOf("AbstractMethodDeclaration", "MethodDeclaration")})
                                    })
                            }

                        val resolvedInstanceMethod = accessibleInstanceMethods.find({ methodDeclaration ->
                            if (getDeclarationName(methodDeclaration) != methodName) {
                                false
                            } else {
                                val methodHeader = methodDeclaration.getChild("MethodHeader")
                                val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                                val formalParameterListOpt = methodDeclarator.getChild("FormalParameterListOpt")
                                getFormalParameterTypes(formalParameterListOpt) == actualParameterTypes
                            }
                        })

                        if (resolvedInstanceMethod == null) {
                            throw TypeCheckingError("Tried to invoke instance method \"$methodName\" from class ${getDeclarationName(currentClass)}, but none found.")
                        }

                        addNameResolution(nameNode, resolvedInstanceMethod)
                        node.setType(getDeclarationType(resolvedInstanceMethod))
                    }

                    NameNodeClassification.Method -> {
                        throw TypeCheckingError("Ambiguous name illegally resolved to MethodName")
                    }
                }
            }
        } else {
            val typeOfPrimary = node.getChild("Primary").getType()
            val methodName = node.getChild("IDENTIFIER").lexeme
            val currentClass = getCurrentClassDeclaration()

            val typeDeclaration = typeOfPrimary.type

            if (typeDeclaration.name != "ClassDeclaration") {
                throw TypeCheckingError("Tried to call instance method \"$methodName\" on a non-class ${typeDeclaration.name}")
            }

            val accessibleInstanceMethods = packageManager.getAccessibleInstanceMembers(currentClass, typeDeclaration)
                .filter({ it.name in listOf("MethodDeclaration", "AbstractMethodDeclarations")})
            val resolvedMethod = accessibleInstanceMethods.find({ methodDeclaration ->
                if (getDeclarationName(methodDeclaration) != methodName) {
                    false
                } else {
                    val methodHeader = methodDeclaration.getChild("MethodHeader")
                    val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                    val formalParameterListOpt = methodDeclarator.getChild("FormalParameterListOpt")
                    getFormalParameterTypes(formalParameterListOpt) == actualParameterTypes
                }
            })

            if (resolvedMethod == null) {
                throw TypeCheckingError("Tried to invoke instance method \"${getDeclarationName(typeDeclaration)}.$methodName\" from class ${getDeclarationName(currentClass)}, but none found.")
            }

            addNameResolution(node.getChild("IDENTIFIER"), resolvedMethod)
            node.setType(getDeclarationType(resolvedMethod))
        }
    }

    /**
     * PackageOrTypeName
     * In a type-import-on-demand declaration (§7.5.2)
     */
    override fun visitTypeImportOnDemandDeclaration(node: CSTNode) {
        val nameNode = node.getChild("Name")
        nameNode.setNameNodeClassification(NameNodeClassification.Package)
    }

    override fun visitType(node: CSTNode) {
        try {
            val nameNode = node.getDescendant("Name")
            resolveTypeName(nameNode)
        } catch (ex: FoundNoDescendant) {

        }
    }

    override fun visitLiteral(node: CSTNode) {
        val typeDeclaration = when(node.children[0].name) {
            "INTEGER" -> CSTNode("int")
            "BOOLEAN" -> CSTNode("boolean")
            "CHARACTER" -> CSTNode("char")
            "STRING" -> findTypeDeclarationByName("String")!!
            "NULL" -> CSTNode("NULL")
            else -> {
                throw TypeCheckingError("Detected illegal type when visiting literal.")
            }
        }

        node.setType(Type(typeDeclaration, false))
    }

    override fun visitPrimaryNoNewArray(node: CSTNode) {
        super.visitPrimaryNoNewArray(node)

        val childType = when (node.children[0].name) {
            "this" -> {
                if (getCurrentMethodDeclaration() != null) {
                    val currentMethodDeclaration = getCurrentMethodDeclaration()!!
                    val currentMethodDeclarationName = getDeclarationName(currentMethodDeclaration)

                    if ("static" in getModifiers(currentMethodDeclaration)) {
                        throw TypeCheckingError("Tried to access \"this\" inside static method $currentMethodDeclarationName")
                    }
                }

                if (getCurrentFieldDeclaration() != null) {
                    val currentFieldDeclaration = getCurrentFieldDeclaration()!!
                    val currentFieldDeclarationName = getDeclarationName(currentFieldDeclaration)

                    if ("static" in getModifiers(currentFieldDeclaration)) {
                        throw TypeCheckingError("Tried to access \"this\" inside static field declaration $currentFieldDeclarationName")
                    }

                }

                Type(getCurrentClassDeclaration(), false)
            }
            "(" -> node.getChild("Expression").getType()
            else -> {
                node.children[0].getType()
            }
        }

        node.setType(childType)
    }

    override fun visitPrimary(node: CSTNode) {
        super.visitPrimary(node)
        node.setType(node.children[0].getType())
    }

    override fun visitExpression(node: CSTNode) {
        super.visitExpression(node)
        node.setType(node.children[0].getType())
    }

    override fun visitAssignmentExpression(node: CSTNode) {
        super.visitAssignmentExpression(node)
        node.setType(node.children[0].getType())
    }

    override fun visitAssignment(node: CSTNode) {
        super.visitAssignment(node)

        val leftHandSideType = node.getChild("LeftHandSide").getType()
        val assignmentExpressionType = node.getChild("AssignmentExpression").getType()

        if (!packageManager.colonEquals(leftHandSideType, assignmentExpressionType)) {
            throw TypeCheckingError("In Assignment, operand AssignmentExpression is not type assignable to LeftHandSide.")
        }

        node.setType(leftHandSideType)
    }

    override fun visitConditionalOrExpression(node: CSTNode) {
        super.visitConditionalOrExpression(node)

        if (node.children[0].name == "ConditionalAndExpression") {
            node.setType(node.getChild("ConditionalAndExpression").getType())
        } else {
            val typeOfConditionalOrExpression = node.getChild("ConditionalOrExpression").getType()
            val typeOfConditionalAndExpression = node.getChild("ConditionalAndExpression").getType()

            if (
                !(!typeOfConditionalAndExpression.isArray && typeOfConditionalAndExpression.type.name == "boolean") ||
                !(!typeOfConditionalOrExpression.isArray && typeOfConditionalOrExpression.type.name == "boolean")
            ) {
                throw TypeCheckingError("Tried to use \"||\" operator with a non-boolean type")
            }

            node.setType(Type(CSTNode("boolean"), false))
        }
    }

    override fun visitConditionalAndExpression(node: CSTNode) {
        super.visitConditionalAndExpression(node)
        if (node.children[0].name == "OrExpression") {
            node.setType(node.getChild("OrExpression").getType())
        } else {
            val typeOfOrExpression = node.getChild("OrExpression").getType()
            val typeOfConditionalAndExpression = node.getChild("ConditionalAndExpression").getType()

            if (
                !(!typeOfConditionalAndExpression.isArray && typeOfConditionalAndExpression.type.name == "boolean") ||
                !(!typeOfOrExpression.isArray && typeOfOrExpression.type.name == "boolean")
            ) {
                throw TypeCheckingError("Tried to use \"&&\" operator with a non-boolean type")
            }

            node.setType(Type(CSTNode("boolean"), false))
        }
    }

    override fun visitOrExpression(node: CSTNode) {
        super.visitOrExpression(node)
        if (node.children[0].name == "AndExpression") {
            node.setType(node.getChild("AndExpression").getType())
        } else {
            val typeOfOrExpression = node.getChild("OrExpression").getType()
            val typeOfAndExpression = node.getChild("AndExpression").getType()

            if (
                !(!typeOfAndExpression.isArray && typeOfAndExpression.type.name == "boolean") ||
                !(!typeOfOrExpression.isArray && typeOfOrExpression.type.name == "boolean")
            ) {
                throw TypeCheckingError("Tried to use eager boolean or operator \"|\" with non-booleans.")
            }

            node.setType(Type(CSTNode("boolean"), false))
        }
    }

    override fun visitAndExpression(node: CSTNode) {
        super.visitAndExpression(node)
        if (node.children[0].name == "EqualityExpression") {
            node.setType(node.getChild("EqualityExpression").getType())
        } else {
            val typeOfAndExpression = node.getChild("AndExpression").getType()
            val typeOfEqualityExpression = node.getChild("EqualityExpression").getType()

            if (
                !(!typeOfAndExpression.isArray && typeOfAndExpression.type.name == "boolean") ||
                !(!typeOfEqualityExpression.isArray && typeOfEqualityExpression.type.name == "boolean")
            ) {
                throw TypeCheckingError("Tried to use eager boolean or operator \"&\" with non-booleans.")
            }

            node.setType(Type(CSTNode("boolean"), false))
        }
    }

    override fun visitEqualityExpression(node: CSTNode) {
        super.visitEqualityExpression(node)
        if (node.children[0].name == "RelationalExpression") {
            node.setType(node.getChild("RelationalExpression").getType())
        } else {
            val typeOfEqualityExpression = node.getChild("EqualityExpression").getType()
            val typeOfRelationalExpression = node.getChild("RelationalExpression").getType()

            if (!packageManager.colonEquals(typeOfEqualityExpression, typeOfRelationalExpression) && !packageManager.colonEquals(typeOfRelationalExpression, typeOfEqualityExpression)) {
               throw TypeCheckingError("In EqualityExpression \"==\" LHS and RHS are not type assignable to each other.")
            }

            node.setType(Type(CSTNode("boolean"), false))
        }
    }

    override fun visitAdditiveExpression(node: CSTNode) {
        super.visitAdditiveExpression(node)

        if (node.children[0].name == "MultiplicativeExpression") {
            node.setType(node.getChild("MultiplicativeExpression").getType())
        } else if (node.children[1].name == "-") {
            val typeOfAdditiveExpression = node.getChild("AdditiveExpression").getType()
            val typeOfMultiplicativeExpression = node.getChild("MultiplicativeExpression").getType()

            if (!typeOfAdditiveExpression.isNumeric() || !typeOfMultiplicativeExpression.isNumeric()) {
                throw TypeCheckingError("In AdditiveExpression, tried to subtract non-numeric values")
            }

            node.setType(Type(CSTNode("int"), false))
        } else {
            val typeOfAdditiveExpression = node.getChild("AdditiveExpression").getType()
            val typeOfMultiplicativeExpression = node.getChild("MultiplicativeExpression").getType()

            val stringDeclaration = packageManager.getJavaLangType("String")
            val stringType = Type(stringDeclaration!!, false)

            if (typeOfAdditiveExpression != stringType && typeOfMultiplicativeExpression != stringType) {
                if (!typeOfAdditiveExpression.isNumeric() || !typeOfMultiplicativeExpression.isNumeric()) {
                    throw TypeCheckingError("In AdditiveExpression, tried to add non-numeric values where neither of them are strings")
                }

                node.setType(Type(CSTNode("int"), false))
            } else {
                if (typeOfMultiplicativeExpression.type.name == "void" || typeOfAdditiveExpression.type.name == "void") {
                    throw TypeCheckingError("In AdditiveExpression, tried to add a void type to a String.")
                }
                node.setType(stringType)
            }
        }
    }

    override fun visitMultiplicativeExpression(node: CSTNode) {
        super.visitMultiplicativeExpression(node)

        if (node.children[0].name == "UnaryExpression") {
            node.setType(node.getChild("UnaryExpression").getType())
        } else {
            val operator = node.children[1].name
            val typeOfMultiplicativeExpression = node.getChild("MultiplicativeExpression").getType()
            val typeOfUnaryExpression = node.getChild("UnaryExpression").getType()

            if (!(typeOfMultiplicativeExpression.isNumeric() && typeOfUnaryExpression.isNumeric())) {
                throw TypeCheckingError("In MultiplicativeExpression, tried to use \"$operator\" on non-numeric operands")
            }

            node.setType(Type(CSTNode("int"), false))
        }
    }

    override fun visitUnaryExpression(node: CSTNode) {
        super.visitUnaryExpression(node)

        if (node.children[0].name == "UnaryExpressionNotPlusMinus") {
            node.setType(node.getChild("UnaryExpressionNotPlusMinus").getType())
        } else {
            // First child is "-"
            val unaryExpressionType = node.getChild("UnaryExpression").getType()
            if (!unaryExpressionType.isNumeric()) {
                throw TypeCheckingError("In UnaryExpression, tried to prefix non-numeric expression with \"-\".")
            }
            node.setType(unaryExpressionType)
        }
    }

    override fun visitReturnStatement(node: CSTNode) {
        super.visitReturnStatement(node)

        val currentMethodDeclaration = getCurrentMethodDeclaration()
        val expressionOpt = node.getChild("ExpressionOpt")

        /**
         * Constructors can also have returns.
         */
        if (currentMethodDeclaration == null) {
            if (expressionOpt.children.size != 0) {
                throw TypeCheckingError("Tried to return something from a constructor of ${getDeclarationName(getCurrentClassDeclaration())}.")
            }

            return
        }

        val currentMethodReturnType = getDeclarationType(currentMethodDeclaration)

        if (currentMethodReturnType.type.name == "void") {
            if (expressionOpt.children.size != 0) {
                throw TypeCheckingError("Tried to return from a void method.")
            }
        } else {
            if (expressionOpt.children.size == 0) {
                throw TypeCheckingError("Tried to return void from a non-void method.")
            }
            val returnExpressionType = expressionOpt.getChild("Expression").getType()
            if (!packageManager.colonEquals(currentMethodReturnType, returnExpressionType)) {
                throw TypeCheckingError("Return type of expression is not type assignable to the method return type.")
            }
        }
    }

    override fun visitFieldAccess(node: CSTNode) {
        super.visitFieldAccess(node)

        val typeOfPrimary = node.getChild("Primary").getType()
        val fieldName = node.getChild("IDENTIFIER").lexeme

        if (typeOfPrimary.isArray) {
            if (fieldName != "length") {
                throw TypeCheckingError("Tried to access non-length field of an array")
            }

            if (isLValue(node)) {
                throw TypeCheckingError("In \"FieldAccess\", detected an \"array.length\" within a \"LeftHandSide\". This implies that you may be writing to final \"length\" property of an array.")
            }

            node.setType(Type(CSTNode("int"), false))
        } else {
            val currentClass = getCurrentClassDeclaration()
            val accessibleFields = packageManager.getAccessibleInstanceMembers(currentClass, typeOfPrimary.type)
                .filter({ it.name == "FieldDeclaration" })

            val matchingField = accessibleFields.find({ getDeclarationName(it) == fieldName })
            if (matchingField == null) {
                throw TypeCheckingError("No field \"$fieldName\" found in class ${getDeclarationName(typeOfPrimary.type)}")
            }

            node.getChild("IDENTIFIER").setDeclaration(matchingField)
            node.setType(getDeclarationType(matchingField))
        }
    }

    override fun visitConstructorDeclaration(node: CSTNode) {
        environment.withNewScope({
            super.visitConstructorDeclaration(node)

            val currentClass = getCurrentClassDeclaration()
            val superOpt = currentClass.getChild("SuperOpt")
            if (superOpt.children.size == 1) {
                val superClassNameNode = superOpt.getDescendant("Name")
                val superClass = superClassNameNode.getDeclaration()
                val superClassConstructors = superClass.getChild("ClassBody").getDescendants("ConstructorDeclaration")
                val emptyParamSuperClassConstructors = superClassConstructors.filter({ it.getChild("ConstructorDeclarator").getChild("FormalParameterListOpt").children.size == 0 })
                if (emptyParamSuperClassConstructors.size == 0) {
                    throw TypeCheckingError("Super class constructor does not have a constructor with empty parameters defined.")
                }
            }
        })
    }

    override fun visitIfThenStatement(node: CSTNode) {
        super.visitIfThenStatement(node)

        val typeOfExpression = node.getChild("Expression").getType()
        if (!typeOfExpression.isBoolean()) {
            throw TypeCheckingError("Detected a non-boolean expression inside IfThenStatement")
        }
    }

    override fun visitIfThenElseStatement(node: CSTNode) {
        super.visitIfThenElseStatement(node)

        val typeOfExpression = node.getChild("Expression").getType()
        if (!typeOfExpression.isBoolean()) {
            throw TypeCheckingError("Detected a non-boolean expression inside IfThenElseStatement")
        }
    }

    override fun visitIfThenElseStatementNoShortIf(node: CSTNode) {
        super.visitIfThenElseStatementNoShortIf(node)

        val typeOfExpression = node.getChild("Expression").getType()
        if (!typeOfExpression.isBoolean()) {
            throw TypeCheckingError("Detected a non-boolean expression inside IfThenElseStatementNoShortIf")
        }
    }

    override fun visitWhileStatement(node: CSTNode) {
        super.visitWhileStatement(node)

        val typeOfExpression = node.getChild("Expression").getType()
        if (!typeOfExpression.isBoolean()) {
            throw TypeCheckingError("Detected a non-boolean expression inside WhileStatement")
        }
    }

    override fun visitWhileStatementNoShortIf(node: CSTNode) {
        super.visitWhileStatementNoShortIf(node)

        val typeOfExpression = node.getChild("Expression").getType()
        if (!typeOfExpression.isBoolean()) {
            throw TypeCheckingError("Detected a non-boolean expression inside WhileStatementNoShortIf")
        }
    }

    override fun visitForStatement(node: CSTNode) {
        environment.withNewScope({
            super.visitForStatement(node)
        })

        val expressionOpt = node.getChild("ExpressionOpt")

        if (expressionOpt.children.size != 0) {
            val typeOfExpression = expressionOpt.getChild("Expression").getType()
            if (!typeOfExpression.isBoolean()) {
                throw TypeCheckingError("Detected a non-boolean expression inside ForStatement")
            }
        }
    }

    override fun visitForStatementNoShortIf(node: CSTNode) {
        environment.withNewScope({
            super.visitForStatementNoShortIf(node)
        })

        val expressionOpt = node.getChild("ExpressionOpt")

        if (expressionOpt.children.size != 0) {
            val typeOfExpression = expressionOpt.getChild("Expression").getType()
            if (!typeOfExpression.isBoolean()) {
                throw TypeCheckingError("Detected a non-boolean expression inside ForStatementNoShortIf")
            }
        }
    }
}

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
        fun resolveNames(packageManager: PackageManager) : Map<CSTNode, CSTNode> {
            val globalEnvironment = Environment()
            val visitor = NameResolutionVisitor(globalEnvironment, packageManager)

            packageManager.packages.forEach({(packageName, _) ->
                packageManager.getTypesDeclaredInPackage(packageName).forEach({(typeName, _) ->
                    val qualifiedTypeName = "$packageName.$typeName"
                    if (packageManager.doesPackageExist(qualifiedTypeName)) {
                        throw EitherPackageNameOrQualifiedType(qualifiedTypeName)
                    }
                })
            })

            val visitPackages = { resolutionDepth: NameResolutionVisitor.ResolutionDepth ->
                packageManager.packages.forEach({(packageName, compilationUnits) ->
                    compilationUnits.forEach({ compilationUnit ->
                        val iodPackages = compilationUnit
                            .getDescendants("TypeImportOnDemandDeclaration")
                            .map({ node -> node.getDescendants("IDENTIFIER") })
                            .map({ identifiers -> identifiers.map({ it.lexeme }).joinToString(".")})
                            .filter({ it != packageName })
                            .toMutableSet()

                        if (packageManager.doesPackageExist("java.lang")) {
                            iodPackages.add("java.lang")
                        }

                        val iodEnvironment = iodPackages
                            .fold(Environment(), { iodEnvironment, iodPackageName ->
                                if (!packageManager.doesPackageExist(iodPackageName)) {
                                    throw ImportOnDemandDeclarationDetectedForNonExistentPackage(iodPackageName)
                                }

                                val typesDeclaredInThisPackage = packageManager.getTypesDeclaredInPackage(iodPackageName)

                                typesDeclaredInThisPackage.forEach({(typeName, typeDeclarationNode) ->
                                    iodEnvironment.push(typeName, typeDeclarationNode)
                                })

                                iodEnvironment
                            })

                        visitor.attachImportOnDemandEnvironment(iodEnvironment)

                        globalEnvironment.withNewScope({
                            packageManager.getTypesDeclaredInPackage(packageName)
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

                                    if (!packageManager.doesPackageExist(stiPackageName)) {
                                        throw SingleTypeImportDeclarationDetectedForNonExistentPackage(stiPackageName)
                                    }

                                    val typesInPackage = packageManager.getTypesDeclaredInPackage(stiPackageName)

                                    if (!typesInPackage.contains(typeName)) {
                                        throw SingleTypeImportDeclarationDetectedForNonExistentType(stiPackageName, typeName)
                                    }

                                    globalEnvironment.push(typeName, typesInPackage[typeName]!!)
                                })

                            visitor.setResolutionDepth(resolutionDepth)
                            visitor.visit(compilationUnit)
                        })

                        visitor.detachImportOnDemandEnvironment()
                    })
                })
            }

            visitPackages(NameResolutionVisitor.ResolutionDepth.TypeDeclaration)
            visitPackages(NameResolutionVisitor.ResolutionDepth.MemberDeclaration)
            visitPackages(NameResolutionVisitor.ResolutionDepth.All)

            return visitor.getNameResolution()
        }
    }
}
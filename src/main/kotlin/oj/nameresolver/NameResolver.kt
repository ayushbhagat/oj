package oj.nameresolver

import oj.models.*

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
open class InterfaceHierarchyIsCyclic(interfaceName: String, firstDuplicateInterface: String) : HierarchyCheckingError("Detected a duplicate interface \"$firstDuplicateInterface\" in interface \"$interfaceName\"'s hierarchy.")
open class DuplicateMethodsDetectedInInterface(interfaceName: String, methodName: String): HierarchyCheckingError("Detected a duplicate method \"$methodName\" in interface \"$interfaceName\"")
open class TwoMethodsInInterfaceHierarchyWithSameSignatureButDifferentReturnTypes(interfaceName: String, methodName: String): HierarchyCheckingError("Two methods with the same name, \"$methodName\", but different return type exist in the interface hierarchy (including interface methods) of interface \"$interfaceName\"")
open class ConcreteMethodImplementsPublicMethodWithProtectedVisibility(className: String, methodName: String) : HierarchyCheckingError("Concrete method \"$className.$methodName\" implements \"public\" method with \"protected\" visibility.")

val isClassOrInterfaceDeclaration = { node: CSTNode ->
    node.name == "ClassDeclaration" || node.name == "InterfaceDeclaration"
}

val PRIMITIVE_TYPE_NAMES = setOf("byte", "short", "int", "char", "boolean")

class NameResolutionVisitor(
    private val environment: Environment,
    val typesDeclaredInPackages: Map<String, Map<String, CSTNode>>
) : CSTNodeVisitor() {

    enum class ResolutionDepth {
        TypeDeclaration, MemberDeclaration, All
    }

    private var resolutionDepth = ResolutionDepth.All
    private val nameResolution = mutableMapOf<CSTNode, CSTNode>()
    private var importOnDemandDeclarationEnvironment: Environment = Environment()
    private var currentFieldDeclaration: CSTNode? = null
    private var currentMethodDeclaration: CSTNode? = null
    private var currentLeftHandSide: CSTNode? = null

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

    class TriedToGetPackageOfNonType(nodeType: String): Exception("Tried to access package of a non-type CSTNode: \"$nodeType\"")
    class PackageNotFoundForType(typeName: String): Exception("Package not found for type \"$typeName\"")

    fun getPackageOfType(node: CSTNode): String {
        if (node.name != "ClassDeclaration" && node.name != "InterfaceDeclaration") {
            throw TriedToGetPackageOfNonType(node.name)
        }

        for ((packageName, types) in typesDeclaredInPackages) {
            if (types.containsValue(node)) {
                return packageName
            }
        }

        throw PackageNotFoundForType(getDeclarationName(node))
    }

    fun isSubClass(child: CSTNode, potentialAncestor: CSTNode): Boolean {
        if (child.name != "ClassDeclaration" || potentialAncestor.name != "ClassDeclaration") {
            throw CSTNodeError("Tried to check sub-class relationship on non-classes: ${child.name} ${potentialAncestor.name}")
        }

        val baseSuperClass = getClassFromIODEnvironment("Object")
        if (potentialAncestor == baseSuperClass) {
            return true
        }

        if (child === potentialAncestor) {
            return true
        }

        val superOptNode = child.getChild("SuperOpt")
        val superNameNode = if (superOptNode.children.size == 1) superOptNode.getDescendant("Name") else null

        if (superNameNode == null) {
            return false
        }

        return isSubClass(superNameNode.getDeclaration(), potentialAncestor)
    }

    fun isSubInterface(child: CSTNode, potentialAncestor: CSTNode): Boolean {
        if (child.name != "InterfaceDeclaration") {
            throw CSTNodeError("Tried to check sub-interface relationship on non-interface child: ${child.name}")
        }

        val baseSuperClass = getClassFromIODEnvironment("Object")
        if (potentialAncestor == baseSuperClass) {
            return true
        }

        if (potentialAncestor.name != "InterfaceDeclaration") {
            throw CSTNodeError("Tried to check sub-interface relationship on non-interface potential ancestor: ${potentialAncestor.name}")
        }

        if (child === potentialAncestor) {
            return true
        }

        val extendedInterfaces = child.getChild("ExtendsInterfaceOpt").getDescendants("Name").map({ it.getDeclaration() })

        if (extendedInterfaces.isEmpty()) {
            return false
        }

        if (extendedInterfaces.any({ extendedInterface -> isSubInterface(extendedInterface, potentialAncestor)})) {
            return true
        }

        return false
    }

    fun canAccessProtectedMethodsOf(currentClass: CSTNode, otherClass: CSTNode): Boolean {
        if (currentClass.name != "ClassDeclaration" && otherClass.name != "ClassDeclaration") {
            throw Exception("Expected currentClass and otherClass to be ClassDeclarations")
        }

        val isInSamePackage = getPackageOfType(currentClass) == getPackageOfType(otherClass)

        return isSubClass(currentClass, otherClass) || isInSamePackage
    }

    fun getAccessibleInstanceMembers(currentClass: CSTNode, typeToInspect: CSTNode): List<CSTNode> {
        if (currentClass.name != "ClassDeclaration") {
            throw TypeCheckingError("Tried to call method with currentClass.name == \"${currentClass.name}\" != \"ClassDeclaration\"")
        }

        return if (typeToInspect.name == "ClassDeclaration") {
            val shouldAddProtectedMethods = canAccessProtectedMethodsOf(typeToInspect, currentClass)

            val classToInspect = typeToInspect

            val accessibleMembers = mutableListOf<CSTNode>()
            val inaccessibleMembers = mutableSetOf<CSTNode>()

            var currentClassToInspect = classToInspect

            while (true) {
                val classBody = currentClassToInspect.getChild("ClassBody")
                val members = classBody.getDescendants({
                    it.name in listOf("FieldDeclaration", "MethodDeclaration", "AbstractMethodDeclaration") && "static" !in getModifiers(it)
                })

                val publicMembers = members.filter({ member -> "public" in getModifiers(member) })

                accessibleMembers.addAll(publicMembers)

                if (shouldAddProtectedMethods) {
                    val protectedMembers = members.filter({ member -> "protected" in getModifiers(member) })

                    if (canAccessProtectedMethodsOf(currentClass, currentClassToInspect)) {
                        accessibleMembers.addAll(protectedMembers)
                    } else {
                        inaccessibleMembers.addAll(protectedMembers)
                    }
                }

                val superOpt = currentClassToInspect.getChild("SuperOpt")
                if (superOpt.children.isNotEmpty()) {
                    currentClassToInspect = superOpt.getDescendant("Name").getDeclaration()
                } else {
                    val baseSuperClass = getClassFromIODEnvironment("Object")

                    if (currentClassToInspect === baseSuperClass) {
                        break
                    }

                    if (baseSuperClass != null) {
                        currentClassToInspect = baseSuperClass
                    }
                }
            }

            return accessibleMembers.filter({ accessibleMember ->
                inaccessibleMembers.filter(fun (inaccessibleMember: CSTNode): Boolean {
                    if (inaccessibleMember.name == accessibleMember.name) {
                        if (inaccessibleMember.name == "FieldDeclaration") {
                            return getDeclarationName(inaccessibleMember) == getDeclarationName(accessibleMember)
                        }

                        if (inaccessibleMember.name == "MethodDeclaration") {
                            return canMethodsReplaceOneAnother(
                                accessibleMember.getChild("MethodHeader"),
                                inaccessibleMember.getChild("MethodHeader")
                            )
                        }
                    }

                    return false
                }).isEmpty()
            }) + getAllMethodsOfAllInterfacesImplementedInClassHierarchyOf(classToInspect)
        } else if (typeToInspect.name == "InterfaceDeclaration"){
            getInterfaceInheritedMethods(typeToInspect)
        } else {
            throw TypeCheckingError("Tried to call \"getAccessibleInstanceMembers\" with a non-interface and non-class typeToInspect: \"${typeToInspect.name}\"")
        }
    }

    fun getAccessibleStaticMembers(currentClass: CSTNode, classToInspect: CSTNode): List<CSTNode> {
        if (currentClass.name != "ClassDeclaration" || classToInspect.name != "ClassDeclaration") {
            throw TypeCheckingError("Tried to call getAccessibleStaticMembers on a non-class arguments.")
        }

        val accessibleMembers = mutableListOf<CSTNode>()
        val inaccessibleMembers = mutableSetOf<CSTNode>()

        var currentClassToInspect = classToInspect

        while (true) {
            val classBody = currentClassToInspect.getChild("ClassBody")
            val members = classBody.getDescendants({
                it.name in listOf("FieldDeclaration", "MethodDeclaration") && "static" in getModifiers(it)
            })

            val publicMembers = members.filter({ member -> "public" in getModifiers(member) })
            val protectedMembers = members.filter({ member -> "protected" in getModifiers(member) })

            accessibleMembers.addAll(publicMembers)

            if (canAccessProtectedMethodsOf(currentClass, currentClassToInspect)) {
                accessibleMembers.addAll(protectedMembers)
            } else {
                inaccessibleMembers.addAll(protectedMembers)
            }

            val superOpt = currentClassToInspect.getChild("SuperOpt")
            if (superOpt.children.isNotEmpty()) {
                currentClassToInspect = superOpt.getDescendant("Name").getDeclaration()
            } else {
                val baseSuperClass = getClassFromIODEnvironment("Object")

                if (currentClassToInspect === baseSuperClass) {
                    break
                }

                if (baseSuperClass != null) {
                    currentClassToInspect = baseSuperClass
                }
            }
        }

        return accessibleMembers.filter({ accessibleMember ->
            inaccessibleMembers.filter(fun (inaccessibleMember: CSTNode): Boolean {
                if (inaccessibleMember.name == accessibleMember.name) {
                    if (inaccessibleMember.name == "FieldDeclaration") {
                        return getDeclarationName(inaccessibleMember) == getDeclarationName(accessibleMember)
                    }

                    if (inaccessibleMember.name == "MethodDeclaration") {
                        return canMethodsReplaceOneAnother(
                            accessibleMember.getChild("MethodHeader"),
                            inaccessibleMember.getChild("MethodHeader")
                        )
                    }
                }

                return false
            }).isEmpty()
        })
    }

    private fun getInterfaceInheritedMethods(interfaceDeclaration: CSTNode, typeName: String = getDeclarationName(interfaceDeclaration), visited: MutableSet<CSTNode> = mutableSetOf()): List<CSTNode> {
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
        var extendedAbstractMethods = extendedInterfaceDeclarations.flatMap({
            getInterfaceInheritedMethods(it, typeName, visited)
        })

        visited.remove(interfaceDeclaration)

        if (extendedInterfaceNames.isEmpty()) {
            if (importOnDemandDeclarationEnvironment.contains("Object")) {
                val baseSuperClass = importOnDemandDeclarationEnvironment.find("Object")
                val baseSuperClassClassBody = baseSuperClass.getChild("ClassBody")
                val baseSuperClassMethodDeclarations = baseSuperClassClassBody.getDescendants("MethodDeclaration")
                val baseSuperInterfaceAbstractMethodDeclarations = baseSuperClassMethodDeclarations.map({ methodDeclaration ->
                    val methodHeader = methodDeclaration.getChild("MethodHeader")
                    val semicolon = CSTNode(";", "", mutableListOf())
                    val abstractMethodDeclaration = CSTNode("AbstractMethodDeclaration", "", mutableListOf(methodHeader, semicolon))
                     abstractMethodDeclaration
                })

                extendedAbstractMethods += baseSuperInterfaceAbstractMethodDeclarations
            }
        }

        return abstractMethodDeclarations + extendedAbstractMethods
    }

    /**
     * Gets all the methods from all the interfaces implemented by this class or any classes in its hierarchy
     */
    fun getAllMethodsOfAllInterfacesImplementedInClassHierarchyOf(classDeclaration: CSTNode): List<CSTNode> {
        if (classDeclaration.name != "ClassDeclaration") {
            throw CSTNodeError("Tried to call getAllInterfacesImplementedByClass on a non-class: ${classDeclaration.name}")
        }

        val extendedInterfaces = classDeclaration.getChild("InterfacesOpt").getDescendants("Name").map({ it.getDeclaration() })
        val abstractMethods = extendedInterfaces.flatMap({ getInterfaceInheritedMethods(it) })

        val superOpt = classDeclaration.getChild("SuperOpt")

        val superClassAbstractMethods =
            if (superOpt.children.size == 1) {
                val superClassDeclaration = superOpt.getDescendant("Name").getDeclaration()
                getAllMethodsOfAllInterfacesImplementedInClassHierarchyOf(superClassDeclaration)
            } else {
                listOf()
            }

        return abstractMethods + superClassAbstractMethods
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
                val typeDeclaration = environment.find(predicate)
                addNameResolution(nameNode, typeDeclaration)
                nameNode.setType(Type(typeDeclaration, false))
            } else {
                val iodLookup = importOnDemandDeclarationEnvironment.findAll(typeName)
                if (iodLookup.size == 1) {
                    addNameResolution(nameNode, iodLookup[0])
                    nameNode.setType(Type(iodLookup[0], false))
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

            /**
             * Ensure that no strict prefix of a qualified type is a package that contains types.
             *
             * If foo.bar.baz.A a = new foo.bar.baz.A() then:
             *  - foo can't have types
             *  - foo.bar can't have types
             *  - foo.bar.baz can have types (ex: foo.bar.baz.A)
             */

            for (i in IntRange(1, pkg.size - 1)) {
                val prefix = pkg.subList(0, i)
                val prefixPackageName = prefix.joinToString(".")

                if (typesDeclaredInPackages.containsKey(prefixPackageName)) {
                    throw NameResolutionError("Prefix \"$prefixPackageName\" of a package \"$packageName\" used to resolve type \"$typeName\" contained a type.")
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
            for (i in IntRange(0, pkg.size - 1)) {
                val prefix = pkg.subList(0, i + 1)

                if (prefix.size == 1) {
                    if (environment.contains(prefix[0]) || importOnDemandDeclarationEnvironment.contains(prefix[0])) {
                        throw NameResolutionError("Prefix \"${prefix[0]}\" of qualified type \"${name.joinToString(".")}\" is a type")
                    }
                } else {
                    val prefixPrefixPkg = prefix.subList(0, prefix.size - 1)
                    val prefixType = prefix.last()

                    if (typesDeclaredInPackages.contains(prefixPrefixPkg.joinToString(".")) && typesDeclaredInPackages[prefixPrefixPkg.joinToString(".")]!!.contains(prefixType)) {
                        throw NameResolutionError("Prefix \"${prefix.joinToString(".")}\" of qualified type \"${name.joinToString(".")}\" is a type")
                    }
                }
            }

            val typesInPackage = typesDeclaredInPackages[packageName]!!
            if (!typesInPackage.containsKey(typeName)) {
                throw NameResolutionError("Tried to access type \"$typeName\" in package \"$packageName\", but type \"$typeName\" doesn't exist.")
            }

            addNameResolution(nameNode, typesInPackage[typeName]!!)
            nameNode.setType(Type(typesInPackage[typeName]!!, false))
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

    fun resolveExpressionName(nameNode: CSTNode) {
        val name = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme })
        if (name.size == 1) {
            val expressionName = name[0]
            // Ensure that the expression name is defined. If so, add it to the name resolution, otherwise throw error.
            val declarationNode = environment.find(expressionName)
            addNameResolution(nameNode, declarationNode)

            if (declarationNode.name == "FieldDeclaration") {
                validateUsageOfFieldDeclaration(declarationNode)
            }

            nameNode.setType(getDeclarationType(declarationNode))
        } else {
            val ambiguousPrefix = name.subList(0, name.size - 1)
            val id = name.last()
            val ambiguousPrefixReclassification = resolveAmbiguousName(ambiguousPrefix)

            when (ambiguousPrefixReclassification.first) {
                ReclassifiedNameType.Package -> {
                    val packageName = ambiguousPrefix.joinToString(".")
                    throw NameResolutionError("In ExpressionName \"${name.joinToString(".")}\", ambiguous prefix \"$packageName\" resolved to a package, which is illegal.")
                }

                ReclassifiedNameType.Type -> {
                    val classDeclaration = ambiguousPrefixReclassification.second!!.type
                    if (classDeclaration.name != "ClassDeclaration") {
                        throw NameResolutionError("Tried to access static field on type \"${classDeclaration.name}\" node called \"${getDeclarationName(classDeclaration)}\". Statics are only supported on classes.")
                    }

                    val fields = getAccessibleStaticMembers(getCurrentClassDeclaration(), classDeclaration)
                        .filter({ it.name == "FieldDeclaration" && getDeclarationName(it) == id })

                    if (fields.isEmpty()) {
                        val typeName = ambiguousPrefix.joinToString(".")
                        throw NameResolutionError("Prefix \"$typeName\" is TypeName, but static field \"$id\" isn't accessible in type \"$typeName\".")
                    }

                    val declarationNode = fields[0]
                    if ("static" !in getModifiers(declarationNode)) {
                        throw NameResolutionError("Tried to access non-static field \"${getDeclarationName(fields[0])}\" from a static context.")
                    }

                    addNameResolution(nameNode, declarationNode)
                    nameNode.setType(getDeclarationType(declarationNode))
                }

                ReclassifiedNameType.Expression -> {
                    val typeDeclarationOfExpression = ambiguousPrefixReclassification.second!!.type

                    val isTypeArray = ambiguousPrefixReclassification.second!!.isArray
                    if (isTypeArray) {
                        if (id != "length") {
                            throw NameResolutionError("Only the length field is defined on arrays. Tried to access field ${id} on the array \"${ambiguousPrefix.joinToString { "." }}\".")
                        }
                        // TODO: Think about what does name node resolve to.
                        // TODO: Have to make sure that this doesn't appear in a place where it can be assigned to

                        if (getCurrentLeftHandSide() != null) {
                            throw TypeCheckingError("While resolving \"${ambiguousPrefix.joinToString(".")}.$id\", detected an \"array.length\" within a \"LeftHandSide\". This implies that you may be writing to final \"length\" property of an array.")
                        }

                        nameNode.setType(Type(CSTNode("int"), false))
                    } else {
                        if (typeDeclarationOfExpression.name in PRIMITIVE_TYPE_NAMES) {
                            throw NameResolutionError("Tried to access field of an expression that evaluates to primitive type \"${typeDeclarationOfExpression.name}\"")
                        }

                        val currentClass = getCurrentClassDeclaration()
                        val members = getAccessibleInstanceMembers(currentClass, typeDeclarationOfExpression)
                            .filter({ it.name == "FieldDeclaration" && getDeclarationName(it) == id })

                        if (members.isEmpty()) {
                            val expressionTypeName = getDeclarationName(typeDeclarationOfExpression)
                            throw NameResolutionError("Prefix \"${ambiguousPrefix.joinToString(".")}\" is ExpressionName which refers to type \"$expressionTypeName\", but failed to find instance field \"$id\" said type.")
                        }

                        val fieldDeclarationNode = members[0]
                        addNameResolution(nameNode, fieldDeclarationNode)
                        nameNode.setType(getDeclarationType(fieldDeclarationNode))
                    }
                }
            }
        }
    }

    enum class ReclassifiedNameType {
        Package,
        Type,
        Expression
    }

    fun getClassFromIODEnvironment(name: String): CSTNode? {
        if (importOnDemandDeclarationEnvironment.contains(name)) {
            return importOnDemandDeclarationEnvironment.find(name)
        }
        return null
    }

    fun colonEquals(typeOfA: Type, typeOfB: Type): Boolean {
        val areBothArraysOrNonArrays = typeOfA.isArray && typeOfB.isArray || !typeOfA.isArray && !typeOfB.isArray

        val baseSuperClass = getClassFromIODEnvironment("Object")
        val cloneableDeclaration = getClassFromIODEnvironment("Cloneable")
        val serializableDeclaration = typesDeclaredInPackages["java.io"].orEmpty()["Serializable"]

        val primitiveTypesWithoutNull = setOf("byte", "short", "int", "char", "boolean")
        if (!typeOfB.isArray && !typeOfA.isArray && typeOfB.type.name in primitiveTypesWithoutNull && typeOfA.type.name in primitiveTypesWithoutNull) {
            if (typeOfB.type.name == "byte") {
                return typeOfA.type.name in listOf("short", "int", "byte")
            }
            if (typeOfB.type.name == "short") {
                return typeOfA.type.name in listOf("short", "int")
            }
            if (typeOfB.type.name == "char") {
                return typeOfA.type.name in listOf("char", "int")
            }
            if (typeOfB.type.name == "int") {
                return typeOfA.type.name in listOf("int")
            }
            if (typeOfB.type.name == "boolean") {
                return typeOfA.type.name in listOf("boolean")
            }
        } else if (typeOfB.isArray && (typeOfA.type == baseSuperClass || typeOfA.type == serializableDeclaration || typeOfA.type == cloneableDeclaration) && !typeOfA.isArray) {
            // Goes in here for reference and primitive arrays.
            return true
        } else if (typeOfB.isArray && typeOfB.type.name in primitiveTypesWithoutNull) {
            if (typeOfA.type.name == typeOfB.type.name && typeOfA.isArray) {
                return true
            }
        } else if (typeOfB.type.name == "InterfaceDeclaration" && areBothArraysOrNonArrays) {
            if (isSubInterface(typeOfB.type, typeOfA.type)) {
                return true
            }
        } else if (typeOfB.type.name == "ClassDeclaration" && areBothArraysOrNonArrays) {
            if (typeOfA.type.name == "InterfaceDeclaration") {
                if (isInterfaceImplementedByClass(typeOfA.type, typeOfB.type)) {
                    return true
                }
            } else if (typeOfA.type.name == "ClassDeclaration") {
                if (isSubClass(typeOfB.type, typeOfA.type)) {
                    return true
                }
            }
        } else if (typeOfB.type.name == "NULL") {
            return typeOfA.isReference()
        }

        return false
    }

    fun lookupTypeName(typeName: String): CSTNode? {
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

    fun resolveAmbiguousName(identifiers: List<String>) : Pair<ReclassifiedNameType, Type?> {
        if (identifiers.size == 1) {
            val identifier = identifiers[0]
            val declarationTypes = setOf("LocalVariableDeclaration", "FormalParameter", "FieldDeclaration")

            val declarationLookup = environment.findAll({ (name, node) -> name == identifier && node.name in declarationTypes })
            if (declarationLookup.isNotEmpty()) {
                if (declarationLookup[0].name == "FieldDeclaration") {
                    validateUsageOfFieldDeclaration(declarationLookup[0])
                }

                val declarationType = getDeclarationType(declarationLookup[0])
                return Pair(ReclassifiedNameType.Expression, declarationType)
            }

            val typeDeclaration = lookupTypeName(identifier)
            if (typeDeclaration != null) {
                return Pair(ReclassifiedNameType.Type, Type(typeDeclaration, false))
            }

            return Pair(ReclassifiedNameType.Package, null)
        }

        val ambiguousPrefix = identifiers.subList(0, identifiers.size - 1)
        val ambiguousPrefixReclassification = resolveAmbiguousName(ambiguousPrefix)

        val identifier = identifiers.last()

        when (ambiguousPrefixReclassification.first) {
            ReclassifiedNameType.Package -> {
                val packageName = ambiguousPrefix.joinToString(".")

                if (typesDeclaredInPackages.containsKey(packageName) && typesDeclaredInPackages[packageName].orEmpty().contains(identifier)) {
                    val typeDeclaration = typesDeclaredInPackages[packageName].orEmpty()[identifier]!!
                    return Pair(ReclassifiedNameType.Type, Type(typeDeclaration, false))
                }
                return Pair(ReclassifiedNameType.Package, null)
            }

            ReclassifiedNameType.Type -> {
                val classDeclaration = ambiguousPrefixReclassification.second!!.type

                if (classDeclaration.name != "ClassDeclaration") {
                    throw NameResolutionError("Tried to access static field or method on type \"${classDeclaration.name}\" node called \"${getDeclarationName(classDeclaration)}\". Statics are only supported on classes.")
                }

                val members = getClassInheritedDescendants(classDeclaration, { (it.name == "FieldDeclaration" || it.name == "MethodDeclaration") && "static" in getModifiers(it) })

                val memberLookup = members.filter({ getDeclarationName(it) == identifier })
                if (memberLookup.isNotEmpty()) {
                    val memberType = getDeclarationType(memberLookup[0])
                    return Pair(ReclassifiedNameType.Expression, memberType)
                }

                throw NameResolutionError("Prefix \"$ambiguousPrefix\" is TypeName, but \"$identifier\" doesn't exist in the type.")
            }

            ReclassifiedNameType.Expression -> {
                val expressionType = ambiguousPrefixReclassification.second!!
                val currentClass = getCurrentClassDeclaration()

                if (expressionType.isArray) {
                    throw NameResolutionError("Ambiguous name resolved to an array.")
                } else {
                    val expressionTypeDeclaration = expressionType.type

                    if (expressionTypeDeclaration.name in PRIMITIVE_TYPE_NAMES) {
                        throw NameResolutionError("Tried to access member of a primitive type \"${expressionTypeDeclaration.name}\"")
                    }

                    val members = getAccessibleInstanceMembers(currentClass, expressionTypeDeclaration)
                        .filter({ it.name == "FieldDeclaration" })



                    val memberLookup = members.filter({ getDeclarationName(it) == identifier })
                    if (memberLookup.isNotEmpty()) {
                        val member = memberLookup[0]
                        return Pair(ReclassifiedNameType.Expression, getDeclarationType(member))
                    }

                    val expressionTypeName = getDeclarationName(expressionTypeDeclaration)
                    throw NameResolutionError("Prefix \"$ambiguousPrefix\" is ExpressionName which refers to type \"$expressionTypeName\", but failed to find instance method or field \"$identifier\" said type.")
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
            environment.pushScope()
            this.visit(methodHeader)
            environment.popScope()
        })

        val ownFieldDeclarations = classBody.getDescendants("FieldDeclaration")

        ownFieldDeclarations.forEach({ fieldDeclaration ->
            val type = fieldDeclaration.getChild("Type")
            environment.pushScope()
            this.visit(type)
            environment.popScope()
        })

        val constructorDeclarations = node.getDescendants("ConstructorDeclaration")

        constructorDeclarations.forEach({ constructorDeclaration ->
            val constructorDeclarator = constructorDeclaration.getChild("ConstructorDeclarator")
            environment.pushScope()
            this.visit(constructorDeclarator)
            environment.popScope()
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
         * If a class hasn't implemented implemented all its interface methods, then it must be abstract.
         * A class that contains any abstract methods must be abstract.
         */

        fun areInterfaceMethodsImplemented(classDeclaration: CSTNode, descendantConcreteMethods: List<CSTNode>, descendantUnimplementedInterfaceMethods: List<CSTNode>): Boolean {
            val implementedInterfaces = classDeclaration.getChild("InterfacesOpt").getDescendants("Name").map({ it.getDeclaration() })
            val allInterfaceInheritedMethods = implementedInterfaces.flatMap({ getInterfaceInheritedMethods(it, getDeclarationName(classDeclaration), mutableSetOf()).toSet() })
            val potentiallyUnimplementedInterfaceMethods = allInterfaceInheritedMethods + descendantUnimplementedInterfaceMethods

            val abstractMethods = classDeclaration.getDescendants("MethodDeclaration").filter({ "abstract" in getModifiers(it) })
            val ownConcreteMethods = classDeclaration.getDescendants("MethodDeclaration").filter({ "static" !in getModifiers(it) && "abstract" !in getModifiers(it) })
            val concreteMethods = ownConcreteMethods + descendantConcreteMethods

            val abstractMethodsThatArentConcretelyImplemented = abstractMethods.filter({ abstractMethod ->
                concreteMethods.filter({ concreteMethod ->
                    canMethodsReplaceOneAnother(abstractMethod.getChild("MethodHeader"), concreteMethod.getChild("MethodHeader"))
                }).isEmpty()
            })

//            val concreteMethodsNotUsedToImplementAbstractMethods = concreteMethods.filter({ concreteMethod ->
//                abstractMethods.filter({ abstractMethod ->
//                    canMethodsReplaceOneAnother(abstractMethod.getChild("MethodHeader"), concreteMethod.getChild("MethodHeader"))
//                }).isEmpty()
//            })

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

            val remainingUnimplementedAbstractInterfaceMethods = unshadowedPotentiallyUnimplementedInterfaceMethods.filter({ unimplementedInterfaceMethod ->
                concreteMethods.filter({ concreteMethod ->
                    if (canMethodsReplaceOneAnother(concreteMethod.getChild("MethodHeader"), unimplementedInterfaceMethod.getChild("MethodHeader"))) {
                        if ("protected" in getModifiers(concreteMethod) && "public" in getModifiers(unimplementedInterfaceMethod)) {
                            throw ConcreteMethodImplementsPublicMethodWithProtectedVisibility(className, getDeclarationName(concreteMethod))
                        }
                        true
                    } else {
                        false
                    }
                }).isEmpty()
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
                        canMethodsReplaceOneAnother(concreteMethod.getChild("MethodHeader"), unimplementedInterfaceMethod.getChild("MethodHeader"))
                    }).isEmpty()
                })

                return finalUnimplementedAbstractInterfaceMethods.isEmpty()
            } else {
//                val remainingConcreteMethods = concreteMethods.filter({ concreteMethod ->
//                    unshadowedPotentiallyUnimplementedInterfaceMethods.filter({ unimplementedInterfaceMethod ->
//                        canMethodsReplaceOneAnother(concreteMethod.getChild("MethodHeader"), unimplementedInterfaceMethod.getChild("MethodHeader"))
//                    }).isEmpty()
//                })

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
         * TEST
         *
         * A protected method must not implement a
         */


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

            environment.pushScope()
            if (methodDeclaration.name == "MethodDeclaration") {
                val methodName = getDeclarationName(methodDeclaration)
                environment.push(methodName, methodDeclaration)
            }
            this.visit(methodDeclaration)
            environment.popScope()
        }

        /**
         * Add Fields Names to Env
         */
        allFieldDeclarations.forEach(addFieldDeclarationToEnvironment)

        /**
         * Add Method Names to Env
         */
        if ("abstract" in getModifiers(node)) {
            getAllMethodsOfAllInterfacesImplementedInClassHierarchyOf(node).reversed().forEach(addMethodDeclarationToEnvironment)
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
            environment.pushScope()
            this.visit(methodHeader)
            environment.popScope()
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
            val inheritedAndFinalMethodDeclarations = allMethodDeclarations.filter({ inheritedOrOwnMethod ->
                inheritedPredicate(inheritedOrOwnMethod) &&
                abstractMethodDeclarations.filter({ ownMethod ->
                    inheritedOrOwnMethod !== ownMethod && ownPredicate(ownMethod)
                }).isNotEmpty()
            })
            abstractMethodDeclarations.forEach({interfaceMethod ->
                inheritedAndFinalMethodDeclarations.forEach(fun (inheritedInterfaceMethod: CSTNode) {
                    if (canMethodsReplaceOneAnother(interfaceMethod.getChild("MethodHeader"), inheritedInterfaceMethod.getChild("MethodHeader"))) {
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
        ownMethodsMustNotReplaceInheritedMethods({ "public" in getModifiers(it)}, { "protected" in getModifiers(it) })
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
        currentFieldDeclaration = node
        super.visitFieldDeclaration(node)

        // Type checking for field declaration.
        if (node.children[2].name == "VariableDeclarator") {
            val variableDeclarator = node.getChild("VariableDeclarator")
            val fieldType = getDeclarationType(node)
            val expressionType = variableDeclarator.getDescendant("Expression").getType()
            if (!colonEquals(fieldType, expressionType)) {
                throw TypeCheckingError("FieldDeclaration initializer is not type assignable to the field type.")
            }
        }
        currentFieldDeclaration = null
    }

    override fun visitBlock(node: CSTNode) {
        environment.pushScope()
        super.visitBlock(node)
        environment.popScope()
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
        if (!colonEquals(localVariableType, expressionType)) {
            throw TypeCheckingError("LocalVariableDeclaration initializer is not type assignable to the variable type.")
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

        if ("protected" in getModifiers(selectedConstructorDeclaration)) {
            val currentClassDeclaration = getCurrentClassDeclaration()
            val isConstructorInSamePackage = getPackageOfType(classBeingInstantiated) == getPackageOfType(currentClassDeclaration)

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
                this.visit(node.getChild("UnaryExpressionNotPlusMinus"))
            }

            "Expression" -> {
                val expressionNode = node.getChild("Expression")
                val nameNode = expressionNode.getDescendant("Name")
                resolveTypeName(nameNode)

                expressionNode.setType(nameNode.getType())

                this.visit(node.getChild("UnaryExpressionNotPlusMinus"))
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

                if (colonEquals(typeOfExpression, typeOfUnaryExpressionNotPlusMinus)) {
                    node.setType(typeOfExpression)
                } else if (colonEquals(typeOfUnaryExpressionNotPlusMinus, typeOfExpression)) {
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

                if (colonEquals(typeOfCast, typeOfUnaryExpressionNotPlusMinus)) {
                    node.setType(typeOfCast)
                } else if (colonEquals(typeOfUnaryExpressionNotPlusMinus, typeOfCast)) {
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
                } else if (colonEquals(typeOfCast, typeOfUnaryExpression)) {
                    node.setType(typeOfCast)
                } else if (colonEquals(typeOfUnaryExpression, typeOfCast)) {
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

            if (!colonEquals(typeOfRelationalExpression, typeOfAdditiveExpression) && !colonEquals(typeOfAdditiveExpression, typeOfRelationalExpression)) {
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

            if (!colonEquals(typeOfRelationalExpression, typeOfReferenceType) && !colonEquals(typeOfReferenceType, typeOfRelationalExpression)) {
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

    fun getCurrentLeftHandSide(): CSTNode? {
        return currentLeftHandSide
    }

    /**
     * ExpressionName:
     * As the left-hand operand of an assignment operator (§15.26)
     */
    override fun visitLeftHandSide(node: CSTNode) {
        currentLeftHandSide = node
        try {
            val nameNode = node.getChild("Name")
            resolveExpressionName(nameNode)
        } catch (ex: FoundNoChild) {
        }

        super.visitLeftHandSide(node)
        node.setType(node.children[0].getType())
        currentLeftHandSide = null
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
            val name = nameNode.getDescendants("IDENTIFIER").map({ it.lexeme })

            if (name.size == 1) {
                val methodName = name[0]
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
                node.setType(getDeclarationType(methodDeclarationNode))
            } else {
                val methodName = name.last()
                val ambiguousPrefix = name.subList(0, name.size - 1)
                val ambiguousPrefixResolution = resolveAmbiguousName(ambiguousPrefix)

                when (ambiguousPrefixResolution.first) {
                    ReclassifiedNameType.Package -> {
                        val packageName = ambiguousPrefix.joinToString(".")
                        throw NameResolutionError("Tried to call method \"$methodName\" on package \"$packageName\"")
                    }

                    ReclassifiedNameType.Type -> {
                        val resolvedType = ambiguousPrefixResolution.second!!
                        val typeDeclaration = resolvedType.type
                        val currentClass = getCurrentClassDeclaration()

                        if (typeDeclaration.name != "ClassDeclaration") {
                            throw TypeCheckingError("Tried to call static method \"$methodName\" on a non-class ${typeDeclaration.name}")
                        }

                        val accessibleStaticMethods = getAccessibleStaticMembers(currentClass, typeDeclaration)
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

                    ReclassifiedNameType.Expression -> {
                        val resolvedType = ambiguousPrefixResolution.second!!
                        val resolvedTypeDeclaration = resolvedType.type
                        val currentClass = getCurrentClassDeclaration()

                        if (resolvedType.isNumeric() || resolvedType.isBoolean() || resolvedType.isNull()) {
                            throw TypeCheckingError("Tried to invoke method \"$methodName\" on an expression with a primitive type \"${resolvedTypeDeclaration.name}\"")
                        }

                        val accessibleInstanceMethods = getAccessibleInstanceMembers(currentClass, resolvedTypeDeclaration)
                            .filter({ it.name in listOf("MethodDeclaration", "AbstractMethodDeclaration") })

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
                            throw TypeCheckingError("Tried to invoke instance method \"${getDeclarationName(resolvedTypeDeclaration)}.$methodName\" from class ${getDeclarationName(currentClass)}, but none found.")
                        }

                        addNameResolution(nameNode, resolvedInstanceMethod)
                        node.setType(getDeclarationType(resolvedInstanceMethod))
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

            val accessibleInstanceMethods = getAccessibleInstanceMembers(currentClass, typeDeclaration)
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
        // Don't visit.
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
            "STRING" -> lookupTypeName("String")!!
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

        if (!colonEquals(leftHandSideType, assignmentExpressionType)) {
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

            if (!colonEquals(typeOfEqualityExpression, typeOfRelationalExpression) && !colonEquals(typeOfRelationalExpression, typeOfEqualityExpression)) {
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

            val stringDeclaration = getClassFromIODEnvironment("String")

            if (typeOfAdditiveExpression.type !== stringDeclaration && typeOfMultiplicativeExpression.type !== stringDeclaration) {
                if (!typeOfAdditiveExpression.isNumeric() || !typeOfMultiplicativeExpression.isNumeric()) {
                    throw TypeCheckingError("In AdditiveExpression, tried to add non-numeric values where neither of them are strings")
                }

                node.setType(Type(CSTNode("int"), false))
            } else {
                if (typeOfAdditiveExpression.type === stringDeclaration && typeOfMultiplicativeExpression.type.name == "void" ||
                    typeOfMultiplicativeExpression.type == stringDeclaration && typeOfAdditiveExpression.type.name == "void") {
                    throw TypeCheckingError("In AdditiveExpression, tried to add a void type to a String.")
                }
                node.setType(Type(stringDeclaration, false))
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
        val currentMethodReturnType = getDeclarationType(currentMethodDeclaration!!)

        val expressionOpt = node.getChild("ExpressionOpt")

        if (currentMethodReturnType.type.name == "void") {
            if (expressionOpt.children.size != 0) {
                throw TypeCheckingError("Tried to return from a void method.")
            }
        } else {
            if (expressionOpt.children.size == 0) {
                throw TypeCheckingError("Tried to return void from a non-void method.")
            }
            val returnExpressionType = expressionOpt.getChild("Expression").getType()
            if (!colonEquals(currentMethodReturnType, returnExpressionType)) {
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

            if (getCurrentLeftHandSide() != null) {
                throw TypeCheckingError("In \"FieldAccess\", detected an \"array.length\" within a \"LeftHandSide\". This implies that you may be writing to final \"length\" property of an array.")
            }

            node.setType(Type(CSTNode("int"), false))
        } else {
            val currentClass = getCurrentClassDeclaration()
            val accessibleFields = getAccessibleInstanceMembers(currentClass, typeOfPrimary.type)
                .filter({ it.name == "FieldDeclaration" })

            val matchingField = accessibleFields.find({ getDeclarationName(it) == fieldName })
            if (matchingField == null) {
                throw TypeCheckingError("No field \"$fieldName\" found in class ${getDeclarationName(typeOfPrimary.type)}")
            }

            node.setType(getDeclarationType(matchingField))
        }
    }

    override fun visitConstructorDeclaration(node: CSTNode) {
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
        super.visitForStatement(node)

        val expressionOpt = node.getChild("ExpressionOpt")

        if (expressionOpt.children.size != 0) {
            val typeOfExpression = expressionOpt.getChild("Expression").getType()
            if (!typeOfExpression.isBoolean()) {
                throw TypeCheckingError("Detected a non-boolean expression inside ForStatement")
            }
        }
    }

    override fun visitForStatementNoShortIf(node: CSTNode) {
        super.visitForStatementNoShortIf(node)

        val expressionOpt = node.getChild("ExpressionOpt")

        if (expressionOpt.children.size != 0) {
            val typeOfExpression = expressionOpt.getChild("Expression").getType()
            if (!typeOfExpression.isBoolean()) {
                throw TypeCheckingError("Detected a non-boolean expression inside ForStatementNoShortIf")
            }
        }
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
                    val qualifiedTypeName = "$packageName.$typeName"
                    if (doesPackageExist(qualifiedTypeName)) {
                        throw EitherPackageNameOrQualifiedType(qualifiedTypeName)
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
            visitPackages(NameResolutionVisitor.ResolutionDepth.MemberDeclaration)
            visitPackages(NameResolutionVisitor.ResolutionDepth.All)

            return visitor.getNameResolution()
        }
    }
}
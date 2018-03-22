package oj.models

import oj.nameresolver.isClassOrInterfaceDeclaration

class PackageManager(
    val packages: Map<String, List<CSTNode>>
) {
    open class PackageManagerError(reason: String): Exception(reason)
    class DetectedTwoTypesWithSameNameInSamePackage(packageName: String, typeName: String)
        : PackageManagerError("Detected two types named \"$typeName\" within package \"$packageName\".")
    class TriedToGetTypesOfNonExistentPackage(packageName: String)
        : PackageManagerError("Tried to get Types declared in non-existent package \"$packageName\"")


    private val typesDeclaredInPackages: Map<String, Map<String, CSTNode>>

    init {
        typesDeclaredInPackages = packages.mapValues({(packageName, compilationUnits) ->
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
    }

    fun doesPackageExist(packageName: String): Boolean {
        for ((pkgName, _) in packages) {
            if (pkgName.startsWith(packageName + ".") || pkgName == packageName) {
                return true
            }
        }

        return false
    }

    fun getTypesDeclaredInPackage(packageName: String) : Map<String, CSTNode> {
        val typesInPackage = typesDeclaredInPackages[packageName]
        if (typesInPackage == null) {
            if (doesPackageExist(packageName)) {
                return mapOf()
            }

            throw TriedToGetTypesOfNonExistentPackage(packageName)
        }

        return typesInPackage
    }

    fun getJavaLangType(typeName: String): CSTNode? {
        if (doesPackageExist("java.lang")) {
            return typesDeclaredInPackages["java.lang"].orEmpty()[typeName]
        }

        return null
    }

    class TriedToGetPackageOfNonType(nodeType: String): PackageManagerError("Tried to access package of a non-type CSTNode: \"$nodeType\"")
    class PackageNotFoundForType(typeName: String): PackageManagerError("Package not found for type \"$typeName\"")

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
            throw PackageManagerError("Tried to check sub-class relationship on non-classes: ${child.name} ${potentialAncestor.name}")
        }

        val baseSuperClass = getJavaLangType("Object")
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
            throw PackageManagerError("Tried to check sub-interface relationship on non-interface child: ${child.name}")
        }

        val baseSuperClass = getJavaLangType("Object")
        if (potentialAncestor == baseSuperClass) {
            return true
        }

        if (potentialAncestor.name != "InterfaceDeclaration") {
            throw PackageManagerError("Tried to check sub-interface relationship on non-interface potential ancestor: ${potentialAncestor.name}")
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
            throw PackageManagerError("Expected currentClass and otherClass to be ClassDeclarations")
        }

        val isInSamePackage = getPackageOfType(currentClass) == getPackageOfType(otherClass)

        return isSubClass(currentClass, otherClass) || isInSamePackage
    }

    fun getAccessibleInstanceMembers(currentClass: CSTNode, typeToInspect: CSTNode): List<CSTNode> {
        if (currentClass.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to call method with currentClass.name == \"${currentClass.name}\" != \"ClassDeclaration\"")
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
                    val baseSuperClass = getJavaLangType("Object")

                    if (baseSuperClass == null || currentClassToInspect === baseSuperClass) {
                        break
                    }

                    currentClassToInspect = baseSuperClass
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
            throw PackageManagerError("Tried to call \"getAccessibleInstanceMembers\" with a non-interface and non-class typeToInspect: \"${typeToInspect.name}\"")
        }
    }

    fun getAccessibleStaticMembers(currentClass: CSTNode, classToInspect: CSTNode): List<CSTNode> {
        if (currentClass.name != "ClassDeclaration" || classToInspect.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to call getAccessibleStaticMembers on a non-class arguments.")
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
                val baseSuperClass = getJavaLangType("Object")

                if (baseSuperClass == null || currentClassToInspect === baseSuperClass) {
                    break
                }

                currentClassToInspect = baseSuperClass
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

    open class HierarchyCheckingError(reason: String) : PackageManagerError(reason)
    class InterfaceHierarchyIsCyclic(interfaceName: String, firstDuplicateInterface: String) : HierarchyCheckingError("Detected a duplicate interface \"$firstDuplicateInterface\" in interface \"$interfaceName\"'s hierarchy.")

    fun getInterfaceInheritedMethods(interfaceDeclaration: CSTNode, typeName: String = getDeclarationName(interfaceDeclaration), visited: MutableSet<CSTNode> = mutableSetOf()): List<CSTNode> {
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
            val baseSuperClass = getJavaLangType("Object")
            if (baseSuperClass != null) {
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
            throw PackageManagerError("Tried to call getAllInterfacesImplementedByClass on a non-class: ${classDeclaration.name}")
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

    fun getClassInheritedDescendants(classDeclaration: CSTNode, predicate: (CSTNode) -> Boolean) : List<CSTNode> {
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
                val baseSuperClass = getJavaLangType("Object")
                if (baseSuperClass != null) {
                    val baseSuperClassClassBody = baseSuperClass.getChild("ClassBody")
                    baseSuperClassClassBody.getDescendants(predicate)
                } else {
                    listOf()
                }
            } + descendantNodes
            )
    }

    fun colonEquals(typeOfA: Type, typeOfB: Type): Boolean {
        val areBothArraysOrNonArrays = typeOfA.isArray && typeOfB.isArray || !typeOfA.isArray && !typeOfB.isArray

        val baseSuperClass = getJavaLangType("Object")
        val cloneableDeclaration = getJavaLangType("Cloneable")
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

}
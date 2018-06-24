package oj.models

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

    fun getJavaIOType(typeName: String): CSTNode? {
        if (doesPackageExist("java.io")) {
            return typesDeclaredInPackages["java.io"].orEmpty()[typeName]
        }

        return null
    }

    class TriedToGetPackageOfNonType(nodeType: String): PackageManagerError("Tried to access package of a non-type CSTNode: \"$nodeType\"")
    class PackageNotFoundForType(typeName: String): PackageManagerError("Package not found for type \"$typeName\"")

    fun getPackageNameOfType(node: CSTNode): String {
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

        val extendedInterfaces = child.getChild("ExtendsInterfacesOpt").getDescendants("Name").map({ it.getDeclaration() })

        if (extendedInterfaces.isEmpty()) {
            return false
        }

        if (extendedInterfaces.any({ extendedInterface -> isSubInterface(extendedInterface, potentialAncestor)})) {
            return true
        }

        return false
    }

    fun canMembersReplaceOneAnother(member1: CSTNode, member2: CSTNode): Boolean {
        if (member1.name == member2.name) {
            if (member1.name == "FieldDeclaration") {
                return getDeclarationName(member1) == getDeclarationName(member2)
            }

            if (member1.name == "MethodDeclaration") {
                return canMethodsReplaceOneAnother(
                    member2.getChild("MethodHeader"),
                    member1.getChild("MethodHeader")
                )
            }
        }

        return false
    }

    /**
     * Doesn't return the members of interfaces in its class heirarchy.
     */
    fun getClassAccessibleMembers(currentClass: CSTNode, classToInspect: CSTNode, shouldAccessInstanceMembers: Boolean): List<CSTNode> {
        if (currentClass.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to call method with currentClass.name == \"${currentClass.name}\" != \"ClassDeclaration\"")
        }

        val canAddProtectedMethods = isSubClass(classToInspect, currentClass)

        var accessibleMembers = mutableListOf<CSTNode>()
        var inaccessibleMembers = mutableListOf<CSTNode>()

        var currentClassToInspect: CSTNode? = classToInspect

        while (currentClassToInspect != null) {
            val classBody = currentClassToInspect.getChild("ClassBody")
            val members = classBody
                .getDescendants({ it.name in listOf("FieldDeclaration", "MethodDeclaration", "AbstractMethodDeclaration") })
                .filter({ if (shouldAccessInstanceMembers) "static" !in getModifiers(it) else "static" in getModifiers(it) })

            val publicMembers = members.filter({ member -> "public" in getModifiers(member) })
            accessibleMembers.addAll(publicMembers)

            var protectedMembers = members.filter({ member -> "protected" in getModifiers(member) })

            if (getPackageNameOfType(currentClass) == getPackageNameOfType(currentClassToInspect)) {
                accessibleMembers.addAll(protectedMembers)
            } else if (isSubClass(currentClass, currentClassToInspect)) {
                protectedMembers = protectedMembers.filter({ protectedMember ->
                    inaccessibleMembers.filter({ inaccessibleMember ->
                        canMembersReplaceOneAnother(inaccessibleMember, protectedMember)
                    }).isEmpty()
                })
                if (shouldAccessInstanceMembers) {
                    if (canAddProtectedMethods) {
                        accessibleMembers.addAll(protectedMembers)
                    } else {
                        inaccessibleMembers.addAll(protectedMembers)
                    }
                } else {
                    accessibleMembers.addAll(protectedMembers)
                }
            } else {
                inaccessibleMembers.addAll(protectedMembers)
            }

            currentClassToInspect = getSuperClass(currentClassToInspect)
        }

        return accessibleMembers
    }

    fun getAccessibleInstanceMembers(currentClass: CSTNode, typeToInspect: CSTNode): List<CSTNode> {
        if (currentClass.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to call method with currentClass.name == \"${currentClass.name}\" != \"ClassDeclaration\"")
        }

        return if (typeToInspect.name == "ClassDeclaration") {
            val classToInspect = typeToInspect
            getClassAccessibleMembers(currentClass, classToInspect, true) + getAllMethodsOfAllInterfacesImplementedInClassHierarchyOf(classToInspect)
        } else if (typeToInspect.name == "InterfaceDeclaration"){
            getInterfaceInheritedMethods(typeToInspect)
        } else {
            throw PackageManagerError("Tried to call \"getAccessibleInstanceMembers\" with a non-interface and non-class typeToInspect: \"${typeToInspect.name}\"")
        }
    }

    fun getAccessibleStaticMembers(currentClass: CSTNode, classToInspect: CSTNode): List<CSTNode> {
        return getClassAccessibleMembers(currentClass, classToInspect, false)
    }

    open class HierarchyCheckingError(reason: String) : PackageManagerError(reason)
    class InterfaceHierarchyIsCyclic(interfaceName: String, firstDuplicateInterface: String) : HierarchyCheckingError("Detected a duplicate interface \"$firstDuplicateInterface\" in interface \"$interfaceName\"'s hierarchy.")

    private var baseInterfaceMethods: List<CSTNode>? = null

    fun getBaseInterfaceMethods(): List<CSTNode> {
        if (baseInterfaceMethods != null) {
            return baseInterfaceMethods!!
        }

        val baseSuperClass = getJavaLangType("Object")
        if (baseSuperClass == null) {
            baseInterfaceMethods = listOf()
            return baseInterfaceMethods!!
        }

        val baseSuperClassClassBody = baseSuperClass.getChild("ClassBody")
        val baseSuperClassMethodDeclarations = baseSuperClassClassBody.getDescendants("MethodDeclaration")
        val baseSuperInterfaceAbstractMethodDeclarations = baseSuperClassMethodDeclarations
            .filter({ "public" in getModifiers(it) })
            .map({ methodDeclaration ->
                val methodHeader = methodDeclaration.getChild("MethodHeader")
                val semicolon = CSTNode(";", "", mutableListOf())
                val abstractMethodDeclaration = CSTNode("AbstractMethodDeclaration", "", mutableListOf(methodHeader, semicolon))
                abstractMethodDeclaration
            })

        baseInterfaceMethods = baseSuperInterfaceAbstractMethodDeclarations
        return baseInterfaceMethods!!
    }

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
            extendedAbstractMethods += getBaseInterfaceMethods()
        }

        return abstractMethodDeclarations + extendedAbstractMethods
    }

    /**
     * Gets all the methods from all the interfaces implemented by this class or any classes in its hierarchy
     * Doesn't include java.lang.Object public methods
     */
    fun getAllMethodsOfAllInterfacesImplementedInClassHierarchyOf(classDeclaration: CSTNode): List<CSTNode> {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to call getAllInterfacesImplementedByClass on a non-class: ${classDeclaration.name}")
        }


        val extendedInterfaces = classDeclaration.getChild("InterfacesOpt").getDescendants("Name").map({ it.getDeclaration() })
        val abstractMethods = extendedInterfaces.flatMap({ getInterfaceInheritedMethods(it) - getBaseInterfaceMethods() })

        val superClass = getSuperClass(classDeclaration)
        val superClassAbstractMethods =
            if (superClass != null) {
                getAllMethodsOfAllInterfacesImplementedInClassHierarchyOf(superClass)
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

    private val classOverriddenStaticFieldsMap = mutableMapOf<CSTNode, List<CSTNode>>()
    fun getClassOverriddenStaticFields(classDeclaration: CSTNode): List<CSTNode> {
        return getClassOverriddenMembers(
            classDeclaration,
            { "FieldDeclaration" == it.name && "static" in getModifiers(it) },
            { a, b -> getDeclarationName(a) == getDeclarationName(b) },
            classOverriddenStaticFieldsMap
        )
    }

    private val classOverriddenInstanceFieldsMap = mutableMapOf<CSTNode, List<CSTNode>>()
    fun getClassOverriddenInstanceFields(classDeclaration: CSTNode): List<CSTNode> {
        return getClassOverriddenMembers(
            classDeclaration,
            { "FieldDeclaration" == it.name && "static" !in getModifiers(it) },
            { a, b -> getDeclarationName(a) == getDeclarationName(b) },
            classOverriddenInstanceFieldsMap
        )
    }

    private val classOverriddenStaticMethodsMap = mutableMapOf<CSTNode, List<CSTNode>>()
    fun getClassOverriddenStaticMethods(classDeclaration: CSTNode): List<CSTNode> {
        return getClassOverriddenMembers(
            classDeclaration,
            { "MethodDeclaration" == it.name && "static" in getModifiers(it) },
            { a, b -> canMethodsReplaceOneAnother(a.getChild("MethodHeader"), b.getChild("MethodHeader"))},
            classOverriddenStaticMethodsMap
        )
    }

    private val classOverriddenInstanceMethodsMap = mutableMapOf<CSTNode, List<CSTNode>>()
    fun getClassOverriddenInstanceMethods(classDeclaration: CSTNode): List<CSTNode> {
        return getClassOverriddenMembers(
            classDeclaration,
            { "MethodDeclaration" == it.name && "static" !in getModifiers(it) },
            { a, b -> canMethodsReplaceOneAnother(a.getChild("MethodHeader"), b.getChild("MethodHeader"))},
            classOverriddenInstanceMethodsMap
        )
    }

    private fun getClassOverriddenMembers(
        classDeclaration: CSTNode,
        predicate: (CSTNode) -> Boolean,
        areEqual: (CSTNode, CSTNode) -> Boolean,
        memoizedMap: MutableMap<CSTNode, List<CSTNode>>
    ) : List<CSTNode> {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Expected a class declaration, but found ${classDeclaration.name} instead.")
        }

        if (classDeclaration in memoizedMap) {
            return memoizedMap[classDeclaration]!!
        }

        val classBody = classDeclaration.getChild("ClassBody")
        val ownFieldDeclarations = classBody.getDescendants(predicate)

        val superOptNode = classDeclaration.getChild("SuperOpt")
        val superNode = if (superOptNode.children.size == 1) superOptNode.getChild("Super") else null

        val allFields =
            if (superNode != null) {
                getClassOverriddenMembers(superNode.getDescendant("Name").getDeclaration(), predicate, areEqual, memoizedMap)
            } else {
                val baseSuperClass = getJavaLangType("Object")
                if (baseSuperClass != null) {
                    val baseSuperClassClassBody = baseSuperClass.getChild("ClassBody")
                    baseSuperClassClassBody.getDescendants(predicate)
                } else {
                    mutableListOf()
                }
            }.toMutableList()

        ownFieldDeclarations.forEach({ ownField ->
            val allFieldIndex = allFields.indexOfFirst({ areEqual(it, ownField) })

            if (allFieldIndex == -1) {
                allFields.add(ownField)
            } else {
                allFields[allFieldIndex] = ownField
            }
        })

        memoizedMap[classDeclaration] = allFields

        return allFields
    }

    fun getSuperClass(classDeclaration: CSTNode): CSTNode? {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to get super class of non-class CSTNode of type ${classDeclaration.name}")
        }

        val superOptNode = classDeclaration.getChild("SuperOpt")
        val superNode = if (superOptNode.children.size == 1) superOptNode.getChild("Super") else null

        if (superNode != null) {
            return superNode.getDescendant("Name").getDeclaration()
        }

        val baseClassDeclaration = getJavaLangType("Object")
        if (classDeclaration == baseClassDeclaration) {
            return null
        }

        return baseClassDeclaration
    }

    fun colonEquals(typeOfA: Type, typeOfB: Type): Boolean {
        val areBothArraysOrNonArrays = typeOfA.isArray && typeOfB.isArray || !typeOfA.isArray && !typeOfB.isArray

        val baseSuperClass = getJavaLangType("Object")
        val cloneableDeclaration = getJavaLangType("Cloneable")
        val serializableDeclaration = typesDeclaredInPackages["java.io"].orEmpty()["Serializable"]

        if (!typeOfB.isArray && !typeOfA.isArray && typeOfB.type.name in primitiveTypes && typeOfA.type.name in primitiveTypes) {
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
        } else if (typeOfB.isArray && typeOfB.type.name in primitiveTypes) {
            if (typeOfA.type.name == typeOfB.type.name && typeOfA.isArray) {
                return true
            }
        } else if (typeOfB.type.name == "InterfaceDeclaration" && areBothArraysOrNonArrays) {
            if (typeOfA.type.name == "InterfaceDeclaration") {
                if (isSubInterface(typeOfB.type, typeOfA.type)) {
                    return true
                }
            } else if (typeOfA.type == baseSuperClass) {
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

    fun isString(typeOfNode: Type): Boolean {

        if (typeOfNode.isArray) {
            return false
        }

        if (!typeOfNode.isReference()) {
            return false
        }

        if ("java.lang" == getPackageNameOfType(typeOfNode.type)) {
            return true
        }

        return false
    }

    fun getQualifiedDeclarationName(node: CSTNode): String {
        if (node.name !in setOf("ClassDeclaration", "InterfaceDeclaration")) {
            throw PackageManagerError("Tried to get qualified name of yet unsupported CSTNode type ${node.name}")
        }

        val packageName = getPackageNameOfType(node)
        val typeName = getDeclarationName(node)

        return if (packageName.isEmpty()) typeName else "$packageName.$typeName"

    }

    fun getClassOrInterfaceDeclarationLabel(classOrInterface: CSTNode): String {
        if (classOrInterface.name !in setOf("ClassDeclaration", "InterfaceDeclaration")) {
            throw PackageManagerError("Tried to get label of CSTNode \"${classOrInterface.name}\", which is neither a \"ClassDeclaration\" nor an \"InterfaceDeclaration\"")
        }

        val packageName = getPackageNameOfType(classOrInterface)
        val packageLabel =
            if (packageName.isEmpty()) {
                "#"
            } else {
                packageName.replace('.', '#')
            }

        return "$packageLabel#${getDeclarationName(classOrInterface)}"
    }

    private val classMemberLabelMap = mutableMapOf<CSTNode, String>()

    fun getClassMemberLabel(classDeclaration: CSTNode, memberDeclaration: CSTNode): String {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to get label for the member of a non-class \"${classDeclaration.name}\"")
        }

        if (memberDeclaration.name !in setOf("FieldDeclaration", "MethodDeclaration", "AbstractMethodDeclaration", "ConstructorDeclaration")) {
            val className = getDeclarationName(classDeclaration)
            throw PackageManagerError("Tried to get label for a non-member \"${memberDeclaration.name}\" of class \"$className\"")
        }

        if (memberDeclaration.name == "MethodDeclaration" && "native" in getModifiers(memberDeclaration)) {
            return "NATIVE${getQualifiedDeclarationName(classDeclaration)}.${getDeclarationName(memberDeclaration)}"
        }

        val classDeclarationLabel = getClassOrInterfaceDeclarationLabel(classDeclaration)

        if (memberDeclaration.name == "FieldDeclaration") {
            val fieldName = getDeclarationName(memberDeclaration)
            val label = "FIELD#$classDeclarationLabel#$fieldName"
            classMemberLabelMap[memberDeclaration] = label
            return label
        }


        val formalParameterListOpt = when (memberDeclaration.name) {
            in setOf("AbstractMethodDeclaration", "MethodDeclaration") -> {
                val methodHeader = memberDeclaration.getChild("MethodHeader")
                val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                methodDeclarator.getChild("FormalParameterListOpt")
            }
            "ConstructorDeclaration" -> {
                val constructorDeclarator = memberDeclaration.getChild("ConstructorDeclarator")
                constructorDeclarator.getChild("FormalParameterListOpt")
            }
            else -> {
                throw PackageManagerError("Tried to get FormalParameterListOpt of non-constructor and non-method CSTNode of type ${memberDeclaration.name}")
            }
        }

        val formalParameterTypes = getFormalParameterTypes(formalParameterListOpt)

        val serializedFormalParameters = formalParameterTypes.map({ formalParameterType ->
            if (formalParameterType.type.name in setOf("ClassDeclaration", "InterfaceDeclaration")) {
                getClassOrInterfaceDeclarationLabel(formalParameterType.type)
            } else {
                formalParameterType.type.name
            } + if (formalParameterType.isArray) "?" else ""
        }).joinToString("@")

        val label = when (memberDeclaration.name) {
            in setOf("AbstractMethodDeclaration", "MethodDeclaration") -> {
                val methodName = getDeclarationName(memberDeclaration)
                "METHOD#$classDeclarationLabel#$methodName#$serializedFormalParameters"
            }
            "ConstructorDeclaration" -> {
                "CONSTRUCTOR#$classDeclarationLabel#$serializedFormalParameters"
            }
            else -> {
                throw PackageManagerError("Tried to get Label of non-constructor and non-method CSTNode of type ${memberDeclaration.name}")
            }
        }

        return label
    }

    fun getClassMemberLabel(memberDeclaration: CSTNode): String {
        if (memberDeclaration.name !in setOf("FieldDeclaration", "MethodDeclaration", "AbstractMethodDeclaration", "ConstructorDeclaration")) {
            throw PackageManagerError("Tried to get label for a non-member ${memberDeclaration.name}")
        }

        if (memberDeclaration in classMemberLabelMap) {
            return classMemberLabelMap[memberDeclaration]!!
        }

        for ((_, compilationUnits) in packages) {
            for (compilationUnit in compilationUnits) {
                if (compilationUnit.getDescendants({ it == memberDeclaration }).isEmpty()) {
                    continue
                }

                val classDeclaration = compilationUnit.getDescendant("ClassDeclaration")

                val label = getClassMemberLabel(classDeclaration, memberDeclaration)
                classMemberLabelMap[memberDeclaration] = label
                return label
            }
        }

        val memberDeclarationName = getDeclarationName(memberDeclaration)
        throw PackageManagerError("Could not find class declaration for member \"$memberDeclarationName\"")
    }

    fun getInstanceFieldInitializerLabel(classDeclaration: CSTNode): String {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to get instance field initializer label of non-class declaration CSTNode ${classDeclaration.name}")
        }

        val classLabel = getClassOrInterfaceDeclarationLabel(classDeclaration)
        return "INSTANCE_FIELD_INITIALIZERS#$classLabel"
    }

    fun getStaticFieldInitializerLabel(classDeclaration: CSTNode): String {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to get instance field initializer label of non-class declaration CSTNode ${classDeclaration.name}")
        }

        val classLabel = getClassOrInterfaceDeclarationLabel(classDeclaration)
        return "STATIC_FIELD_INITIALIZERS#$classLabel"
    }

    fun getStaticFieldsDefinitionLabel(classDeclaration: CSTNode): String {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to get static fields definition label of non-class declaration CSTNode ${classDeclaration.name}")
        }

        val classLabel = getClassOrInterfaceDeclarationLabel(classDeclaration)
        return "STATIC_FIELDS#$classLabel"
    }

    fun getVTableLabel(classDeclaration: CSTNode): String {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to get VTable label of non-class declaration CSTNode ${classDeclaration.name}")
        }

        val classLabel = getClassOrInterfaceDeclarationLabel(classDeclaration)
        return "V_TABLE#$classLabel"
    }

    fun getSelectorIndexedTableLabel(classDeclaration: CSTNode): String {
        if (classDeclaration.name != "ClassDeclaration") {
            throw PackageManagerError("Tried to get VTable label of non-class declaration CSTNode ${classDeclaration.name}")
        }

        val classLabel = getClassOrInterfaceDeclarationLabel(classDeclaration)
        return "SELECTOR_INDEXED_TABLE#$classLabel"
    }

    private var allInterfaceMethodsInProgram: List<CSTNode>? = null

    fun getAllInterfaceMethodsInProgram(): List<CSTNode> {
        if (allInterfaceMethodsInProgram == null) {
            allInterfaceMethodsInProgram = packages.values.flatten()
                .map({ compilationUnit -> compilationUnit.getDescendant(isClassOrInterfaceDeclaration)})
                .filter({ type -> type.name == "InterfaceDeclaration" })
                .flatMap({ it.getDescendants("AbstractMethodDeclaration") })
                .plus(getBaseInterfaceMethods())
        }

        return allInterfaceMethodsInProgram!!
    }

    private var superTypeTables = mutableMapOf<Type, Map<Type, Boolean>>()

    fun getSuperTypeTableKeys(): List<Type> {
        return packages
            .values
            .flatten()
            .map({ compilationUnit -> compilationUnit.getDescendant(isClassOrInterfaceDeclaration) })
            .sortedBy({ getDeclarationName(it) })
            .flatMap({ listOf(Type(it, false), Type(it, true)) })
            .plus(
                primitiveTypes.map({ primitiveType ->
                    Type(CSTNode(primitiveType), true)
                })
            )
    }

    fun getSuperTypeTable(typeToInspect: Type): Map<Type, Boolean> {
        if (typeToInspect in superTypeTables) {
            return superTypeTables[typeToInspect]!!
        }

        val typesInProgram = getSuperTypeTableKeys()

        val superTypeTable = typesInProgram
            .fold(mutableMapOf<Type, Boolean>(), { map, type ->
                map[type] = colonEquals(type, typeToInspect)
                map
            })

        superTypeTables[typeToInspect] = superTypeTable

        return superTypeTable
    }

    fun getSuperTypeTableLabel(typeDeclaration: Type): String {
        if (typeDeclaration.type.name !in "ClassDeclaration" + primitiveTypes) {
            throw PackageManagerError("Expected argument to be a valid type, but got \"${typeDeclaration.type.name}\"")
        }

        val typeName =
            if (typeDeclaration.type.name in primitiveTypes) {
                typeDeclaration.type.name
            } else {
                getClassOrInterfaceDeclarationLabel(typeDeclaration.type)
            }

        return "SUPER_TYPE_TABLE#$typeName#${if (typeDeclaration.isArray) "ARRAY" else "NO_ARRAY" }"
    }

    fun getAllClasses(): List<CSTNode> {
        return packages
            .values
            .flatten()
            .map({ compilationUnit -> compilationUnit.getDescendant(isClassOrInterfaceDeclaration) })
            .filter({ it.name == "ClassDeclaration" })
    }
}
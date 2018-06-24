package oj.models

import java.util.*

open class CSTNodeError(reason: String): Exception(reason)

class FoundNoChild: CSTNodeError("Found no child, expected 1.")
class FoundManyChildren: CSTNodeError("Found many children, expected 1.")

class FoundNoDescendant : CSTNodeError("Found no descendants, expected 1.")
class FoundManyDescendants: CSTNodeError("Found many descendants, expected 1.")

enum class NameNodeClassification {
    Package,
    Type,
    Expression,
    Method
}

class CSTNode(
    val name: String,
    val lexeme: String = "",
    val children: MutableList<CSTNode> = mutableListOf()
) {
    companion object {
        private val declarations: MutableMap<CSTNode, CSTNode> = mutableMapOf()
        private val types: MutableMap<CSTNode, Type> = mutableMapOf()
        private val downCast: MutableSet<CSTNode> = mutableSetOf()
        private val nameNodeClassifications: MutableMap<CSTNode, NameNodeClassification> = mutableMapOf()
    }

    fun setNameNodeClassification(classification: NameNodeClassification) {
        if (name != "Name") {
            throw CSTNodeError("Tried to set name node classification from non-name \"$name\" node.")
        }

        nameNodeClassifications[this] = classification
    }

    fun getNameNodeClassification(): NameNodeClassification? {
        if (name != "Name") {
            throw CSTNodeError("Tried to get name node classification from non-name \"$name\" node.")
        }

        return nameNodeClassifications[this]
    }

    // TODO: Two names that are value equal will resolve to the same declaration. This is incorrect. Fix.
    fun setDeclaration(node: CSTNode) {
        if (name !in listOf("Name", "IDENTIFIER", "ClassInstanceCreationExpression")) {
            throw CSTNodeError("Tried to assign a declaration to a \"$name\" != \"Name\" or \"IDENTIFIER\" node.")
        }

        CSTNode.declarations[this] = node
    }

    fun getDeclaration(): CSTNode {
        if (name !in listOf("Name", "IDENTIFIER", "ClassInstanceCreationExpression")) {
            throw CSTNodeError("Tried to retrieve a declaration for a \"$name\" != \"Name\" or \"IDENTIFIER\" node.")
        }

        val declaration = CSTNode.declarations[this]
        if (declaration == null) {
            val serializedName =
                if (name == "Name")
                    serializeName(this)
                else
                    lexeme

            throw CSTNodeError("\"$serializedName\" \"$name\" node doesn't have a declaration assigned to it.")
        }

        return declaration
    }

    fun setType(type: Type) {
        CSTNode.types[this] = type
    }

    fun getType(): Type {
        val type = CSTNode.types[this]
        if (type == null) {
            throw CSTNodeError("\"$name\" node doesn't have a type assigned to it.")
        }

        return type
    }

    fun markDowncast() {
        if (name != "CastExpression") {
            throw CSTNodeError("Tried to mark down-cast on a non-cast CSTNode: $name")
        }

        CSTNode.downCast.add(this)
    }

    fun isDowncast(): Boolean {
        if (name != "CastExpression") {
            throw CSTNodeError("Tried to mark down-cast on a non-cast CSTNode: $name")
        }

        return this in CSTNode.downCast
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


fun areTypesTheSame(type: CSTNode, otherType: CSTNode) : Boolean {
    if (type.name != "Type" || otherType.name != "Type") {
        throw CSTNodeError("Tried to compare non-types")
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
                val primitiveType = arrayType.children[0]
                val otherPrimitiveType = otherArrayType.children[0]

                if (primitiveType.children[0].name != otherPrimitiveType.children[0].name) {
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
        throw CSTNodeError("Tried to compare non-method declarators")
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
        throw CSTNodeError("Tried to compare non-method headers")
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

fun canMethodsReplaceOneAnother(node1: CSTNode, node2: CSTNode): Boolean {
    val methodHeader1 = when (node1.name) {
        "MethodHeader" -> node1
        in setOf("AbstractMethodDeclaration", "MethodDeclaration") -> node1.getChild("MethodHeader")
        else -> throw CSTNodeError("Expected node1 to be of type \"MethodDeclaration\" or \"AbstractMethodDeclaration\", but was \"${node1.name}\"")
    }

    val methodHeader2 = when (node2.name) {
        "MethodHeader" -> node2
        in setOf("AbstractMethodDeclaration", "MethodDeclaration") -> node2.getChild("MethodHeader")
        else -> throw CSTNodeError("Expected node2 to be of type \"MethodDeclaration\" or \"AbstractMethodDeclaration\", but was \"${node2.name}\"")
    }

    val methodDeclarator1 = methodHeader1.getChild("MethodDeclarator")
    val methodDeclarator2 = methodHeader2.getChild("MethodDeclarator")

    val methodName1 = methodDeclarator1.getChild("IDENTIFIER").lexeme
    val methodName2 = methodDeclarator2.getChild("IDENTIFIER").lexeme

    if (areMethodSignaturesTheSame(methodDeclarator1, methodDeclarator2)) {
        if (!areMethodReturnTypesTheSame(methodHeader1, methodHeader2)) {
            throw CSTNodeError("Methods \"$methodName1\" and \"$methodName2\" have the same signature but different return types, which is illegal.")
        }

        return true
    }

    return false
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

    if (declaration.name == "FormalParameter") {
        return declaration.getChild("IDENTIFIER").lexeme
    }

    throw TriedToGetNameOfAnUnsupportedCSTNode(declaration.name)
}

fun isInterfaceImplementedByClass(interfaceDeclaration: CSTNode, classDeclaration: CSTNode): Boolean {
    if (classDeclaration.name != "ClassDeclaration") {
        throw CSTNodeError("Expected classDeclaration to be a \"ClassDeclaration\", but found \"${classDeclaration.name}\"")
    }

    if (interfaceDeclaration.name != "InterfaceDeclaration") {
        throw CSTNodeError("Expected interfaceDeclaration to be a \"InterfaceDeclaration\", but found \"${interfaceDeclaration.name}\"")
    }

    var currentClass = classDeclaration

    while (true) {
        val queue = LinkedList<CSTNode>()
        val extendedInterfaces = currentClass.getChild("InterfacesOpt").getDescendants("Name").map({ it.getDeclaration() })
        queue.addAll(extendedInterfaces)

        while (queue.isNotEmpty()) {
            val interfaceBeingExamined = queue.remove()
            if (interfaceDeclaration === interfaceBeingExamined) {
                return true
            }

            queue.addAll(interfaceBeingExamined.getChild("ExtendsInterfacesOpt").getDescendants("Name").map({ it.getDeclaration() }))
        }

        val superOpt = currentClass.getChild("SuperOpt")
        val superClasses = superOpt.getDescendants("Name").map({ it.getDeclaration() })

        if (superClasses.size == 0) {
            break
        }

        currentClass = superClasses[0]
    }

    return false
}

fun resolveTypeNode(typeNode: CSTNode) : Type {
    if (typeNode.children[0].name == "ReferenceType") {
        val referenceType = typeNode.getChild("ReferenceType")

        if (referenceType.children[0].name == "ArrayType") {
            val arrayType = referenceType.getChild("ArrayType")

            if (arrayType.children[0].name == "PrimitiveType") {
                val primitiveType = arrayType.getChild("PrimitiveType")
                return Type(primitiveType.children[0], true)
            }

            val name = arrayType.getChild("Name")
            return Type(name.getDeclaration(), true)
        }

        val classOrInterfaceType = referenceType.getChild("ClassOrInterfaceType")
        val name = classOrInterfaceType.getChild("Name")
        return Type(name.getDeclaration(), false)
    }

    val primitiveType = typeNode.getChild("PrimitiveType")
    return Type(primitiveType.children[0], false)
}

class TriedToGetDeclarationTypeOfAnUnsupportedCSTNode(nodeName: String): Exception("Tried to get declaration type of an unsupported CSTNode: $nodeName")

fun getDeclarationType(node: CSTNode): Type {
    when {
        node.name in setOf("ClassDeclaration", "InterfaceDeclaration") -> {
            return Type(node, false)
        }
        node.name in setOf("FieldDeclaration", "FormalParameter", "LocalVariableDeclaration") -> {
            return resolveTypeNode(node.getChild("Type"))
        }
        node.name == "MethodDeclaration" || node.name =="AbstractMethodDeclaration" -> {
            val methodHeader = node.getChild("MethodHeader")
            if (methodHeader.children[1].name == "void") {
                return Type(methodHeader.children[1], false)
            } else {
                val methodReturnType = methodHeader.getChild("Type")
                return resolveTypeNode(methodReturnType)
            }
        }
    }

    throw TriedToGetDeclarationTypeOfAnUnsupportedCSTNode(node.name)
}

fun getActualParameterTypes(argumentListOpt: CSTNode): List<Type> {
    return argumentListOpt.getDescendants("Expression").map({ it.getType() })
}

fun getFormalParameterTypes(formalParameterListOpt: CSTNode): List<Type> {
    return formalParameterListOpt.getDescendants("Type").map({ resolveTypeNode(it) })
}

fun getQualifiedIdentifierFromName(name: CSTNode): String {
    if (name.name != "Name") {
        throw CSTNodeError("Tried to get qualified identifier from non-name node: \"${name.name}\"")
    }
    return name.getDescendants("IDENTIFIER").map({ it.lexeme }).joinToString(".")
}

val isClassOrInterfaceDeclaration = { node: CSTNode ->
    node.name == "ClassDeclaration" || node.name == "InterfaceDeclaration"
}

fun serializeName(nameNode: CSTNode): String {
    if (nameNode.name !in listOf("Name", "QualifiedName", "SimpleName")) {
        throw CSTNodeError("Inside \"serializeName\": Tried to serialize non-name node.")
    }

    return nameNode.getDescendants("IDENTIFIER").map({ it.lexeme }).joinToString(".")
}

package oj.codegenerator

import oj.models.*
import java.util.*

const val OBJECT_V_TABLE_POINTER_OFFSET = 0
const val OBJECT_SELECTOR_INDEXED_TABLE_POINTER_OFFSET = 4
const val OBJECT_SUPER_TYPE_TABLE_OFFSET = 8
const val OBJECT_FIELD_OFFSET = 12

const val ARRAY_V_TABLE_POINTER_OFFSET = 0
const val ARRAY_SELECTOR_INDEXED_TABLE_POINTER_OFFSET = 4
const val ARRAY_SUPER_TYPE_TABLE_OFFSET = 8
const val ARRAY_ELEMENT_TYPE_OFFSET = 12
const val ARRAY_FIELD_OFFSET = 16
const val ARRAY_ELEMENT_OFFSET = 20

class CodeGenerationVisitor(
    private val packageManager: PackageManager
) : CSTNodeVisitor() {
    open class CodeGenerationError(reason: String): Exception(reason)

    private val asmWriter = ASMWriter()
    private val localVariableDeclarationEnvironment = Environment()
    private val formalParameterEnvironment = Environment()
    private var currentClassDeclaration: CSTNode? = null
    private var leftHandSideStack: LinkedList<CSTNode> = LinkedList()
    private var currentMethodDeclaration: CSTNode? = null
    private val stringLiteralDeclarations: MutableList<String> = mutableListOf()

    fun getCode(): String {
        return asmWriter.getCode()
    }

    private fun writeComment(comment: String = "") {
        asmWriter.writeComment(comment)
    }

    private fun writeLn(line: String = "") {
        asmWriter.writeLn(line)
    }

    private fun withIndent(fn: () -> Unit) {
        asmWriter.withIndent(fn)
    }

    override fun visitClassDeclaration(node: CSTNode) {
        currentClassDeclaration = node
        try {
            /**
             * Section text
             */
            writeLn("section .text")

            /**
             * Write Externs
             */

            writeComment("Externs for primitive arrays super type tables")
            primitiveTypes
                .map({ primitiveType -> Type(CSTNode(primitiveType), true) })
                .forEach({ primitiveArrayType ->
                    val superTypeTableLabel = packageManager.getSuperTypeTableLabel(primitiveArrayType)
                    writeLn("extern $superTypeTableLabel")
                })
            writeLn()

            /**
             * Extern runtime.s functions
             */

            writeComment("Externs for runtime.s")
            writeLn("extern __malloc")
            writeLn("extern __debexit")
            writeLn("extern __exception")
            writeLn()

            val nativeMethods = node.getDescendants({ it.name == "MethodDeclaration" && "native" in getModifiers(it) })
            writeComment("Extern own native methods")
            nativeMethods.forEach({ nativeMethod ->
                val nativeMethodLabel = packageManager.getClassMemberLabel(nativeMethod)
                writeLn("extern $nativeMethodLabel")
            })
            writeLn()


            packageManager.packages.values
                .flatMap({ compilationUnits -> compilationUnits })
                .map({ compilationUnit -> compilationUnit.getDescendant(isClassOrInterfaceDeclaration) })
                .filter({ type -> type.name == "ClassDeclaration" })
                .filter({ classDeclaration -> classDeclaration != node })
                .forEach({ classDeclaration ->
                    writeComment("EXTERNS for ${packageManager.getQualifiedDeclarationName(classDeclaration)}")
                    writeLn()
                    /**
                     * Extern constructors
                     */
                    val constructors = classDeclaration.getDescendants("ConstructorDeclaration")
                    constructors.forEach({ constructor ->
                        writeLn("extern ${packageManager.getClassMemberLabel(constructor)}")
                    })

                    /**
                     * Extern methods
                     */
                    val methods = classDeclaration.getDescendants({
                        descendant -> descendant.name == "MethodDeclaration" && "abstract" !in getModifiers(descendant)
                    })
                    methods.forEach({ method ->
                        writeLn("extern ${packageManager.getClassMemberLabel(classDeclaration, method)}")
                    })

                    /**
                     * Extern fields
                     */
                    val staticFields = classDeclaration.getDescendants({
                        descendant -> descendant.name == "FieldDeclaration" && "static" in getModifiers(descendant)
                    })
                    staticFields.forEach({ staticField ->
                        writeLn("extern ${packageManager.getClassMemberLabel(classDeclaration, staticField)}")
                    })

                    /**
                     * Extern VTable
                     */
                    writeLn("extern ${packageManager.getVTableLabel(classDeclaration)}")

                    /**
                     * Extern Selector Indexed Table
                     */
                    writeLn("extern ${packageManager.getSelectorIndexedTableLabel(classDeclaration)}")

                    /**
                     * Extern Supertype Tables
                     */
                    writeLn("extern ${packageManager.getSuperTypeTableLabel(Type(classDeclaration, true))}")
                    writeLn("extern ${packageManager.getSuperTypeTableLabel(Type(classDeclaration, false))}")
                    writeLn()
                    writeLn()
                })

            /**
             * Write all constructors
             */

            val constructors = node.getDescendants("ConstructorDeclaration")
            constructors.forEach({ visit(it) })

            /**
             * Write all methods
             */
            val methods = node.getDescendants("MethodDeclaration")
            methods.forEach({ visit(it) })

            /**
             * Static field initializers
             */
            val staticFields = node.getDescendants({ it.name == "FieldDeclaration" && "static" in getModifiers(it) })
            val classStaticFieldInitializerLabel = packageManager.getStaticFieldInitializerLabel(node)
            val qualifiedClassName = packageManager.getQualifiedDeclarationName(node)
            withAssemblyMethodDeclaration(classStaticFieldInitializerLabel, {
                staticFields.forEach({ staticField ->
                    if (staticField.children[2].name == "VariableDeclarator") {
                        val qualifiedFieldName = "$qualifiedClassName.${getDeclarationName(staticField)}"
                        writeComment("Initializer for $qualifiedFieldName")
                        val staticFieldVariableInitializer = staticField.getDescendant("VariableInitializer")
                        visit(staticFieldVariableInitializer)
                        writeLn()

                        writeComment("$qualifiedFieldName = eax")
                        val staticFieldLabel = packageManager.getClassMemberLabel(staticField)
                        writeLn("mov [$staticFieldLabel], eax")
                        writeLn()
                    }
                })
            })

            /**
             * Instance field initializers
             */
            writeLn()
            val instanceFields = node.getDescendants({ it.name == "FieldDeclaration" && "static" !in getModifiers(it) })
            val classInstanceFieldInitializerLabel = packageManager.getInstanceFieldInitializerLabel(node)
            withAssemblyMethodDeclaration(classInstanceFieldInitializerLabel, {
                instanceFields
                    .zip(IntRange(0, instanceFields.size - 1))
                    .forEach({(instanceField, i) ->
                        if (instanceField.children[2].name == "VariableDeclarator") {
                            val instanceFieldVariableInitializer = instanceField.getDescendant("VariableInitializer")
                            val qualifiedFieldName = "Instance Field: $qualifiedClassName.${getDeclarationName(instanceField)}"
                            writeComment("Initializer for $qualifiedFieldName")
                            visit(instanceFieldVariableInitializer)
                            writeLn()

                            writeComment("$qualifiedFieldName = eax")
                            // This assumes that "this" will be pushed as the first argument to the stack when this label is called.
                            writeLn("mov ebx, ebp")
                            writeLn("add ebx, 8")
                            writeLn("mov ebx, [ebx]")
                            writeLn("add ebx, ${OBJECT_FIELD_OFFSET + i * 4}")
                            writeLn("mov [ebx], eax")
                            writeLn()
                        }
                    })
            })

            /**
             * Section data
             */
            writeLn()
            writeLn("section .data")

            /**
             * Write static fields
             */
            val staticFieldsDefinitionLabel = packageManager.getStaticFieldsDefinitionLabel(node)
            writeLn("$staticFieldsDefinitionLabel:")
            withIndent({
                staticFields.forEach({ staticField ->
                    val staticFieldLabel = packageManager.getClassMemberLabel(staticField)
                    writeLn("global $staticFieldLabel")
                    writeLn("$staticFieldLabel: dd 0")
                })
            })
            writeLn()

            /**
             * Write VTable
             */
            val vTableLabel = packageManager.getVTableLabel(node)
            writeLn("global $vTableLabel")
            writeLn("$vTableLabel:")
            withIndent({
                packageManager
                    .getClassOverriddenInstanceMethods(node)
                    .map({ packageManager.getClassMemberLabel(it) })
                    .forEach({ writeLn("dd $it") })
            })
            writeLn()

            /**
             * Write Selector Indexed Table
             */
            val selectorIndexedTableLabel = packageManager.getSelectorIndexedTableLabel(node)
            writeLn("global $selectorIndexedTableLabel")
            writeLn("$selectorIndexedTableLabel:")
            withIndent({
                val interfaceOverriddenMethods = packageManager.getClassOverriddenInstanceMethods(node)

                packageManager
                    .getAllInterfaceMethodsInProgram()
                    .forEach({ abstractMethodDeclaration ->
                        val concreteImplementationsOfAbstractMethod = interfaceOverriddenMethods.filter({ concreteInstanceMethod ->
                            canMethodsReplaceOneAnother(concreteInstanceMethod, abstractMethodDeclaration)
                        })

                        if (concreteImplementationsOfAbstractMethod.size > 1) {
                            val className = getDeclarationName(node)
                            throw CodeGenerationError("An interface method is implemented by twice by a class \"$className\"")
                        }

                        if (concreteImplementationsOfAbstractMethod.isEmpty()) {
                            writeLn("dd 0")
                        } else {
                            writeLn("dd ${packageManager.getClassMemberLabel(concreteImplementationsOfAbstractMethod[0])}")
                        }
                    })
            })
            writeLn()

            /**
             * Write SuperType table
             */

            for (isArray in listOf(true, false)) {
                val type = Type(node, isArray)
                val superTypeTableLabel = packageManager.getSuperTypeTableLabel(type)
                writeLn("global $superTypeTableLabel")
                writeLn("$superTypeTableLabel:")
                withIndent({
                    val superTypeTable = packageManager.getSuperTypeTable(type)

                    superTypeTable.forEach({(type, isSuperType) ->
                        writeLn("dd ${if (isSuperType) 1 else 0} ;; $type")
                    })
                })
                writeLn()
            }

            /**
             * Write String Literal declarations
             */
            stringLiteralDeclarations.forEach({
                writeLn(it)
            })

            stringLiteralDeclarations.clear()

        } finally {
            currentClassDeclaration = null
        }
    }

    override fun visitReturnStatement(node: CSTNode) {
        // This should evaluate the return expression and place its result in eax
        writeComment("Calculate the return Expression")
        super.visitReturnStatement(node)
        writeLn()

        writeComment("Return from method")
        writeLn("add esp, ${localVariableDeclarationEnvironment.size * 4}")
        writeLn("pop ebp")
        writeLn("ret")
    }

    fun withAssemblyMethodDeclaration(declaration: CSTNode, fn: () -> Unit) {
        val methodLabel = packageManager.getClassMemberLabel(declaration)
        withAssemblyMethodDeclaration(methodLabel, fn)
    }

    fun withAssemblyMethodDeclaration(label: String, fn: () -> Unit) {
        writeLn("global $label")
        writeLn("$label:")
        withIndent({
            writeComment("Method preamble")
            writeLn("push ebp")
            writeLn("mov ebp, esp")
            writeLn()
            fn()
            writeLn()
            writeComment("Method postamble")
            writeLn("pop ebp")
            writeLn("ret")
        })
    }

    override fun visitConstructorDeclaration(node: CSTNode) {
        formalParameterEnvironment.withNewScope({
            localVariableDeclarationEnvironment.withNewScope({
                val originalNumVariables = localVariableDeclarationEnvironment.size

                super.visit(node.getChild("Modifiers"))
                super.visit(node.getChild("ConstructorDeclarator"))

                withAssemblyMethodDeclaration(node, {
                    val currentClassDeclaration = getCurrentClassDeclaration()
                    val parentOfCurrentClass = packageManager.getSuperClass(currentClassDeclaration)

                    if (parentOfCurrentClass != null) {
                        val parentZeroArgumentConstructor = parentOfCurrentClass.getDescendant({
                            it.name == "ConstructorDeclaration" && it.getChild("ConstructorDeclarator").getDescendants("FormalParameter").isEmpty()
                        })

                        val parentZeroArgumentConstructorLabel = packageManager.getClassMemberLabel(parentZeroArgumentConstructor)
                        val stackOffsetOfThis = 8 + formalParameterEnvironment.size * 4

                        writeComment("Retrieve `this` from method call arguments")
                        writeLn("mov eax, ebp")
                        writeLn("add eax, $stackOffsetOfThis")
                        writeLn("mov eax, [eax]")
                        writeLn()

                        writeComment("Move parent's zero argument constructor method address into ebx")
                        writeLn("mov ebx, $parentZeroArgumentConstructorLabel")
                        writeLn()

                        callInstanceMethod(listOf())
                        writeLn()
                    }

                    val instanceFieldInitializerMethod = packageManager.getInstanceFieldInitializerLabel(currentClassDeclaration)
                    val stackOffsetOfThis = 8 + formalParameterEnvironment.size * 4

                    writeComment("Retrieve `this` from method call arguments")
                    writeLn("mov eax, ebp")
                    writeLn("add eax, $stackOffsetOfThis")
                    writeLn("mov eax, [eax]")
                    writeLn()

                    writeComment("Move instance field initializer method address into ebx")
                    writeLn("mov ebx, $instanceFieldInitializerMethod")
                    writeLn()

                    callInstanceMethod(listOf())
                    writeLn()

                    super.visit(node.getChild("ConstructorBody"))

                    val numVariables = localVariableDeclarationEnvironment.size

                    check(numVariables >= originalNumVariables) {
                        "Can't pop off local variables from ConstructorDeclaration children"
                    }

                    writeComment("Pop off local variables defined in constructor body")
                    writeLn("add esp, ${(numVariables - originalNumVariables) * 4}")
                })

                writeLn()
            })
        })
    }

    fun getCurrentClassDeclaration(): CSTNode {
        return currentClassDeclaration!!
    }

    fun getCurrentMethodDeclaration(): CSTNode {
        return currentMethodDeclaration!!
    }

    override fun visitAssignment(node: CSTNode) {
        val leftHandSide = node.getChild("LeftHandSide")
        writeComment("Evaluate LeftHandSide")
        visit(leftHandSide)
        writeLn()

        writeComment("Save LeftHandSide")
        writeLn("push eax")
        writeLn()

        val assignmentExpression = node.getChild("AssignmentExpression")
        val typeOfAssignmentExpression = assignmentExpression.getType()
        val shouldCheckArrayStore = leftHandSide.children[0].name == "ArrayAccess" && !typeOfAssignmentExpression.isNull() && leftHandSide.getType().isReference()

        if (shouldCheckArrayStore) {
            writeComment("Save Array address")
            writeLn("push ebx")
            writeLn()
        }

        writeComment("Evaluate AssignmentExpression (RHS)")
        visit(assignmentExpression)
        writeLn()

        if (shouldCheckArrayStore) {
            writeComment("Restore array address")
            writeLn("pop ebx")
            writeLn()

            val arrayStoreValid = genLabel("arrayStoreValid")
            writeComment("Skip array store check if eval(AssignmentExpression) == null")
            writeLn("cmp eax, 0")
            writeLn("je $arrayStoreValid")
            writeLn()

            withIndent({
                writeComment("Move to the SuperType table of AssignmentExpression")
                writeLn("mov ecx, eax")
                writeLn("add ecx, $OBJECT_SUPER_TYPE_TABLE_OFFSET")
                writeLn("mov ecx, [ecx]")
                writeLn()

                writeComment("Store array object's element type in ebx")
                writeLn("add ebx, $ARRAY_ELEMENT_TYPE_OFFSET")
                writeLn("mov ebx, [ebx]")
                writeLn()

                writeComment("Compute type(LeftHandSide) := type(AssignmentExpression)")
                writeLn("imul ebx, 4")
                writeLn("add ecx, ebx")
                writeLn("mov ebx, [ecx]")
                writeLn()

                writeComment("Throw array store error")
                writeLn("cmp ebx, 1")
                writeLn("je $arrayStoreValid")
                writeLn("call __exception")
            })

            writeLn("$arrayStoreValid:")
            writeLn()
        }

        writeComment("Write assignment RHS to LHS")
        writeLn("pop ebx")
        writeLn("mov [ebx], eax")
        writeLn()

        writeComment("Return value is in eax")
    }

    override fun visitConditionalOrExpression(node: CSTNode) {
        when (node.children[0].name) {
            "ConditionalAndExpression" -> {
                visit(node.getChild("ConditionalAndExpression"))
            }

            "ConditionalOrExpression" -> {
                val conditionalOrExpression = node.getChild("ConditionalOrExpression")
                val conditionalAndExpression = node.getChild("ConditionalAndExpression")
                val endLabel = genLabel("end")

                writeComment("ConditionalOrExpression: Evaluate ConditionalOrExpression")
                visit(conditionalOrExpression)
                writeLn()

                writeComment("Short-circuit for \"||\"")
                writeLn("cmp eax, 1")
                writeLn("je $endLabel")
                writeLn()

                writeComment("ConditionalOrExpression: Evaluate ConditionalAndExpression")
                visit(conditionalAndExpression)
                writeLn()
                writeLn("$endLabel:")
            }
        }
    }

    override fun visitConditionalAndExpression(node: CSTNode) {
        when (node.children[0].name) {
            "OrExpression" -> {
                visit(node.getChild("OrExpression"))
            }

            "ConditionalAndExpression" -> {
                val conditionalAndExpression = node.getChild("ConditionalAndExpression")
                val orExpression = node.getChild("OrExpression")
                val endLabel = genLabel("end")

                writeComment("ConditionalAndExpression: Evaluate ConditionalAndExpression")
                visit(conditionalAndExpression)
                writeLn()

                writeComment("Short-circuit for \"&&\"")
                writeLn("cmp eax, 0")
                writeLn("je $endLabel")
                writeLn()

                writeComment("ConditionalAndExpression: Evaluate OrExpression")
                visit(orExpression)
                writeLn()

                writeLn("$endLabel:")
            }
        }
    }

    override fun visitOrExpression(node: CSTNode) {
        when (node.children[0].name) {
            "AndExpression" -> {
                visit(node.getChild("AndExpression"))
            }

            "OrExpression" -> {
                val orExpression = node.getChild("OrExpression")
                val andExpression = node.getChild("AndExpression")

                writeComment("OrExpression: Evaluate OrExpression")
                visit(orExpression)
                writeLn()

                writeComment("Save result from OrExpression")
                writeLn("push eax")
                writeLn()

                writeComment("Evaluate AndExpression")
                visit(andExpression)
                writeLn()

                writeComment("Compute \"|\"")
                writeLn("pop ebx")
                writeLn("or eax, ebx")
            }
        }
    }

    override fun visitAndExpression(node: CSTNode) {
        when (node.children[0].name) {
            "EqualityExpression" -> {
                visit(node.getChild("EqualityExpression"))
            }

            "AndExpression" -> {
                val andExpression = node.getChild("AndExpression")
                val equalityExpression = node.getChild("EqualityExpression")

                writeComment("AndExpression: Evaluate AndExpression")
                visit(andExpression)
                writeLn()

                writeComment("Save result from AndExpression")
                writeLn("push eax")
                writeLn()

                writeComment("Evaluate EqualityExpression")
                visit(equalityExpression)
                writeLn()

                writeComment("Compute \"&\"")
                writeLn("pop ebx")
                writeLn("and eax, ebx")
            }
        }
    }

    val setCCInstructionMap = mapOf(
        "==" to "sete",
        "!=" to "setne",
        "<" to "setl",
        ">" to "setg",
        "<=" to "setle",
        ">=" to "setge"
    )

    override fun visitEqualityExpression(node: CSTNode) {
        if (node.children[0].name == "RelationalExpression") {
            visit(node.getChild("RelationalExpression"))
        } else {
            val equalityExpression = node.getChild("EqualityExpression")
            val relationalExpression = node.getChild("RelationalExpression")

            writeComment("Compute EqualityExpression")
            visit(equalityExpression)
            writeLn()

            writeComment("Save result from EqualityExpression onto stack")
            writeLn("push eax")
            writeLn()

            writeComment("Compute RelationalExpression")
            visit(relationalExpression)
            writeLn()

            writeComment("Restore result of EqualityExpression from stack")
            writeLn("pop ebx")
            writeLn()

            val operator = node.children[1].name

            writeComment("Check if EqualityExpression == RelationalExpression")
            writeLn("cmp eax, ebx")
            writeLn("${setCCInstructionMap[operator]} al")
            writeLn("movzx eax, al")
        }
    }

    override fun visitRelationalExpression(node: CSTNode) {
        if (node.children[0].name == "AdditiveExpression") {
            visit(node.getChild("AdditiveExpression"))
            return
        }

        val relationalExpression = node.getChild("RelationalExpression")
        writeComment("Compute RelationalExpression in RelationalExpression")
        visit(relationalExpression)
        writeLn()

        val operator = node.children[1].name
        if (operator in setCCInstructionMap.keys) {
            val additiveExpression = node.getChild("AdditiveExpression")
            writeComment("Save result from RelationalExpression")
            writeLn("push eax")
            writeLn()

            writeComment("Compute AdditiveExpression in RelationalExpression")
            visit(additiveExpression)
            writeLn()

            writeComment("Restore RelationalExpression from stack")
            writeLn("pop ebx")
            writeLn()

            writeComment("eax = AdditiveExpression $operator RelationalExpression")
            writeLn("cmp ebx, eax")
            writeLn("${setCCInstructionMap[operator]} al")
            writeLn("movzx eax, al")
            return
        }

        /**
         * At runtime, the result of the instanceof operator is true if the value of the RelationalExpression
         * is not null and the reference could be cast (§15.16) to the ReferenceType without raising a
         * ClassCastException. Otherwise the result is false.
         */

        val endLabel = genLabel("endif")

        writeComment("Ensure instanceof returns false if ReferenceType is null")
        writeLn("cmp eax, 0")
        writeLn("je $endLabel")
        writeLn()

        withIndent({
            writeComment("Move to object's SuperType table")
            writeLn("add eax, $OBJECT_SUPER_TYPE_TABLE_OFFSET")
            writeLn("mov eax, [eax]")
            writeLn()

            val referenceType = node.getChild("ReferenceType")
            val typeOfReferenceType = referenceType.getType()
            val indexOfReferenceType = packageManager.getSuperTypeTable(relationalExpression.getType()).keys.indexOf(typeOfReferenceType)

            check(indexOfReferenceType != -1) { "Type of \"ReferenceType\" node not found in SuperType table" }

            writeComment("Calculate instanceof $typeOfReferenceType")
            writeLn("add eax, ${indexOfReferenceType * 4}")
            writeLn("mov eax, [eax]")
        })
        writeLn()

        writeLn("$endLabel:")
    }

    /**
     * In `eax` should be the result of the expression
     */
    private fun convertToString(expressionType: Type) {
        val stringClass = packageManager.getJavaLangType("String")
        val stringType = Type(stringClass!!, false)

        val stringValueOfMethod = stringClass.getDescendant(fun(descendant: CSTNode): Boolean {
            if (descendant.name != "MethodDeclaration") {
                return false
            }

            val methodHeader = descendant.getChild("MethodHeader")
            val methodDeclarator = methodHeader.getChild("MethodDeclarator")
            val formalParameterListOpt = methodDeclarator.getChild("FormalParameterListOpt")
            val formalParameterTypes = getFormalParameterTypes(formalParameterListOpt)

            if (formalParameterTypes.size != 1 || getDeclarationName(descendant) != "valueOf") {
                return false
            }

            if (expressionType.isNull()) {
                val baseClass = packageManager.getJavaLangType("Object")
                check(baseClass != null) {
                    "Base class not found."
                }

                val baseClassType = Type(baseClass!!, false)

                return baseClassType == formalParameterTypes[0]
            }

            if (packageManager.colonEquals(stringType, expressionType)) {
                return stringType == formalParameterTypes[0]
            }

            if (expressionType.isPrimitive()) {
                return expressionType == formalParameterTypes[0]
            }

            return packageManager.colonEquals(formalParameterTypes[0], expressionType)
        })

        writeComment("Push expression result stored in eax as argument to String.valueOf call")
        writeLn("push eax")
        writeLn()

        val stringValueOfMethodLabel = packageManager.getClassMemberLabel(stringValueOfMethod)
        writeLn("call $stringValueOfMethodLabel")
        writeLn()

        writeComment("Pop formal parameter for method call")
        writeLn("add esp, 4")
        writeLn()
    }

    override fun visitAdditiveExpression(node: CSTNode) {
        if (node.children[0].name == "MultiplicativeExpression") {
            visit(node.getChild("MultiplicativeExpression"))
            return
        }

        val operator = node.children[1].name
        val additiveExpression = node.getChild("AdditiveExpression")
        val multiplicativeExpression = node.getChild("MultiplicativeExpression")

        when (operator) {
            "-" -> {
                // Operands must be numeric
                writeComment("Calculating AdditiveExpression")
                visit(additiveExpression)
                writeLn()

                writeComment("Save AdditiveExpression result on stack")
                writeLn("push eax")
                writeLn()

                writeComment("Calculate MultiplicativeExpression")
                visit(multiplicativeExpression)
                writeLn()

                writeComment("eax = ebx - eax = AdditiveExpression - MultiplicativeExpression")
                writeLn("pop ebx")
                writeLn("sub ebx, eax")
                writeLn("mov eax, ebx")
            }

            "+" -> {
                val typeOfAdditiveExpression = additiveExpression.getType()
                val typeOfMultiplicativeExpression =  multiplicativeExpression.getType()

                if (typeOfAdditiveExpression.isNumeric() && typeOfMultiplicativeExpression.isNumeric()) {
                    writeComment("Calculating AdditiveExpression")
                    visit(additiveExpression)
                    writeLn()

                    writeComment("Save AdditiveExpression result on stack")
                    writeLn("push eax")
                    writeLn()

                    writeComment("Calculate MultiplicativeExpression")
                    visit(multiplicativeExpression)
                    writeLn()

                    writeComment("eax = eax + ebx = MultiplicativeExpression + AdditiveExpression")
                    writeLn("pop ebx")
                    writeLn("add eax, ebx")
                    return
                }

                val stringClass = packageManager.getJavaLangType("String")
                val stringType = Type(stringClass!!, false)

                writeComment("Calculating AdditiveExpression")
                visit(additiveExpression)
                writeLn()

                writeComment("Convert AdditiveExpression (eax) to String using String.valueOf")
                convertToString(additiveExpression.getType())
                writeLn()

                writeComment("Save AdditiveExpression result on stack")
                writeLn("push eax")
                writeLn()

                writeComment("Calculate MultiplicativeExpression")
                visit(multiplicativeExpression)
                writeLn()

                writeComment("Convert MultiplicativeExpression (eax) to String using String.valueOf")
                convertToString(multiplicativeExpression.getType())
                writeLn()

                writeComment("Restore AdditiveExpression from stack")
                writeLn("pop ebx")
                writeLn()

                writeComment("State: ebx = eval(AdditiveExpression) and eax = eval(MultiplicativeExpression)")
                writeComment("Push arguments for String.concat")
                writeLn("push ebx")
                writeLn("push eax")
                writeLn()

                val stringConcatMethod = stringClass.getDescendant({ descendant ->
                    if (descendant.name != "MethodDeclaration") {
                        false
                    } else {
                        val methodHeader = descendant.getChild("MethodHeader")
                        val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                        val formalParameterListOpt = methodDeclarator.getChild("FormalParameterListOpt")
                        val formalParameterTypes = getFormalParameterTypes(formalParameterListOpt)
                        formalParameterTypes.size == 1 &&
                            formalParameterTypes[0] == stringType &&
                            getDeclarationName(descendant) == "concat"
                    }
                })

                val stringConcatMethodIndex = packageManager.getClassOverriddenInstanceMethods(stringClass).indexOf(stringConcatMethod)
                check(stringConcatMethodIndex != -1) { "Couldn't find String concat method." }

                writeComment("Move ebx to VTable of this String")
                writeLn("add ebx, $OBJECT_V_TABLE_POINTER_OFFSET")
                writeLn("mov ebx, [ebx]")
                writeLn()

                writeComment("Put String.concat into ebx")
                writeLn("add ebx, ${stringConcatMethodIndex * 4}")
                writeLn("mov ebx, [ebx]")
                writeLn()

                writeComment("Call String.concat")
                writeLn("call ebx")
                writeLn()

                writeComment("Pop formal parameters for String.concat method")
                writeLn("add esp, 8")
            }
        }
    }

    override fun visitMultiplicativeExpression(node: CSTNode) {
        if (node.children[0].name == "UnaryExpression") {
            visit(node.getChild("UnaryExpression"))
            return
        }

        val operator = node.children[1].name
        val multiplicativeExpression = node.getChild("MultiplicativeExpression")
        val unaryExpression = node.getChild("UnaryExpression")

        writeComment("Evaluate MultiplicativeExpression")
        visit(multiplicativeExpression)
        writeLn()

        writeComment("Save result from MultiplicativeExpression in MultiplicativeExpression")
        writeLn("push eax")
        writeLn()

        writeComment("Evaluate UnaryExpression in MultiplicativeExpression")
        visit(unaryExpression)
        writeLn()

        writeComment("Restore MultiplicativeExpression from stack")
        writeLn("pop ebx")
        writeLn()

        if (operator == "/" || operator == "%") {
            writeComment("Ensure UnaryExpression (divisor) is non-zero")
            val divisorNonZero = genLabel("divisorNonZero")
            writeLn("cmp eax, 0")
            writeLn("jne $divisorNonZero")
            writeLn("call __exception")
            writeLn("$divisorNonZero:")
            writeLn()
        }

        writeComment("Perform $operator computation")

        when (operator) {
            "*" -> {
                writeLn("imul eax, ebx")
            }
            "/" -> {
                writeLn("mov ecx, eax")
                writeLn("mov eax, ebx")
                writeLn("cdq")
                writeLn("idiv ecx")
            }
            "%" -> {
                writeLn("mov ecx, eax")
                writeLn("mov eax, ebx")
                writeLn("cdq")
                writeLn("idiv ecx")
                writeLn("mov eax, edx")
            }
        }
    }

    override fun visitUnaryExpression(node: CSTNode) {
        if (node.children[0].name == "UnaryExpressionNotPlusMinus") {
            visit(node.getChild("UnaryExpressionNotPlusMinus"))
            return
        }

        val unaryExpression = node.getChild("UnaryExpression")
        visit(unaryExpression)
        writeLn("neg eax")
    }

    override fun visitUnaryExpressionNotPlusMinus(node: CSTNode) {
        if (node.children[0].name == "!") {
            val unaryExpression = node.getChild("UnaryExpression")
            visit(unaryExpression)
            writeLn("not eax")
            writeLn("and eax, 1")
            return
        }

        visit(node.children[0])
    }

    /**
     * Assume `eax` contains the array or object
     */
    fun getMethodAddress(type: Type, methodDeclaration: CSTNode) {
        if (methodDeclaration.name !in setOf("MethodDeclaration", "AbstractMethodDeclaration")) {
            throw CodeGenerationError(
                "Expected name methodDeclaration to resolve to MethodDeclaration or AbstractMethodDeclaration, but it resolved to a \"${methodDeclaration.name}\""
            )
        }

        writeNullPointerCheck()
        writeLn()

        if (!type.isArray && methodDeclaration.name == "AbstractMethodDeclaration") {
            val allInterfaceMethodsInProgram = packageManager.getAllInterfaceMethodsInProgram()
            val selectorIndexedTableIndex = allInterfaceMethodsInProgram.indexOfFirst({ canMethodsReplaceOneAnother(it, methodDeclaration) })

            if (selectorIndexedTableIndex == -1) {
                val methodDeclarationName = getDeclarationName(methodDeclaration)
                throw CodeGenerationError("Instance method $methodDeclarationName not found in selector indexed table")
            }

            val qualifiedMethodName = run {
                val qualifiedTypeName = packageManager.getQualifiedDeclarationName(type.type)
                val methodName = getDeclarationName(methodDeclaration)
                "$qualifiedTypeName.$methodName"
            }

            val formalParameterListOpt = methodDeclaration.getDescendant("FormalParameterListOpt")
            val serializedArguments = getFormalParameterTypes(formalParameterListOpt).joinToString(",")

            writeComment("Retrieving address of interface method \"$qualifiedMethodName($serializedArguments)\"")
            writeLn("mov ebx, eax")
            writeLn("add ebx, $OBJECT_SELECTOR_INDEXED_TABLE_POINTER_OFFSET")
            writeLn("mov ebx, [ebx]")
            writeLn("add ebx, ${4 * selectorIndexedTableIndex}")
            writeLn("mov ebx, [ebx]")

        } else {
            val classDeclaration = if (type.isArray) packageManager.getJavaLangType("Object")!! else type.type
            val instanceMethods = packageManager.getClassOverriddenInstanceMethods(classDeclaration)
            val methodVTableIndex = instanceMethods.indexOfFirst({ canMethodsReplaceOneAnother(it, methodDeclaration) })

            if (methodVTableIndex == -1) {
                val methodDeclarationName = getDeclarationName(methodDeclaration)
                val className = getDeclarationName(type.type)

                throw CodeGenerationError("Instance method $methodDeclarationName not found in instance methods of class $className")
            }

            val qualifiedMethodName = run {
                val qualifiedTypeName = packageManager.getQualifiedDeclarationName(classDeclaration)
                val methodName = getDeclarationName(methodDeclaration)
                "$qualifiedTypeName.$methodName"
            }

            val formalParameterListOpt = methodDeclaration.getDescendant("FormalParameterListOpt")
            val serializedArguments = getFormalParameterTypes(formalParameterListOpt).joinToString(",")

            writeComment("Retrieving address of class instance method \"$qualifiedMethodName($serializedArguments)\"")
            writeLn("mov ebx, eax")
            writeLn("add ebx, $OBJECT_V_TABLE_POINTER_OFFSET")
            writeLn("mov ebx, [ebx]")
            writeLn("add ebx, ${4 * methodVTableIndex}")
            writeLn("mov ebx, [ebx]")
        }
    }

    fun writeNullPointerCheck() {
        writeComment("NullPointerException: assert(eax != null)")
        val pointerNotNull = genLabel("pointerNotNull")
        writeLn("cmp eax, 0")
        writeLn("jne $pointerNotNull")
        writeLn("call __exception")
        writeLn("$pointerNotNull:")
    }

    override fun visitName(node: CSTNode) {
        when (node.getNameNodeClassification()) {

            NameNodeClassification.Expression -> {
                when(node.children[0].name) {

                    "QualifiedName" -> {
                        val qualifiedName = node.getChild("QualifiedName")
                        val prefixName = qualifiedName.getChild("Name")

                        writeComment("Retrieve address of \"${serializeName(prefixName)}\"")
                        visit(prefixName)
                        writeLn()

                        when (prefixName.getNameNodeClassification()!!) {

                            NameNodeClassification.Expression -> {
                                writeNullPointerCheck()
                                writeLn()

                                val typeOfPrefixName = prefixName.getType()

                                val fieldName = serializeName(node)
                                writeComment("Retrieve field \"$fieldName\"")

                                if (typeOfPrefixName.isArray) {
                                    check(!isLValue(node)) {
                                        "array.length can't be on the LHS of an assignment."
                                    }

                                    writeLn("add eax, $ARRAY_FIELD_OFFSET")
                                    writeLn("mov eax, [eax]")
                                } else {

                                    val instanceFieldDeclaration = node.getDeclaration()

                                    if (instanceFieldDeclaration.name != "FieldDeclaration") {
                                        throw CodeGenerationError(
                                            "Expected name ${serializeName(node)} to resolve to FieldDeclaration, but it resolved to a \"${instanceFieldDeclaration.name}\""
                                        )
                                    }

                                    writeLn("add eax, $OBJECT_FIELD_OFFSET")

                                    val instanceFields = packageManager.getClassOverriddenInstanceFields(typeOfPrefixName.type)
                                    val fieldIndex = instanceFields.indexOf(instanceFieldDeclaration)

                                    if (fieldIndex == -1) {
                                        val fieldDeclarationName = getDeclarationName(instanceFieldDeclaration)
                                        val className = getDeclarationName(typeOfPrefixName.type)

                                        throw CodeGenerationError("Instance field $fieldDeclarationName not found in instance methods of class $className")
                                    }

                                    writeLn("add eax, ${fieldIndex * 4}")

                                    if (!isLValue(node)) {
                                        writeLn()
                                        writeComment("Dereference field \"$fieldName\"")
                                        writeLn("mov eax, [eax]")
                                    }
                                }
                            }

                            NameNodeClassification.Type -> {
                                val staticFieldDeclaration = node.getDeclaration()

                                if (staticFieldDeclaration.name != "FieldDeclaration") {
                                    throw CodeGenerationError(
                                        "Expected name ${serializeName(node)} to resolve to FieldDeclaration, but it resolved to a \"${staticFieldDeclaration.name}\""
                                    )
                                }

                                val fieldName = serializeName(node)
                                writeComment("Retrieve static field \"$fieldName\"")

                                val classMemberLabel = packageManager.getClassMemberLabel(staticFieldDeclaration)
                                writeLn("mov eax, $classMemberLabel")

                                if (!isLValue(node)) {
                                    writeLn()
                                    writeComment("Dereference static field \"$fieldName\"")
                                    writeLn("mov eax, [eax]")
                                }
                            }

                            else -> {
                                val identifier = qualifiedName.getChild("IDENTIFIER").lexeme
                                throw CodeGenerationError(
                                    "Tried to access field \"$identifier\" on a non-expression and non-type name node \"${serializeName(prefixName)}\""
                                )
                            }
                        }
                    }

                    "SimpleName" -> {
                        val nameNodeDeclaration = node.getDeclaration()

                        val serializedName = serializeName(node)

                        if (nameNodeDeclaration.name == "FormalParameter") {
                            val formalParameterIndex = formalParameterEnvironment.findIndex(getDeclarationName(nameNodeDeclaration))
                            val formalParameterStackOffset = 8 + (formalParameterEnvironment.size - (formalParameterIndex + 1)) * 4
                            writeComment("Retrieve FormalParameter \"$serializedName\" from stack")
                            writeLn("mov eax, $formalParameterStackOffset")
                            writeLn("add eax, ebp")

                            if (!isLValue(node)) {
                                writeLn()
                                writeComment("Dereference FormalParameter \"$serializedName\"")
                                writeLn("mov eax, [eax]")
                            }

                        } else if (nameNodeDeclaration.name == "LocalVariableDeclaration") {
                            val localVariableIndex = localVariableDeclarationEnvironment.findIndex(getDeclarationName(nameNodeDeclaration))
                            val localVariableStackOffset = -(localVariableIndex + 1) * 4
                            writeComment("Retrieve LocalVariableDeclaration \"$serializedName\" from stack")
                            writeLn("mov eax, $localVariableStackOffset")
                            writeLn("add eax, ebp")

                            if (!isLValue(node)) {
                                writeLn()
                                writeComment("Dereference LocalVariableDeclaration \"$serializedName\"")
                                writeLn("mov eax, [eax]")
                            }

                        } else if (nameNodeDeclaration.name == "FieldDeclaration") {
                            val stackOffsetOfThis = 8 + formalParameterEnvironment.size * 4
                            writeComment("Retrieve `this` from method call arguments")
                            writeLn("mov eax, $stackOffsetOfThis")
                            writeLn("add eax, ebp")
                            writeLn("mov eax, [eax]")
                            writeLn()

                            writeComment("Retrieve field \"$serializedName\" from this")
                            writeLn("add eax, $OBJECT_FIELD_OFFSET")

                            val instanceFields = packageManager.getClassOverriddenInstanceFields(getCurrentClassDeclaration())
                            val fieldIndex = instanceFields.indexOf(nameNodeDeclaration)

                            if (fieldIndex == -1) {
                                val fieldDeclarationName = getDeclarationName(nameNodeDeclaration)
                                val className = getDeclarationName(getCurrentClassDeclaration())
                                throw CodeGenerationError("Instance field $fieldDeclarationName not found in instance methods of class $className")
                            }

                            writeLn("add eax, ${fieldIndex * 4}")

                            if (!isLValue(node)) {
                                writeLn()
                                writeComment("Dereference field \"$serializedName\"")
                                writeLn("mov eax, [eax]")
                            }
                        }
                    }
                }
            }

            NameNodeClassification.Type -> {
                // Do nothing
            }

            NameNodeClassification.Method -> {
                when (node.children[0].name) {
                    "QualifiedName" -> {
                        val qualifiedName = node.getChild("QualifiedName")
                        val prefixName = qualifiedName.getChild("Name")

                        visit(prefixName)
                        writeLn()

                        val identifier = qualifiedName.getChild("IDENTIFIER").lexeme

                        when (prefixName.getNameNodeClassification()!!) {
                            NameNodeClassification.Expression -> {
                                getMethodAddress(prefixName.getType(), node.getDeclaration())
                            }

                            NameNodeClassification.Type -> {
                                val staticMethodDeclaration = node.getDeclaration()
                                if (staticMethodDeclaration.name != "MethodDeclaration") {
                                    throw CodeGenerationError(
                                        "Expected name ${serializeName(node)} to resolve to MethodDeclaration, but it resolved to a \"${staticMethodDeclaration.name}\""
                                    )
                                }

                                writeComment("Retrieve address of static method")
                                val classMemberLabel = packageManager.getClassMemberLabel(staticMethodDeclaration)
                                writeLn("mov ebx, $classMemberLabel")
                            }

                            else -> {
                                throw CodeGenerationError("Tried to access method \"$identifier\" on a non-expression and non-type name node \"${serializeName(prefixName)}\"")
                            }
                        }
                    }

                    "SimpleName" -> {
                        val instanceMethodDeclaration = node.getDeclaration()

                        if (instanceMethodDeclaration.name != "MethodDeclaration") {
                            throw CodeGenerationError(
                                "Expected name ${serializeName(node)} to resolve to MethodDeclaration, but it resolved to a \"${instanceMethodDeclaration.name}\""
                            )
                        }

                        val currentClassDeclaration = getCurrentClassDeclaration()
                        val instanceMethods = packageManager.getClassOverriddenInstanceMethods(currentClassDeclaration)
                        val methodVTableIndex = instanceMethods.indexOfFirst({ canMethodsReplaceOneAnother(it, instanceMethodDeclaration) })

                        if (methodVTableIndex == -1) {
                            val methodDeclarationName = getDeclarationName(instanceMethodDeclaration)
                            val className = getDeclarationName(currentClassDeclaration)

                            throw CodeGenerationError("Instance method $methodDeclarationName not found in instance methods of class $className")
                        }

                        val stackOffsetOfThis = 8 + formalParameterEnvironment.size * 4
                        writeComment("Retrieve `this` from method call arguments")
                        writeLn("mov eax, $stackOffsetOfThis")
                        writeLn("add eax, ebp")
                        writeLn("mov eax, [eax]")
                        writeLn()

                        val formalParameterListOpt = instanceMethodDeclaration.getDescendant("FormalParameterListOpt")
                        val serializedArguments = getFormalParameterTypes(formalParameterListOpt).joinToString(",")
                        val qualifiedClassName = packageManager.getQualifiedDeclarationName(currentClassDeclaration)
                        val qualifiedMethodName = "$qualifiedClassName.${getDeclarationName(instanceMethodDeclaration)}"

                        writeComment("Retrieve address of instance method \"$qualifiedMethodName($serializedArguments)\"")
                        writeLn("mov ebx, eax")
                        writeLn("add ebx, $OBJECT_V_TABLE_POINTER_OFFSET")
                        writeLn("mov ebx, [ebx]")
                        writeLn("add ebx, ${4 * methodVTableIndex}")
                        writeLn("mov ebx, [ebx]")
                    }
                }
            }

            else -> {
                super.visitName(node)
            }
        }
    }

    override fun visitCastExpression(node: CSTNode) {
        val expression = node.children.last()
        writeComment("Evaluate Expression inside CastExpression")
        visit(expression)
        writeLn()

        val typeOfCast = node.getType()
        val originalType = expression.getType()

        if (node.isDowncast()) {
            writeComment("Check if downcast from \"$originalType\" to \"$typeOfCast\" is valid")
            writeComment("Move into object's SuperType table")
            writeLn("mov ebx, eax")
            writeLn("add ebx, $OBJECT_SUPER_TYPE_TABLE_OFFSET")
            writeLn("mov ebx, [ebx]")
            writeLn()

            val indexOfTypeOfCast = packageManager.getSuperTypeTable(originalType).keys.indexOf(typeOfCast)
            check(indexOfTypeOfCast != -1) { "Type of \"ReferenceType\" node not found in SuperType table" }

            writeComment("Access SuperType table entry")
            writeLn("add ebx, ${indexOfTypeOfCast * 4}")
            writeLn("mov ebx, [ebx]")
            writeLn()

            val downcastIsValid = genLabel("downcastIsValid")
            writeComment("Raise exception when downcast from \"$originalType\" to \"$typeOfCast\" is invalid")
            writeLn("cmp ebx, 1")
            writeLn("je $downcastIsValid")
            writeLn("call __exception")
            writeLn("$downcastIsValid:")
            writeLn()
        }
    }

    override fun visitPrimaryNoNewArray(node: CSTNode) {
        when (node.children[0].name) {
            "this" -> {
                val stackOffsetOfThis = 8 + formalParameterEnvironment.size * 4
                writeComment("PrimaryNoNewArray: Evaluate `this`")
                writeLn("mov eax, ebp")
                writeLn("add eax, $stackOffsetOfThis")
                writeLn("mov eax, [eax]")
            }

            "(" -> {
                writeComment("PrimaryNoNewArray: Evaluate ( Expression )")
                visit(node.getChild("Expression"))
            }

            else -> {
                writeComment("Evaluating PrimaryNoNewArray")
                super.visitPrimaryNoNewArray(node)
            }
        }
    }

    fun createClassInstance(classDeclaration: CSTNode) {
        if (classDeclaration.name != "ClassDeclaration") {
            throw CodeGenerationError("Tried to create object instance of non-class CSTNode ${classDeclaration.name}")
        }

        val numFields = packageManager.getClassOverriddenInstanceFields(classDeclaration).size
        val numBytes = OBJECT_FIELD_OFFSET + numFields * 4
        writeComment("Create \"${getDeclarationName(classDeclaration)}\" instance")
        writeLn("mov eax, $numBytes")
        writeLn("call __malloc")
        writeLn()

        val vTableLabel = packageManager.getVTableLabel(classDeclaration)
        writeComment("Setup VTable pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $OBJECT_V_TABLE_POINTER_OFFSET")
        writeLn("mov ecx, $vTableLabel")
        writeLn("mov [ebx], ecx")
        writeLn()

        val selectorIndexedTableLabel = packageManager.getSelectorIndexedTableLabel(classDeclaration)
        writeComment("Setup Selector Indexed Table pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $OBJECT_SELECTOR_INDEXED_TABLE_POINTER_OFFSET")
        writeLn("mov ecx, $selectorIndexedTableLabel")
        writeLn("mov [ebx], ecx")
        writeLn()

        val superTypeTableLabel = packageManager.getSuperTypeTableLabel(Type(classDeclaration, false))
        writeComment("Setup SuperType Table pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $OBJECT_SUPER_TYPE_TABLE_OFFSET")
        writeLn("mov ecx, $superTypeTableLabel")
        writeLn("mov [ebx], ecx")
    }

    /**
     * Assume `eax` is set to `this`.
     * Assume `ebx` is set to the method address.
     */
    fun callInstanceMethod(arguments: List<CSTNode>) {
        writeComment("Instance method call: Push `this` onto the stack")
        writeLn("push eax")
        writeLn()

        callMethod(arguments)
        writeLn()

        writeComment("Instance method call: Pop `this` off the stack")
        writeLn("add esp, 4")
    }

    /**
     * Assume `ebx` is set to the method address.
     */
    fun callMethod(arguments: List<CSTNode>) {
        if (arguments.isNotEmpty()) {
            writeComment("Pushing ${arguments.size} arguments onto the stack")

            IntRange(0, arguments.size - 1).forEach({i ->
                val argument = arguments[i]

                writeComment("Method call: Save method address (i.e: ebx) before evaluating argument $i")
                writeLn("push ebx")
                writeLn()

                writeComment("Method Call: Evaluate argument $i")
                visit(argument)
                writeLn()

                writeComment("Method call: Restore method address (i.e: ebx) after evaluating argument $i")
                writeLn("pop ebx")
                writeLn()

                writeComment("Method call: Push argument $i")
                writeLn("push eax")

                if (arguments.size - 1 != i) {
                    writeLn()
                }
            })
            writeLn()
        }

        writeComment("Call Method")
        writeLn("call ebx")
        writeLn()

        writeComment("Pop off ${arguments.size} formal parameters from stack")
        writeLn("add esp, ${arguments.size * 4}")
    }

    override fun visitClassInstanceCreationExpression(node: CSTNode) {
        val classDeclaration = node.getType().type
        val arguments = node.getChild("ArgumentListOpt").getDescendants("Expression")
        val constructorDeclaration = node.getDeclaration()
        val constructorDeclarationLabel = packageManager.getClassMemberLabel(constructorDeclaration)

        createClassInstance(classDeclaration)
        writeLn()

        val qualifiedClassName = packageManager.getQualifiedDeclarationName(classDeclaration)
        writeComment("Save \"$qualifiedClassName\" instance")
        writeLn("push eax")
        writeLn()

        writeComment("Move constructor method into ebx")
        writeLn("mov ebx, $constructorDeclarationLabel")
        writeLn()

        callInstanceMethod(arguments)
        writeLn()

        writeComment("Restore \"$qualifiedClassName\" instance from stack")
        writeLn("pop eax")
    }

    fun writeTypeAssignableCheck(type: Type) {
        writeComment("Check if object eax := \"$type\"")
        writeComment("Move to SuperType table")
        writeLn("mov ecx, eax")
        writeLn("add ecx, $OBJECT_SUPER_TYPE_TABLE_OFFSET")
        writeLn("mov ecx, [ecx]")
        writeLn()

        val indexOfTypeInSuperTable = packageManager.getSuperTypeTableKeys().indexOf(type)
        check(indexOfTypeInSuperTable != -1) { "Index of \"$type\" not found in SuperType table" }

        writeComment("Access SuperType table entry of \"$type\"")
        writeLn("add ecx, ${indexOfTypeInSuperTable * 4}")
        writeLn("mov ecx, [ecx]")
        writeLn()

        val objectIsTypeAssignable = genLabel("objectIsTypeAssignable")
        writeComment("Raise exception when object (eax) doesn't implement \"$type\"")
        writeLn("cmp ecx, 1")
        writeLn("je $objectIsTypeAssignable")
        writeLn("call __exception")
        writeLn("$objectIsTypeAssignable:")
    }

    override fun visitMethodInvocation(node: CSTNode) {
        val baseClass = packageManager.getJavaLangType("Object")!!

        val objectCloneMethod = baseClass.getDescendant({ descendant ->
            if (descendant.name != "MethodDeclaration" || getDeclarationName(descendant) != "clone") {
                false
            } else {
                val methodHeader = descendant.getChild("MethodHeader")
                val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                val formalParameterListOpt = methodDeclarator.getChild("FormalParameterListOpt")

                formalParameterListOpt.children.isEmpty()
            }
        })

        val cloneableInterface = packageManager.getJavaLangType("Cloneable")!!
        val cloneableType = Type(cloneableInterface, false)

        val writeImplementsCloneableCheck = {
            writeComment("Since calling \"java.lang.Object.clone()\", check if object implements \"$cloneableType\"")
            writeTypeAssignableCheck(cloneableType)
        }

        when (node.children[0].name) {
            "Name" -> {
                val nameNode = node.getChild("Name")
                visit(nameNode)
                writeLn()

                val arguments = node.getChild("ArgumentListOpt").getDescendants("Expression")
                val methodDeclaration = nameNode.getDeclaration()
                val methodName = serializeName(nameNode)

                if ("static" in getModifiers(methodDeclaration)) {
                    writeComment("Calling static method \"$methodName\"")
                    callMethod(arguments)
                } else {
                    writeComment("Calling instance method \"$methodName\"")
                    if (nameNode.getDeclaration() == objectCloneMethod) {
                        writeImplementsCloneableCheck()
                        writeLn()
                    }

                    callInstanceMethod(arguments)
                }
            }

            "Primary" -> {
                val primary = node.getChild("Primary")
                visit(primary)
                writeLn()

                val primaryType = primary.getType()
                val identifier = node.getChild("IDENTIFIER")
                val methodDeclaration = identifier.getDeclaration()

                getMethodAddress(primaryType, methodDeclaration)
                writeLn()

                val arguments = node.getChild("ArgumentListOpt").getDescendants("Expression")

                check("static" !in getModifiers(methodDeclaration)) {
                    "Can't have static method calls of Primary"
                }

                writeComment("Calling instance method \"$primaryType.${identifier.lexeme}\"")
                if (methodDeclaration == objectCloneMethod) {
                    writeImplementsCloneableCheck()
                    writeLn()
                }

                callInstanceMethod(arguments)
            }
        }
    }

    override fun visitBlockStatements(node: CSTNode) {
        when (node.children[0].name) {
            "BlockStatements" -> {
                visit(node.getChild("BlockStatements"))
                writeLn()
                visit(node.getChild("BlockStatement"))
            }

            else -> {
                super.visitBlockStatements(node)
            }
        }
    }

    override fun visitFieldAccess(node: CSTNode) {
        val primary = node.getChild("Primary")
        writeComment("Resolve Field")
        visit(primary)
        writeLn()

        writeNullPointerCheck()
        writeLn()

        val typeOfPrimary = primary.getType()
        val instanceFieldName = node.getChild("IDENTIFIER").lexeme

        writeComment("Access field \"$instanceFieldName\" of Primary = eax")

        if (typeOfPrimary.isArray) {
            check(instanceFieldName == "length") { "Tried to access non-length field of an array." }
            check(!isLValue(node)) { "Tried to write to an Array's length field" }
            writeLn("add eax, $ARRAY_FIELD_OFFSET")
            writeLn("mov eax, [eax]")
        } else {
            val instanceField = node.getChild("IDENTIFIER").getDeclaration()
            val instanceFields = packageManager.getClassOverriddenInstanceFields(typeOfPrimary.type)
            val indexOfField = instanceFields.indexOf(instanceField)

            check(indexOfField != -1) { "Couldn't find instance field $instanceFieldName" }

            writeLn("add eax, $OBJECT_FIELD_OFFSET")
            writeLn("add eax, ${indexOfField * 4}")

            if (!isLValue(node)) {
                writeLn("mov eax, [eax]")
            }
        }
    }

    override fun visitArrayAccess(node: CSTNode) {
        writeComment("Evaluate ${node.children[0].name}")
        visit(node.children[0])
        writeLn()

        writeNullPointerCheck()
        writeLn()

        writeComment("Save array object")
        writeLn("push eax")
        writeLn()

        val expression = node.getChild("Expression")
        writeComment("Evaluate array index inside ArrayAccess")
        visit(expression)
        writeLn()

        writeComment("Move array index into ecx")
        writeLn("mov ecx, eax")
        writeLn()

        writeComment("Restore array object into eax")
        writeLn("pop eax")
        writeLn()

        val arrayIndexPositive = genLabel("arrayIndexPositive")

        writeComment("Ensure array index is positive")
        writeLn("cmp ecx, 0")
        writeLn("jge $arrayIndexPositive")
        writeLn("call __exception")
        writeLn("$arrayIndexPositive:")
        writeLn()

        writeComment("Get array length")
        writeLn("mov edx, eax")
        writeLn("add edx, $ARRAY_FIELD_OFFSET")
        writeLn("mov edx, [edx]")
        writeLn()

        val arrayIndexInBounds = genLabel("arrayIndexInBounds")
        writeComment("Ensure array index is less than length")
        writeLn("cmp ecx, edx")
        writeLn("jl $arrayIndexInBounds")
        writeLn("call __exception")
        writeLn("$arrayIndexInBounds:")
        writeLn()

        if (isLValue(node)) {
            writeComment("In LValue, so move array address to ebx")
            writeLn("mov ebx, eax")
            writeLn()
        }

        writeComment("Get address of array element offset")
        writeLn("imul ecx, 4")
        writeLn("add ecx, $ARRAY_ELEMENT_OFFSET")
        writeLn("add eax, ecx")

        if (!isLValue(node)) {
            writeLn()

            writeComment("In RValue, so get value")
            writeLn("mov eax, [eax]")
        }
    }

    override fun visitLiteral(node: CSTNode) {
        val nonOctalEscapeSequence = mapOf(
            "\\b" to '\b'.toInt(),
            "\\t" to '\t'.toInt(),
            "\\n" to '\n'.toInt(),
            "\\f" to 12, // Form Feed
            "\\r" to '\r'.toInt(),
            "\\\"" to '"'.toInt(),
            "\\'" to '\''.toInt(),
            "\\\\" to '\\'.toInt()
        )

        when (node.children[0].name) {
            "INTEGER" -> {
                val intLiteral = node.getChild("INTEGER").lexeme
                writeLn("mov eax, $intLiteral")
            }

            "BOOLEAN" -> {
                val booleanLexeme = node.getChild("BOOLEAN").lexeme
                val booleanLiteral = if (booleanLexeme == "true") "1" else "0"
                writeLn("mov eax, $booleanLiteral")
            }

            "CHARACTER" -> {
                val charLexeme = node.getChild("CHARACTER").lexeme
                val charLiteral = charLexeme.substring(1, charLexeme.length - 1)

                val char = when (charLiteral.length) {
                    1 -> charLiteral[0].toInt()
                    else -> {
                        if (charLiteral in nonOctalEscapeSequence.keys) {
                            nonOctalEscapeSequence[charLiteral]
                        } else {
                            charLiteral.substring(1).toInt(8)
                        }
                    }
                }

                writeLn("mov eax, $char")
            }

            "NULL" -> writeLn("mov eax, 0")

            "STRING" -> {
                val stringLexeme = node.getChild("STRING").lexeme
                val stringLiteral = stringLexeme.substring(1, stringLexeme.length - 1)

                val stringChars = mutableListOf<Int>()

                var i = 0
                while (i < stringLiteral.length) {
                    val char = stringLiteral[i]
                    if (char == '\\') {
                        val potentialEscape = stringLiteral.substring(i, i + 2)
                        if (potentialEscape in nonOctalEscapeSequence.keys) {
                            stringChars.add(nonOctalEscapeSequence[potentialEscape]!!)

                            i += 2
                            continue
                        }

                        val nextThreeChars = stringLiteral.substring(i + 1, i + 4)
                        if (nextThreeChars.matches(Regex("[0-3][0-7][0-7]"))) {
                            stringChars.add(nextThreeChars.toInt(8))

                            i += 4
                            continue
                        }

                        val nextTwoChars = stringLiteral.substring(i + 1, i + 3)

                        if (nextTwoChars.matches(Regex("[0-7][0-7]"))) {
                            stringChars.add(nextTwoChars.toInt(8))
                            i += 3
                            continue
                        }

                        val nextChar = stringLiteral.substring(i + 1, i + 2)

                        if (!nextChar.matches(Regex("[0-7]"))) {
                            throw CodeGenerationError("Encountered an invalid escape sequence")
                        }

                        stringChars.add(nextChar.toInt(8))
                        i += 2
                        continue
                    }

                    stringChars.add(char.toInt())
                    i += 1
                }

                if (stringChars.isNotEmpty()) {
                    val stringLiteralLabel = genLabel("stringLiteral")
                    stringLiteralDeclarations.add("$stringLiteralLabel: dd ${stringChars.joinToString(",")}")

                    val numBytes = stringChars.size * 4 + ARRAY_ELEMENT_OFFSET
                    writeComment("Create char[] array of size ${stringChars.size}")
                    writeLn("mov eax, $numBytes")
                    writeLn("call __malloc")
                    writeLn()

                    val baseClass = packageManager.getJavaLangType("Object")
                    if (baseClass == null) {
                        throw CodeGenerationError("Couldn't find class java.lang.Object in program")
                    }

                    val vTableLabel = packageManager.getVTableLabel(baseClass)
                    writeComment("Initialize VTable pointer of char[]")
                    writeLn("mov ebx, eax")
                    writeLn("add ebx, $ARRAY_V_TABLE_POINTER_OFFSET")
                    writeLn("mov ecx, $vTableLabel")
                    writeLn("mov [ebx], ecx")
                    writeLn()

                    val superTypeTableLabel = packageManager.getSuperTypeTableLabel(Type(CSTNode("char"), true))
                    writeComment("Initialize SuperType table pointer of char[]")
                    writeLn("mov ebx, eax")
                    writeLn("add ebx, $ARRAY_SUPER_TYPE_TABLE_OFFSET")
                    writeLn("mov ecx, $superTypeTableLabel")
                    writeLn("mov [ebx], ecx")
                    writeLn()

                    // Skip Sector Indexed table

                    writeComment("Write char[] length")
                    writeLn("mov ebx, eax")
                    writeLn("add ebx, $ARRAY_FIELD_OFFSET")

                    writeLn("mov word [ebx], ${stringChars.size}")
                    writeLn()

                    writeComment("Copy string literal \"$stringLiteral\" into char[]")
                    writeLn("mov esi, $stringLiteralLabel")
                    writeLn("mov edi, eax")
                    writeLn("add edi, $ARRAY_ELEMENT_OFFSET")
                    writeLn("cld")
                    writeLn("mov ecx, ${stringChars.size * 4}")
                    writeLn("rep movsb")
                    writeLn()
                }

                val stringClass = packageManager.getJavaLangType("String")
                if (stringClass == null) {
                    throw CodeGenerationError("Couldn't find class java.lang.String in program")
                }

                if (stringChars.isNotEmpty()) {
                    writeComment("Save char[] to stack")
                    writeLn("push eax")
                    writeLn()
                }

                writeComment("Create String object")
                createClassInstance(stringClass)
                writeLn()

                val stringConstructor = stringClass.getDescendant(fun (descendant: CSTNode): Boolean {
                    if (descendant.name != "ConstructorDeclaration") {
                        return false
                    }

                    val constructorDeclarator = descendant.getChild("ConstructorDeclarator")
                    val formalParameterListOpt = constructorDeclarator.getChild("FormalParameterListOpt")
                    val formalParameterTypes = getFormalParameterTypes(formalParameterListOpt)

                    if (stringChars.isNotEmpty()) {
                       return formalParameterTypes.size == 1 && formalParameterTypes[0].isArray && formalParameterTypes[0].type.name == "char"
                    }

                    return formalParameterTypes.isEmpty()
                })
                val stringConstructorLabel = packageManager.getClassMemberLabel(stringConstructor)

                if (stringChars.isNotEmpty()) {
                    writeComment("Restore char[] pointer")
                    writeLn("pop ebx")
                    writeLn()
                }

                writeComment("Push String object for string constructor call")
                writeLn("push eax")
                writeLn()

                if (stringChars.isNotEmpty()) {
                    writeComment("Push char[] as argument to string constructor call")
                    writeLn("push ebx")
                    writeLn()
                }

                writeComment("Call string constructor")
                writeLn("call $stringConstructorLabel")
                writeLn()

                if (stringChars.isNotEmpty()) {
                    writeComment("Remove String char[] formal argument from stack")
                    writeLn("add esp, 4")
                    writeLn()
                }

                writeComment("Remove String object from formal parameters and put it into eax")
                writeLn("pop eax")
                writeLn()
            }
        }
    }

    override fun visitArrayCreationExpression(node: CSTNode) {
        visit(node.getChild("DimExpr"))

        writeLn()
        val arraySizePositive = genLabel("arraySizePositive")
        writeComment("Ensure array size is positive")
        writeLn("cmp eax, 0")
        writeLn("jge $arraySizePositive")
        writeLn("call __exception")
        writeLn("$arraySizePositive:")
        writeLn()

        writeComment("Save array length")
        writeLn("push eax")
        writeLn()

        writeComment("Create array")
        writeLn("imul eax, 4")
        writeLn("add eax, $ARRAY_ELEMENT_OFFSET")
        writeLn("call __malloc")
        writeLn()

        val baseClass = packageManager.getJavaLangType("Object")
        if (baseClass == null) {
            throw CodeGenerationError("Couldn't find class Object in program")
        }

        val vTableLabel = packageManager.getVTableLabel(baseClass)
        writeComment("Initialize array VTable pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $ARRAY_V_TABLE_POINTER_OFFSET")
        writeLn("mov ecx, $vTableLabel")
        writeLn("mov [ebx], ecx")
        writeLn()

        val typeOfNode = node.getType()

        val superTypeTableLabel = packageManager.getSuperTypeTableLabel(typeOfNode)
        writeComment("Initialize array SuperType table pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $ARRAY_SUPER_TYPE_TABLE_OFFSET")
        writeLn("mov ecx, $superTypeTableLabel")
        writeLn("mov [ebx], ecx")
        writeLn()

        val typeOfArrayElement = Type(typeOfNode.type, false)
        if (typeOfArrayElement.isReference()) {
            val indedOfArrayElementTypeInSuperTable = packageManager.getSuperTypeTableKeys().indexOf(typeOfArrayElement)
            check(indedOfArrayElementTypeInSuperTable != -1) {
                "Couldn't find type of \"$typeOfArrayElement\" in SuperType table"
            }

            writeComment("Initialize array element type")
            writeLn("mov ebx, eax")
            writeLn("add ebx, $ARRAY_ELEMENT_TYPE_OFFSET")
            writeLn("mov ecx, $indedOfArrayElementTypeInSuperTable")
            writeLn("mov [ebx], ecx")
            writeLn()
        }

        // Skip Section Indexed table

        writeComment("Retrieve array length")
        writeLn("pop edx")
        writeLn()

        writeComment("Write array length")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $ARRAY_FIELD_OFFSET")
        writeLn("mov [ebx], edx")
        writeLn()

        // Initialize elements to 0
        writeComment("Initialize array elements to 0")
        writeLn("mov ebx, 0")
        writeLn()

        val forLoop = genLabel("beginFor")
        val endForLoop = genLabel("endFor")
        writeLn("$forLoop:")
        withIndent({
            writeComment("If index (ebx) >= size (edx), exit for loop")
            writeLn("cmp ebx, edx")
            writeLn("jge $endForLoop")
            writeLn()

            writeComment("Find array element offset in bytes")
            writeLn("mov ecx, ebx")
            writeLn("imul ecx, 4")
            writeLn("add ecx, $ARRAY_ELEMENT_OFFSET")
            writeLn()

            writeComment("Calculate array element address")
            writeLn("add ecx, eax")
            writeLn()

            writeComment("Initialize array element to 0")
            writeLn("mov word [ecx], 0")

            writeLn()
            writeComment("Increment loop index and go back to the top of for")
            writeLn("add ebx, 1")
            writeLn("jmp $forLoop")
        })
        writeLn("$endForLoop:")
    }

    override fun visitIfThenStatement(node: CSTNode) {
        val expression = node.getChild("Expression")
        writeComment("Compute Expression in IfThenStatement")
        visit(expression)
        writeLn()

        val endIfLabel = genLabel("endif")
        writeComment("If check fails, goto \"$endIfLabel\"")
        writeLn("cmp eax, 0")
        writeLn("je $endIfLabel")
        writeLn()

        val statement = node.getChild("Statement")
        withIndent({
            writeComment("Execute statements inside IfThenStatement")
            visit(statement)
        })
        writeLn()

        writeLn("$endIfLabel:")
    }

    override fun visitIfThenElseStatement(node: CSTNode) {
        val expression = node.getChild("Expression")
        writeComment("Compute Expression in IfThenElseStatement")
        visit(expression)
        writeLn()

        val elseLabel = genLabel("else")
        writeComment("If check fails, goto \"$elseLabel\"")
        writeLn("cmp eax, 0")
        writeLn("je $elseLabel")
        writeLn()

        val thenStatement = node.getChild("StatementNoShortIf")
        withIndent({
            writeComment("Execute then statements inside IfThenElseStatement")
            visit(thenStatement)
        })
        writeLn()

        val endIfLabel = genLabel("endif")
        writeLn("jmp $endIfLabel")
        writeLn("$elseLabel:")

        val elseStatement = node.getChild("Statement")
        withIndent({
            writeComment("Execute else statements inside IfThenElseStatement")
            visit(elseStatement)
        })
        writeLn()

        writeLn("$endIfLabel:")
    }

    override fun visitIfThenElseStatementNoShortIf(node: CSTNode) {
        val expression = node.getChild("Expression")
        writeComment("Compute Expression in IfThenElseStatementNoShortIf")
        visit(expression)
        writeLn()

        val elseLabel = genLabel("else")
        writeLn("cmp eax, 0")
        writeLn("je $elseLabel")
        writeLn()

        val thenStatement = node.children[4]
        withIndent({
            writeComment("Execute then statements inside IfThenElseStatementNoShortIf")
            visit(thenStatement)
        })
        writeLn()

        val endIfLabel = genLabel("endif")
        writeLn("jmp $endIfLabel")
        writeLn("$elseLabel:")

        val elseStatement = node.children[6]
        withIndent({
            writeComment("Execute else statements inside IfThenElseStatementNoShortIf")
            visit(elseStatement)
        })
        writeLn()

        writeLn("$endIfLabel:")
    }

    override fun visitWhileStatement(node: CSTNode) {
        val endWhileLabel = genLabel("endWhile")
        val beginWhileLabel = genLabel("beginWhile")

        writeLn("$beginWhileLabel:")
        val expression = node.getChild("Expression")
        writeComment("Compute Expression in WhileStatement")
        visit(expression)
        writeLn()

        writeComment("Check termination condition")
        writeLn("cmp eax, 0")
        writeLn("je $endWhileLabel")
        writeLn()

        val statement = node.getChild("Statement")
        withIndent({
            writeComment("Execute statements inside WhileStatement")
            visit(statement)
            writeLn()
            writeLn("jmp $beginWhileLabel")
        })
        writeLn()
        writeLn("$endWhileLabel:")
    }

    override fun visitWhileStatementNoShortIf(node: CSTNode) {
        val endWhileLabel = genLabel("endWhile")
        val beginWhileLabel = genLabel("beginWhile")

        writeLn("$beginWhileLabel:")
        val expression = node.getChild("Expression")
        writeComment("Compute Expression in WhileStatementNoShortIf")
        visit(expression)
        writeLn()

        writeComment("Check termination condition")
        writeLn("cmp eax, 0")
        writeLn("je $endWhileLabel")
        writeLn()

        val statement = node.getChild("StatementNoShortIf")
        withIndent({
            writeComment("Execute statements inside WhileStatementNoShortIf")
            visit(statement)
            writeLn()

            writeLn("jmp $beginWhileLabel")
        })
        writeLn()
        writeLn("$endWhileLabel:")
    }

    override fun visitForStatement(node: CSTNode) {
        val beginForLabel = genLabel("beginFor")
        val endForLabel = genLabel("endFor")

        val numVariables = localVariableDeclarationEnvironment.size

        localVariableDeclarationEnvironment.withNewScope({
            writeComment("For loop initializer")
            val forInitOpt = node.getChild("ForInitOpt")
            visit(forInitOpt)
            writeLn()

            writeLn("$beginForLabel:")
            withIndent({
                val expressions = node.getChild("ExpressionOpt").getDescendants("Expression")
                if (expressions.isNotEmpty()) {
                    writeComment("Compute Expression in ForStatement")
                    visit(expressions.first())
                    writeLn()

                    writeComment("Check termination condition")
                    writeLn("cmp eax, 0")
                    writeLn("je $endForLabel")
                    writeLn()
                }

                writeComment("Visit Statement inside for loop")
                val statement = node.getChild("Statement")
                visit(statement)
                writeLn()

                writeComment("Update for loop variables")
                val forUpdateOpt = node.getChild("ForUpdateOpt")
                visit(forUpdateOpt)
                writeLn()

                writeLn("jmp $beginForLabel")
            })

            writeLn("$endForLabel:")
            writeLn()

            writeComment("Pop for loop variables from stack")
            check(localVariableDeclarationEnvironment.size - numVariables >= 0) { "It's not possible to remove variables from the stack inside ForStatement" }
            writeLn("add esp, ${(localVariableDeclarationEnvironment.size - numVariables) * 4}")
        })
    }

    override fun visitForStatementNoShortIf(node: CSTNode) {
        val beginForLabel = genLabel("beginFor")
        val endForLabel = genLabel("endFor")

        val numVariables = localVariableDeclarationEnvironment.size

        localVariableDeclarationEnvironment.withNewScope({
            writeComment("For loop initializer")
            val forInitOpt = node.getChild("ForInitOpt")
            visit(forInitOpt)
            writeLn()

            writeLn("$beginForLabel:")
            withIndent({
                val expressions = node.getChild("ExpressionOpt").getDescendants("Expression")
                if (expressions.isNotEmpty()) {
                    writeComment("Compute Expression in ForStatement")
                    visit(expressions.first())
                    writeLn()

                    writeComment("Check termination condition")
                    writeLn("cmp eax, 0")
                    writeLn("je $endForLabel")
                    writeLn()
                }

                writeComment("Visit Statement inside for loop")
                val statementNoShortIf = node.getChild("StatementNoShortIf")
                visit(statementNoShortIf)
                writeLn()

                writeComment("Update for loop variables")
                val forUpdateOpt = node.getChild("ForUpdateOpt")
                visit(forUpdateOpt)
                writeLn()

                writeLn("jmp $beginForLabel")
            })

            writeLn("$endForLabel:")
            writeLn()

            writeComment("Pop for loop variables from stack")
            check(localVariableDeclarationEnvironment.size - numVariables >= 0) { "It's not possible to remove variables from the stack inside ForStatement" }
            writeLn("add esp, ${(localVariableDeclarationEnvironment.size - numVariables) * 4}")
        })
    }

    override fun visitMethodDeclaration(node: CSTNode) {
        if ("native" in getModifiers(node)) {
            return
        }

        currentMethodDeclaration = node

        formalParameterEnvironment.withNewScope({
            withAssemblyMethodDeclaration(node, {
                super.visitMethodDeclaration(node)
            })
            writeLn()
        })

        currentMethodDeclaration = null
    }

    override fun visitBlock(node: CSTNode) {
        localVariableDeclarationEnvironment.withNewScope({
            val originalNumVariables = localVariableDeclarationEnvironment.size

            writeComment("Visiting a block")
            withIndent({
                writeLn()
                super.visitBlock(node)
                writeLn()

                val currentNumVariables = localVariableDeclarationEnvironment.size

                check(currentNumVariables >= originalNumVariables) {
                    "Number of variables in scope at end of block is less than number of variables in scope at its beginning"
                }

                writeComment("Block postamble")
                writeLn("add esp, ${(currentNumVariables - originalNumVariables) * 4}")
            })
        })
    }

    override fun visitFormalParameter(node: CSTNode) {
        formalParameterEnvironment.push(getDeclarationName(node), node)
        super.visitFormalParameter(node)
    }

    override fun visitLocalVariableDeclaration(node: CSTNode) {
        val variableName = getDeclarationName(node)
        localVariableDeclarationEnvironment.push(variableName, node)

        writeComment("Execute initializer of local variable \"$variableName\"")
        val variableInitializer = node.getDescendant("VariableInitializer")
        visit(variableInitializer)
        writeLn()

        writeComment("Push local variable \"$variableName\" into stack")
        writeLn("push eax")
        writeLn()
    }

    override fun visitInterfaceDeclaration(node: CSTNode) {
        super.visitInterfaceDeclaration(node)

        writeComment("Inside interface ${getDeclarationName(node)}")
    }

    override fun visitLeftHandSide(node: CSTNode) {
        leftHandSideStack.push(node)
        super.visitLeftHandSide(node)
        leftHandSideStack.pop()
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

    companion object {
        private val counters = mutableMapOf<String, Int>()
        fun genLabel(prefix: String): String {
            val counter = counters.getOrDefault(prefix, 0)
            val label = "$prefix$counter"
            counters[prefix] = counter + 1
            return label
        }
    }
}
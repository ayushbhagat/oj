package oj.codegenerator

import oj.models.*

const val OBJECT_V_TABLE_POINTER_OFFSET = 0
const val OBJECT_SELECTOR_INDEXED_TABLE_POINTER_OFFSET = 4
const val OBJECT_SUPER_TYPE_TABLE_OFFSET = 8
const val OBJECT_FIELD_OFFSET = 12

const val ARRAY_V_TABLE_POINTER_OFFSET = 0
const val ARRAY_SELECTOR_INDEXED_TABLE_POINTER_OFFSET = 4
const val ARRAY_SUPER_TYPE_TABLE_OFFSET = 8
const val ARRAY_FIELD_OFFSET = 12
const val ARRAY_ELEMENT_OFFSET = 16

class CodeGenerationVisitor(
    private val packageManager: PackageManager
) : CSTNodeVisitor() {
    open class CodeGenerationError(reason: String): Exception(reason)
    open class UnimplementedFeature(reason: String): CodeGenerationError(reason)

    private val asmWriter = ASMWriter()
    private val localVariableDeclarationEnvironment = Environment()
    private val formalParameterEnvironment = Environment()
    private var currentClassDeclaration: CSTNode? = null
    private var currentLeftHandSide: CSTNode? = null
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
            packageManager.packages.values
                .flatMap({ compilationUnits -> compilationUnits })
                .map({ compilationUnit -> compilationUnit.getDescendant(isClassOrInterfaceDeclaration) })
                .filter({ type -> type.name == "ClassDeclaration" })
                .filter({ classDeclaration -> classDeclaration != node })
                .forEach({ classDeclaration ->
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
            withAssemblyMethodDeclaration(classStaticFieldInitializerLabel, {
                staticFields.forEach({ staticField ->
                    if (staticField.children[2].name == "VariableDeclarator") {
                        val staticFieldVariableInitializer = staticField.getDescendant("VariableInitializer")
                        writeComment("Initializer for ${getDeclarationName(staticField)}")
                        withIndent({
                            visit(staticFieldVariableInitializer)
                        })
                        val staticFieldLabel = packageManager.getClassMemberLabel(staticField)
                        writeLn("mov [$staticFieldLabel], eax")
                    }
                })
            })

            /**
             * Instance field initializers
             */
            writeLn()
            val instanceFields = packageManager.getClassOverriddenInstanceFields(node)
            val classInstanceFieldInitializerLabel = packageManager.getInstanceFieldInitializerLabel(node)
            withAssemblyMethodDeclaration(classInstanceFieldInitializerLabel, {
                instanceFields
                    .zip(IntRange(0, instanceFields.size - 1))
                    .forEach({(instanceField, i) ->
                        if (instanceField.children[2].name == "VariableDeclarator") {
                            val instanceFieldVariableInitializer = instanceField.getDescendant("VariableInitializer")
                            writeComment("Initializer for ${getDeclarationName(instanceField)}")
                            withIndent({
                                visit(instanceFieldVariableInitializer)
                            })
                            // This assumes that "this" will be pushed as the first argument to the stack when this label is called.
                            writeLn("mov ebx, ebp")
                            writeLn("add ebx, 8")
                            writeLn("mov ebx, [ebx]")
                            writeLn("add ebx, ${OBJECT_FIELD_OFFSET + i * 4}")
                            writeLn("mov [ebx], eax")
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
                    writeLn("$staticFieldLabel: dd 0")
                })
            })
            writeLn()

            /**
             * Write VTable
             */
            val vTableLabel = packageManager.getVTableLabel(node)
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
                writeLn("$superTypeTableLabel:")
                withIndent({
                    val superTypeTable = packageManager.getSuperTypeTable(type)

                    superTypeTable.forEach({(_, isSuperType) ->
                        writeLn("dd ${if (isSuperType) 1 else 0}")
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
        super.visitReturnStatement(node)

        writeLn("add esp, ${localVariableDeclarationEnvironment.size * 4}")
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
            writeLn("push ebp")
            writeLn("mov ebp, esp")
            fn()
            writeLn("pop ebp")
            writeLn("ret")
        })
    }

    override fun visitConstructorDeclaration(node: CSTNode) {
        formalParameterEnvironment.withNewScope({
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

                    writeLn("mov eax, ebp")
                    writeLn("add eax, $stackOffsetOfThis")

                    callInstanceMethod(parentZeroArgumentConstructorLabel, listOf())
                }

                val instanceFieldInitializerMethod = packageManager.getInstanceFieldInitializerLabel(currentClassDeclaration)
                val stackOffsetOfThis = 8 + formalParameterEnvironment.size * 4

                writeLn("mov eax, ebp")
                writeLn("add eax, $stackOffsetOfThis")

                callInstanceMethod(instanceFieldInitializerMethod, listOf())

                super.visit(node.getChild("ConstructorBody"))
            })
        })
    }

    fun getCurrentClassDeclaration(): CSTNode {
        return currentClassDeclaration!!
    }

    override fun visitAssignmentExpression(node: CSTNode) {
        when (node.children[0].name) {
            "Assignment" -> {
                val assignmentExpression = node.getChild("AssignmentExpression")
                writeComment("Evaluate Assignment Expression")
                visit(assignmentExpression)
                writeLn("push eax")

                val leftHandSide = node.getChild("LeftHandSide")
                writeComment("Get Assignment LHS")
                visit(leftHandSide)

                writeComment("Write assignment RHS to LHS")
                writeLn("pop ebx")
                writeLn("mov [eax], ebx")

                writeComment("Return value written into assignment")
                writeLn("mov eax, [eax]")
            }
            "ConditionalOrExpression" -> {
                visit(node.getChild("ConditionalOrExpression"))
            }
        }
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

                visit(conditionalOrExpression)
                writeLn("cmp eax, 1")
                writeLn("je $endLabel")
                visit(conditionalAndExpression)
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

                visit(conditionalAndExpression)
                writeLn("cmp eax, 0")
                writeLn("je $endLabel")
                visit(orExpression)
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
                val endLabel = genLabel("end")

                visit(orExpression)
                writeLn("cmp eax, 1")
                writeLn("je $endLabel")
                visit(andExpression)
                writeLn("$endLabel:")
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
                val endLabel = genLabel("end")

                visit(andExpression)
                writeLn("cmp eax, 0")
                writeLn("je $endLabel")
                visit(equalityExpression)
                writeLn("$endLabel:")
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
            visit(node.getChild("EqualityExpression"))
        } else {
            val equalityExpression = node.getChild("EqualityExpression")
            val relationalExpression = node.getChild("RelationalExpression")

            visit(equalityExpression)
            writeLn("push eax")
            visit(relationalExpression)
            writeLn("pop ebx")

            val operator = node.children[1].name

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
        visit(relationalExpression)

        val operator = node.children[1].name
        if (operator in setCCInstructionMap.keys) {
            val additiveExpression = node.getChild("AdditiveExpression")
            writeLn("push eax")
            visit(additiveExpression)
            writeLn("pop ebx")

            writeLn("cmp ebx, eax")
            writeLn("${setCCInstructionMap[operator]} al")
            writeLn("movzx eax, al")
            return
        }

        writeComment("Move to object's SuperType table")
        writeLn("add eax, $OBJECT_SUPER_TYPE_TABLE_OFFSET")
        writeLn("mov eax, [eax]")

        val referenceType = node.getChild("ReferenceType")
        val typeOfReferenceType = referenceType.getType()
        val indexOfReferenceType = packageManager.getSuperTypeTable(relationalExpression.getType()).keys.indexOf(typeOfReferenceType)

        check(indexOfReferenceType != -1) { "Type of \"ReferenceType\" node not found in SuperType table" }

        writeComment("Calculate instanceof $typeOfReferenceType")
        writeLn("add eax, ${indexOfReferenceType * 4}")
        writeLn("mov eax, [eax]")
    }

    override fun visitAdditiveExpression(node: CSTNode) {
        if (node.children[0].name == "MultiplicativeExpression") {
            visit(node.getChild("MultiplicativeExpression"))
            return
        }

        val operator = node.children[1].lexeme
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
                writeLn()
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
                    writeLn()
                    return
                }

                val stringClass = packageManager.getJavaLangType("String")
                val stringType = Type(stringClass!!, false)

                writeComment("Calculating AdditiveExpression")
                visit(additiveExpression)
                writeLn()

                writeComment("Save AdditiveExpression result on stack")
                writeLn("push eax")
                writeLn()

                writeComment("Calculate MultiplicativeExpression")
                visit(multiplicativeExpression)
                writeLn()

                writeComment("Restore AdditiveExpression from stack")
                writeLn("pop ebx")
                writeLn()

                val expressionToConvertToString =
                    if (typeOfAdditiveExpression != stringType) {
                        additiveExpression
                    } else if (typeOfMultiplicativeExpression != stringType) {
                        multiplicativeExpression
                    } else {
                        null
                    }

                if (expressionToConvertToString != null) {
                    val stringValueOfMethod = stringClass.getDescendant({ descendant ->
                        if (descendant.name != "MethodDeclaration") {
                            false
                        } else {
                            val methodHeader = descendant.getChild("MethodHeader")
                            val methodDeclarator = methodHeader.getChild("MethodDeclarator")
                            val formalParameterListOpt = methodDeclarator.getChild("FormalParameterListOpt")
                            val formalParameterTypes = getFormalParameterTypes(formalParameterListOpt)
                            formalParameterTypes.size == 1 &&
                                packageManager.colonEquals(formalParameterTypes[0], expressionToConvertToString.getType()) &&
                                getDeclarationName(descendant) == "valueOf"
                        }
                    })


                    if (expressionToConvertToString == additiveExpression) {
                        writeComment("Save MultiplicativeExpression")
                        writeLn("push eax")
                        writeLn()

                        writeComment("Push AdditiveExpression as argument to String.valueOf")
                        writeLn("push ebx")
                        writeLn()
                    } else {
                        writeComment("Save AdditiveExpression")
                        writeLn("push ebx")
                        writeLn()

                        writeComment("Push MultiplicativeExpression as argument to String.valueOf")
                        writeLn("push eax")
                        writeLn()
                    }

                    val stringValueOfMethodLabel = packageManager.getClassMemberLabel(stringValueOfMethod)
                    writeLn("call $stringValueOfMethodLabel")
                    writeLn("add esp, 4")

                    if (expressionToConvertToString == additiveExpression) {
                        writeComment("Just converted AdditiveExpression, move it to ebx")
                        writeLn("mov ebx, eax")
                        writeLn()

                        writeComment("MultiplicativeExpression was saved on stack, moving to eax")
                        writeLn("pop eax")
                        writeLn()
                    } else {
                        writeComment("Just converted MultiplicativeExpression, it's already in eax")
                        writeLn()

                        writeComment("AdditiveExpression was saved on stack, moving to ebx")
                        writeLn("pop ebx")
                        writeLn()
                    }
                }

                writeComment("State: ebx = AdditiveExpression and eax = MultiplicativeExpression")
                writeLn()

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
                writeLn()

                writeComment("Call String.concat")
                writeLn("call ebx")
                writeLn("add esp, 8")
                writeLn()
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

        visit(multiplicativeExpression)
        writeLn("push eax")
        visit(unaryExpression)
        writeLn("pop ebx")

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

        if (!type.isArray && methodDeclaration.name == "AbstractMethodDeclaration") {
            val allInterfaceMethodsInProgram = packageManager.getAllInterfaceMethodsInProgram()
            val selectorIndexedTableIndex = allInterfaceMethodsInProgram.indexOfFirst({ canMethodsReplaceOneAnother(it, methodDeclaration) })

            if (selectorIndexedTableIndex == -1) {
                val methodDeclarationName = getDeclarationName(methodDeclaration)
                throw CodeGenerationError("Instance method $methodDeclarationName not found in selector indexed table")
            }

            writeLn("mov ebx, eax")
            writeLn("add ebx, $OBJECT_SELECTOR_INDEXED_TABLE_POINTER_OFFSET")
            writeLn("mov ebx, [ebx]")
            writeLn("add ebx, ${4 * selectorIndexedTableIndex}")
        } else {
            val classDeclaration = if (type.isArray) packageManager.getJavaLangType("Object")!! else type.type
            val instanceMethods = packageManager.getClassOverriddenInstanceMethods(classDeclaration)
            val methodVTableIndex = instanceMethods.indexOfFirst({ canMethodsReplaceOneAnother(it, methodDeclaration) })

            if (methodVTableIndex == -1) {
                val methodDeclarationName = getDeclarationName(methodDeclaration)
                val className = getDeclarationName(type.type)

                throw CodeGenerationError("Instance method $methodDeclarationName not found in instance methods of class $className")
            }

            writeLn("mov ebx, eax")
            writeLn("add ebx, $OBJECT_V_TABLE_POINTER_OFFSET")
            writeLn("mov ebx, [ebx]")
            writeLn("add ebx, ${4 * methodVTableIndex}")
        }
    }

    override fun visitName(node: CSTNode) {
        when (node.getNameNodeClassification()) {

            NameNodeClassification.Expression -> {
                when(node.children[0].name) {

                    "QualifiedName" -> {
                        val qualifiedName = node.getChild("QualifiedName")
                        val prefixName = qualifiedName.getChild("Name")

                        visit(prefixName)

                        when (prefixName.getNameNodeClassification()!!) {

                            NameNodeClassification.Expression -> {
                                val instanceFieldDeclaration = node.getDeclaration()

                                if (instanceFieldDeclaration.name != "FieldDeclaration") {
                                    throw CodeGenerationError(
                                        "Expected name ${serializeName(node)} to resolve to FieldDeclaration, but it resolved to a \"${instanceFieldDeclaration.name}\""
                                    )
                                }


                                writeLn("add eax, $OBJECT_FIELD_OFFSET")

                                val typeOfPrefixName = prefixName.getType()

                                if (typeOfPrefixName.isArray) {
                                    throw UnimplementedFeature("We don't yet support Array.length access")
                                }

                                val instanceFields = packageManager.getClassOverriddenInstanceFields(typeOfPrefixName.type)
                                val fieldIndex = instanceFields.indexOf(instanceFieldDeclaration)

                                if (fieldIndex == -1) {
                                    val fieldDeclarationName = getDeclarationName(instanceFieldDeclaration)
                                    val className = getDeclarationName(typeOfPrefixName.type)

                                    throw CodeGenerationError("Instance field $fieldDeclarationName not found in instance methods of class $className")
                                }

                                writeLn("add eax, ${fieldIndex * 4}")

                                if (!isLValue(node)) {
                                    writeLn("mov eax, [eax]")
                                }
                            }

                            NameNodeClassification.Type -> {
                                val staticFieldDeclaration = node.getDeclaration()

                                if (staticFieldDeclaration.name != "FieldDeclaration") {
                                    throw CodeGenerationError(
                                        "Expected name ${serializeName(node)} to resolve to FieldDeclaration, but it resolved to a \"${staticFieldDeclaration.name}\""
                                    )
                                }

                                val classMemberLabel = packageManager.getClassMemberLabel(staticFieldDeclaration)
                                writeLn("mov eax, $classMemberLabel")

                                if (!isLValue(node)) {
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

                        if (nameNodeDeclaration.name == "FormalParameter") {
                            val formalParameterIndex = formalParameterEnvironment.findIndex(getDeclarationName(nameNodeDeclaration))
                            val formalParameterStackOffset = 8 + (formalParameterEnvironment.size - (formalParameterIndex + 1)) * 4
                            writeLn("mov eax, $formalParameterStackOffset")
                            writeLn("add eax, ebp")

                            if (!isLValue(node)) {
                                writeLn("mov eax, [eax]")
                            }

                        } else if (nameNodeDeclaration.name == "LocalVariableDeclaration") {
                            val localVariableIndex = localVariableDeclarationEnvironment.findIndex(getDeclarationName(nameNodeDeclaration))
                            val localVariableStackOffset = -(localVariableIndex + 1) * 4
                            writeLn("mov eax, $localVariableStackOffset")
                            writeLn("add eax, ebp")

                            if (!isLValue(node)) {
                                writeLn("mov eax, [eax]")
                            }

                        } else if (nameNodeDeclaration.name == "FieldDeclaration") {
                            val stackOffsetOfThis = 8 + formalParameterEnvironment.size * 4
                            writeLn("mov eax, $stackOffsetOfThis")
                            writeLn("add eax, ebp")
                            writeLn("mov eax, [eax]")
                            writeLn("add eax, $OBJECT_FIELD_OFFSET")

                            val instanceFields = packageManager.getClassOverriddenInstanceFields(getCurrentClassDeclaration())
                            val fieldIndex = instanceFields.indexOf(nameNodeDeclaration)

                            if (fieldIndex == -1) {
                                val fieldDeclarationName = getDeclarationName(nameNodeDeclaration)
                                val className = getDeclarationName(getCurrentClassDeclaration())
                                throw CodeGenerationError("Instance method $fieldDeclarationName not found in instance methods of class $className")
                            }

                            writeLn("add eax, ${fieldIndex * 4}")

                            if (!isLValue(node)) {
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
                        writeLn("mov eax, $stackOffsetOfThis")
                        writeLn("add eax, ebp")

                        writeLn("mov ebx, [eax]")
                        writeLn("add ebx, $OBJECT_V_TABLE_POINTER_OFFSET")
                        writeLn("mov ebx, [ebx]")
                        writeLn("add ebx, ${4 * methodVTableIndex}")
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
        visit(expression)

        val typeOfCast = node.getType()
        val originalType = expression.getType()

        if (node.isDowncast()) {
            writeComment("Check if downcast from \"$originalType\" to \"$typeOfCast\" is valid")
            writeComment("Move to object's SuperType table")
            writeLn("mov ebx, eax")
            writeLn("add ebx, $OBJECT_SUPER_TYPE_TABLE_OFFSET")
            writeLn("mov ebx, [ebx]")
            writeLn()

            val indexOfTypeOfCast = packageManager.getSuperTypeTable(originalType).keys.indexOf(typeOfCast)
            check(indexOfTypeOfCast != -1) { "Type of \"ReferenceType\" node not found in SuperType table" }

            writeComment("Access SuperType table of object")
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
        when (node.name) {
            "this" -> {
                val stackOffsetOfThis = 8 + formalParameterEnvironment.size * 4
                writeLn("mov eax, ebp")
                writeLn("add eax, $stackOffsetOfThis")
                writeLn("mov eax, [eax]")
            }

            "(" -> {
                visit(node.getChild("Expression"))
            }

            else -> {
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

        val vTableLabel = packageManager.getVTableLabel(classDeclaration)
        writeComment("Setup VTable pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $OBJECT_V_TABLE_POINTER_OFFSET")
        writeLn("mov [ebx], $vTableLabel")

        val selectorIndexedTableLabel = packageManager.getSelectorIndexedTableLabel(classDeclaration)
        writeComment("Setup Selector Indexed Table pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $OBJECT_SELECTOR_INDEXED_TABLE_POINTER_OFFSET")
        writeLn("mov [ebx], $selectorIndexedTableLabel")

        val superTypeTableLabel = packageManager.getSuperTypeTableLabel(Type(classDeclaration, false))
        writeComment("Setup SuperType Table pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $OBJECT_SUPER_TYPE_TABLE_OFFSET")
        writeLn("mov [ebx], $superTypeTableLabel")
    }

    /**
     * Assume `eax` is set to `this`.
     */
    fun callInstanceMethod(methodLabel: String, arguments: List<CSTNode>) {
        writeLn("push eax")
        callStaticMethod(methodLabel, arguments)
        writeLn("add esp, 4")
    }

    fun callStaticMethod(methodLabel: String, arguments: List<CSTNode>) {
        arguments.forEach({ argument ->
            visit(argument)
            writeLn("push eax")
        })

        writeLn("call $methodLabel")
        writeLn("add esp, ${arguments.size * 4}")
    }

    override fun visitClassInstanceCreationExpression(node: CSTNode) {
        val classDeclaration = node.getType().type
        val arguments = node.getChild("ArgumentListOpt").getDescendants("Expression")
        val constructorDeclaration = node.getDeclaration()
        val constructorDeclarationLabel = packageManager.getClassMemberLabel(constructorDeclaration)

        createClassInstance(classDeclaration)
        callInstanceMethod(constructorDeclarationLabel, arguments)
    }

    override fun visitMethodInvocation(node: CSTNode) {
        when (node.children[0].name) {
            "Name" -> {
                val nameNode = node.getChild("Name")
                visit(nameNode)

                val arguments = node.getChild("ArgumentsListOpt").getDescendants("Expression")
                val methodDeclaration = nameNode.getDeclaration()

                if ("static" in getModifiers(methodDeclaration)) {
                    callStaticMethod("ebx", arguments)
                } else {
                    callInstanceMethod("ebx", arguments)
                }
            }

            "Primary" -> {
                val primary = node.getChild("Primary")
                visit(primary)

                val identifier = node.getChild("IDENTIFIER")
                val methodDeclaration = identifier.getDeclaration()
                getMethodAddress(primary.getType(), methodDeclaration)

                val arguments = node.getChild("ArgumentsListOpt").getDescendants("Expression")
                if ("static" in getModifiers(methodDeclaration)) {
                    callStaticMethod("ebx", arguments)
                } else {
                    callInstanceMethod("ebx", arguments)
                }
            }
        }
    }

    override fun visitFieldAccess(node: CSTNode) {
        val primary = node.getChild("Primary")
        visit(primary)

        val instanceField = node.getDeclaration()
        val typeOfPrimary = primary.getType()
        val instanceFieldName = node.getChild("IDENTIFIER").lexeme

        if (typeOfPrimary.isArray) {
            check(instanceFieldName == "length") { "Tried to access non-length field of an array." }
            check(!isLValue(node)) { "Tried to write to an Array's length field" }
            writeLn("add eax, $ARRAY_FIELD_OFFSET")
            writeLn("mov eax, [eax]")
        } else {
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
        val expression = node.getChild("Expression")
        visit(expression)

        val arrayIndexPositive = genLabel("arrayIndexPositive")

        writeComment("Ensure array index is positive")
        writeLn("cmp eax, 0")
        writeLn("jge $arrayIndexPositive")
        writeLn("call __exception")
        writeLn("$arrayIndexPositive:")

        writeComment("Save array index")
        writeLn("push eax")

        visit(node.children[0])

        writeComment("Retrieve array index")
        writeLn("pop ebx")

        writeComment("Get array length")
        writeLn("mov edx, eax")
        writeLn("add edx, $ARRAY_FIELD_OFFSET")
        writeLn("mov edx, [edx]")

        val arrayIndexInBounds = genLabel("arrayIndexInBounds")
        writeComment("Ensure array index is less than length")
        writeLn("cmp ebx, edx")
        writeLn("jl $arrayIndexInBounds")
        writeLn("call __exception")
        writeLn("$arrayIndexInBounds:")

        writeComment("Get address of array element offset")
        writeLn("imul ebx, 4")
        writeLn("add ebx, $ARRAY_ELEMENT_OFFSET")
        writeLn("add eax, ebx")

        if (!isLValue(node)) {
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

                val stringLiteralLabel = genLabel("stringLiteral")
                stringLiteralDeclarations.add("$stringLiteralLabel: dw ${stringChars.joinToString(",")}")

                val numBytes = stringChars.size * 4 + ARRAY_ELEMENT_OFFSET
                writeLn("mov eax, $numBytes")
                writeLn("call __malloc")

                val baseClass = packageManager.getJavaLangType("Object")
                if (baseClass == null) {
                    throw CodeGenerationError("Couldn't find class java.lang.Object in program")
                }

                val vTableLabel = packageManager.getVTableLabel(baseClass)
                writeComment("Initialize VTable pointer")
                writeLn("mov ebx, eax")
                writeLn("add ebx, $ARRAY_V_TABLE_POINTER_OFFSET")
                writeLn("mov [ebx], $vTableLabel")

                val superTypeTableLabel = packageManager.getSuperTypeTableLabel(Type(CSTNode("char"), true))
                writeComment("Initialize SuperType table pointer")
                writeLn("mov ebx, eax")
                writeLn("add ebx, $ARRAY_SUPER_TYPE_TABLE_OFFSET")
                writeLn("mov [ebx], $superTypeTableLabel")

                // Skip Section Indexed table

                writeComment("Write array length")
                writeLn("mov ebx, eax")
                writeLn("add ebx, $ARRAY_FIELD_OFFSET")
                writeLn("mov [ebx], ${stringChars.size}")

                writeComment("Copy string literal \"$stringLexeme\" into char array")
                writeLn("mov esi, $stringLiteralLabel")
                writeLn("mov edi, eax")
                writeLn("add edi, $ARRAY_ELEMENT_OFFSET")
                writeLn("cld")
                writeLn("mov ecx, ${stringChars.size * 4}")
                writeLn("rep movsb")

                val stringClass = packageManager.getJavaLangType("String")
                if (stringClass == null) {
                    throw CodeGenerationError("Couldn't find class java.lang.String in program")
                }

                writeComment("Save char array")
                writeLn("push eax")

                writeComment("Create String object")
                createClassInstance(stringClass)

                val charArrayConstructor = stringClass.getDescendant({ descendant ->
                    if (descendant.name != "ConstructorDeclaration") {
                        false
                    } else {
                        val constructorDeclarator = descendant.getChild("ConstructorDeclarator")
                        val formalParameterListOpt = constructorDeclarator.getChild("FormalParameterListOpt")
                        val formalParameterTypes = getFormalParameterTypes(formalParameterListOpt)
                        formalParameterTypes.size == 1 && formalParameterTypes[0].isArray && formalParameterTypes[0].type.name == "char"
                    }
                })
                val charArrayConstructorLabel = packageManager.getClassMemberLabel(charArrayConstructor)

                writeComment("Restore char array pointer")
                writeLn("pop ebx")
                writeLn()

                writeComment("Push arguments for char[] constructor call to String")
                writeLn("push eax")
                writeLn("push ebx")
                writeLn("call $charArrayConstructorLabel")
                writeLn("add esp, 8")
                writeLn()
            }
        }
    }

    override fun visitArrayCreationExpression(node: CSTNode) {
        visit(node.getChild("DimExpr"))

        val createArray = genLabel("createArray")
        writeComment("Ensure array size is positive")
        writeLn("cmp eax, 0")
        writeLn("jge $createArray")
        writeLn("call __exception")
        writeLn("$createArray:")

        writeComment("Save array length")
        writeLn("push eax")

        writeComment("Create array")
        writeLn("imul eax, 4")
        writeLn("add eax, $ARRAY_ELEMENT_OFFSET")
        writeLn("call __malloc")

        val baseClass = packageManager.getJavaLangType("Object")
        if (baseClass == null) {
            throw CodeGenerationError("Couldn't find class Object in program")
        }

        val vTableLabel = packageManager.getVTableLabel(baseClass)
        writeComment("Initialize VTable pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $ARRAY_V_TABLE_POINTER_OFFSET")
        writeLn("mov [ebx], $vTableLabel")

        val superTypeTableLabel = packageManager.getSuperTypeTableLabel(node.getType())
        writeComment("Initialize SuperType table pointer")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $ARRAY_SUPER_TYPE_TABLE_OFFSET")
        writeLn("mov [ebx], $superTypeTableLabel")

        // Skip Section Indexed table

        writeComment("Retrieve array length")
        writeComment("pop edx")

        writeComment("Write array length")
        writeLn("mov ebx, eax")
        writeLn("add ebx, $ARRAY_FIELD_OFFSET")
        writeLn("mov [ebx], edx")

        // Initialize elements to 0
        writeLn("mov ebx, 0")
        val forLoop = genLabel("for")
        val endForLoop = genLabel("endFor")

        writeLn("$forLoop:")
        withIndent({
            writeComment("If index (ebx) >= size (edx), exit for loop")
            writeLn("cmp ebx, edx")
            writeLn("jge $endForLoop")

            writeComment("Find array element offset in bytes")
            writeLn("mov ecx, ebx")
            writeLn("imul ecx 4")
            writeLn("add ecx, $ARRAY_ELEMENT_OFFSET")

            writeComment("Calculate array element address")
            writeLn("add ecx, eax")

            writeComment("Initialize array element to 0")
            writeLn("mov [ecx], 0")

            writeLn()
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
        writeLn("cmp eax, 0")
        writeLn("je $endIfLabel")

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
        writeLn("cmp eax, 0")
        writeLn("je $elseLabel")

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

        val statement = node.getChild("Statement")
        withIndent({
            writeComment("Execute statements inside WhileStatement")
            visit(statement)
        })
        writeLn()

        writeLn("jmp $beginWhileLabel")
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

        val statement = node.getChild("StatementNoShortIf")
        withIndent({
            writeComment("Execute statements inside WhileStatementNoShortIf")
            visit(statement)
        })
        writeLn()

        writeLn("jmp $beginWhileLabel")
        writeLn("$endWhileLabel:")
    }

    override fun visitForStatement(node: CSTNode) {
        val beginForLabel = genLabel("beginFor")
        val endForLabel = genLabel("endFor")

        val numVariables = localVariableDeclarationEnvironment.size

        writeComment("For loop initializer")
        val forInitOpt = node.getChild("ForInitOpt")
        visit(forInitOpt)
        writeLn()

        writeLn("$beginForLabel:")

        val expressions = node.getChild("ExpressionOpt").getDescendants("Expression")
        if (expressions.isNotEmpty()) {
            writeComment("Compute Expression in ForStatement")
            visit(expressions.first())
            writeLn()

            writeComment("Check termination condition")
            writeLn("cmp eax, 0")
            writeLn("je $endForLabel")
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
        writeLn("$endForLabel:")
        writeLn()

        writeComment("Pop for loop variables from stack")

        check(localVariableDeclarationEnvironment.size - numVariables >= 0) { "It's not possible to remove variables from the stack inside ForStatement" }

        writeLn("add esp, ${(localVariableDeclarationEnvironment.size - numVariables) * 4}")
    }

    override fun visitForStatementNoShortIf(node: CSTNode) {
        val beginForLabel = genLabel("beginFor")
        val endForLabel = genLabel("endFor")

        val numVariables = localVariableDeclarationEnvironment.size

        writeComment("For loop initializer")
        val forInitOpt = node.getChild("ForInitOpt")
        visit(forInitOpt)
        writeLn()

        writeLn("$beginForLabel:")

        val expressions = node.getChild("ExpressionOpt").getDescendants("Expression")
        if (expressions.isNotEmpty()) {
            writeComment("Compute Expression in ForStatementNoShortIf")
            visit(expressions.first())
            writeLn()

            writeComment("Check termination condition")
            writeLn("cmp eax, 0")
            writeLn("je $endForLabel")
        }

        writeComment("Visit Statement inside for loop")
        val statement = node.getChild("StatementNoShortIf")
        visit(statement)
        writeLn()

        writeComment("Update for loop variables")
        val forUpdateOpt = node.getChild("ForUpdateOpt")
        visit(forUpdateOpt)
        writeLn()

        writeLn("jmp $beginForLabel")
        writeLn("$endForLabel:")
        writeLn()

        writeComment("Pop for loop variables from stack")

        check(localVariableDeclarationEnvironment.size - numVariables >= 0) { "It's not possible to remove variables from the stack inside ForStatementNoShortIf" }

        writeLn("add esp, ${(localVariableDeclarationEnvironment.size - numVariables) * 4}")
    }

    override fun visitMethodDeclaration(node: CSTNode) {
        formalParameterEnvironment.withNewScope({
            withAssemblyMethodDeclaration(node, {
                super.visitMethodDeclaration(node)
            })
        })
    }

    override fun visitBlock(node: CSTNode) {
        localVariableDeclarationEnvironment.withNewScope({
            val originalNumVariables = localVariableDeclarationEnvironment.size
            super.visitBlock(node)
            val currentNumVariables = localVariableDeclarationEnvironment.size

            check(currentNumVariables >= originalNumVariables) {
                "Number of variables in scope at end of block is less than number of variables in scope at its beginning"
            }

            writeLn("add esp, ${(currentNumVariables - originalNumVariables) * 4}")
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
        currentLeftHandSide = node
        super.visitLeftHandSide(node)
        currentLeftHandSide = null
    }

    fun isLValue(node: CSTNode): Boolean {
        val leftHandSide = getCurrentLeftHandSide()
        if (leftHandSide == null) {
            return false
        }

        return leftHandSide.children[0] == node
    }

    fun getCurrentLeftHandSide(): CSTNode? {
        return currentLeftHandSide
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
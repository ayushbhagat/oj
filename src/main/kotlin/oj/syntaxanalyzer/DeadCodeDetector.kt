package oj.syntaxanalyzer

import oj.models.CSTNode
import oj.models.CSTNodeVisitor
import oj.models.PackageManager
import oj.models.getDeclarationName

class DeadCodeDetectorException(reason: String): Exception(reason)

open class NotConstantExpression(): Exception()
class NotConstantBooleanExpression(): NotConstantExpression()
class NotConstantIntegerExpression(): NotConstantExpression()
class NotConstantStringExpression(): NotConstantExpression()

fun evalInt(node: CSTNode): Int {
    when (node.name) {
        "Expression" -> {
            return evalInt(node.getChild("AssignmentExpression"))
        }

        "AssignmentExpression" -> {
            if (node.children[0].name == "ConditionalOrExpression") {
                return evalInt(node.getChild("ConditionalOrExpression"))
            }

            throw NotConstantIntegerExpression()
        }

        "ConditionalOrExpression" -> {
            if (node.children[0].name == "ConditionalAndExpression") {
                return evalInt(node.getChild("ConditionalAndExpression"))
            }

            throw NotConstantIntegerExpression()
        }

        "ConditionalAndExpression" -> {
            if (node.children[0].name == "OrExpression") {
                return evalInt(node.getChild("OrExpression"))
            }

            throw NotConstantIntegerExpression()
        }

        "OrExpression" -> {
            if (node.children[0].name == "AndExpression") {
                return evalInt(node.getChild("AndExpression"))
            }

            throw NotConstantIntegerExpression()
        }

        "AndExpression" -> {
            if (node.children[0].name == "EqualityExpression") {
                return evalInt(node.getChild("EqualityExpression"))
            }

            throw NotConstantIntegerExpression()
        }

        "EqualityExpression" -> {
            if (node.children[0].name == "RelationalExpression") {
                return evalInt(node.getChild("RelationalExpression"))
            }

            throw NotConstantIntegerExpression()
        }

        "RelationalExpression" -> {
            if (node.children[0].name == "AdditiveExpression") {
                return evalInt(node.getChild("AdditiveExpression"))
            }

            throw NotConstantIntegerExpression()
        }

        "AdditiveExpression" -> {
            if (node.children[0].name == "MultiplicativeExpression") {
                return evalInt(node.getChild("MultiplicativeExpression"))
            }

            if (node.children[1].name == "+") {
                return evalInt(node.getChild("AdditiveExpression")) + evalInt(node.getChild("MultiplicativeExpression"))
            }

            return evalInt(node.getChild("AdditiveExpression")) - evalInt(node.getChild("MultiplicativeExpression"))
        }

        "MultiplicativeExpression" -> {
            if (node.children[0].name == "UnaryExpression") {
                return evalInt(node.getChild("UnaryExpression"))
            }

            when (node.children[1].name) {
                "*" -> return evalInt(node.getChild("MultiplicativeExpression")) * evalInt(node.getChild("UnaryExpression"))
                "/" -> return evalInt(node.getChild("MultiplicativeExpression")) / evalInt(node.getChild("UnaryExpression"))
            }

            return evalInt(node.getChild("MultiplicativeExpression")) % evalInt(node.getChild("UnaryExpression"))
        }

        "UnaryExpression" -> {
            if (node.children[0].name == "UnaryExpressionNotPlusMinus") {
                return evalInt(node.getChild("UnaryExpressionNotPlusMinus"))
            }

            return - evalInt(node.getChild("UnaryExpression"))
        }

        "UnaryExpressionNotPlusMinus" -> {
            when (node.children[0].name) {
                "Primary" -> return evalInt(node.getChild("Primary"))
                "CastExpression" -> return evalInt(node.getChild("CastExpression"))
            }

            throw NotConstantIntegerExpression()
        }

        "Primary" -> {
            if (node.children[0].name == "PrimaryNoNewArray") {
                return evalInt(node.getChild("PrimaryNoNewArray"))
            }

            throw NotConstantIntegerExpression()
        }

        "PrimaryNoNewArray" -> {
            when (node.children[0].name) {
                "Literal" -> return evalInt(node.getChild("Literal"))
                "(" -> return evalInt(node.getChild("Expression"))
            }

            throw NotConstantIntegerExpression()
        }

        "Literal" -> {
            if (node.children[0].name == "INTEGER") {
                return node.children[0].lexeme.toInt()
            }

            throw NotConstantIntegerExpression()
        }

        "CastExpression" -> {
            if (node.getType().isNumeric()) {
                val unaryExpression = node.getChild("UnaryExpression")

                if (unaryExpression.getType().isNumeric()) {
                    return evalInt(unaryExpression)
                }
            }

            throw NotConstantIntegerExpression()
        }

        else -> throw NotConstantIntegerExpression()
    }
}

class DeadCodeDetector(val packageManager: PackageManager) : CSTNodeVisitor() {
    enum class Answer {
        No, Maybe
    }

    fun evalString(node: CSTNode): String {
        when (node.name) {
            "Expression" -> return evalString(node.getChild("AssignmentExpression"))

            "AssignmentExpression" -> {
                if (node.children[0].name == "ConditionalOrExpression") {
                    return evalString(node.getChild("ConditionalOrExpression"))
                }

                throw NotConstantStringExpression()
            }

            "ConditionalOrExpression" -> {
                if (node.children[0].name == "ConditionalAndExpression") {
                    return evalString(node.getChild("ConditionalAndExpression"))
                }

                throw NotConstantStringExpression()
            }

            "ConditionalAndExpression" -> {
                if (node.children[0].name == "OrExpression") {
                    return evalString(node.getChild("OrExpression"))
                }

                throw NotConstantStringExpression()
            }

            "OrExpression" -> {
                if (node.children[0].name == "AndExpression") {
                    return evalString(node.getChild("AndExpression"))
                }

                throw NotConstantStringExpression()
            }

            "AndExpression" -> {
                if (node.children[0].name == "EqualityExpression") {
                    return evalString(node.getChild("EqualityExpression"))
                }

                throw NotConstantStringExpression()
            }

            "EqualityExpression" -> {
                if (node.children[0].name == "RelationalExpression") {
                    return evalString(node.getChild("RelationalExpression"))
                }

                throw NotConstantStringExpression()
            }

            "RelationalExpression" -> {
                if (node.children[0].name == "AdditiveExpression") {
                    return evalString(node.getChild("AdditiveExpression"))
                }

                throw NotConstantIntegerExpression()
            }

            "AdditiveExpression" -> {
                if (node.children[0].name == "MultiplicativeExpression") {
                    return evalString(node.getChild("MultiplicativeExpression"))
                }

                if (node.children[1].name == "+") {
                    val additiveExpression = node.getChild("AdditiveExpression")
                    val multiplicativeExpression = node.getChild("MultiplicativeExpression")

                    val typeOfAdditiveExpression = additiveExpression.getType()
                    val typeOfMultiplicativeExpression = multiplicativeExpression.getType()

                    if (typeOfAdditiveExpression.isNumeric() && packageManager.isString(typeOfMultiplicativeExpression)) {
                        return evalInt(additiveExpression).toString() + evalString(multiplicativeExpression)
                    }

                    if (packageManager.isString(typeOfAdditiveExpression) && typeOfMultiplicativeExpression.isNumeric() ) {
                        return evalString(additiveExpression) + evalInt(multiplicativeExpression)
                    }

                    if (packageManager.isString(typeOfAdditiveExpression) && packageManager.isString(typeOfMultiplicativeExpression)) {
                        return evalString(additiveExpression) + evalString(multiplicativeExpression)
                    }
                }

                throw NotConstantIntegerExpression()
            }

            "MultiplicativeExpression" -> {
                if (node.children[0].name == "UnaryExpression") {
                    return evalString(node.getChild("UnaryExpression"))
                }

                throw NotConstantIntegerExpression()
            }

            "UnaryExpression" -> {
                if (node.children[0].name == "UnaryExpressionNotPlusMinus") {
                    return evalString(node.getChild("UnaryExpressionNotPlusMinus"))
                }

                throw NotConstantIntegerExpression()
            }

            "UnaryExpressionNotPlusMinus" -> {
                when (node.children[0].name) {
                    "Primary" -> return evalString(node.getChild("Primary"))
                    "CastExpression" -> return evalString(node.getChild("CastExpression"))
                }

                throw NotConstantIntegerExpression()
            }

            "Primary" -> {
                if (node.children[0].name == "PrimaryNoNewArray") {
                    return evalString(node.getChild("PrimaryNoNewArray"))
                }

                throw NotConstantIntegerExpression()
            }

            "PrimaryNoNewArray" -> {
                if (node.children[0].name == "Literal") {
                    return evalString(node.getChild("Literal"))
                }

                throw NotConstantIntegerExpression()
            }

            "Literal" -> {
                if (node.children[0].name == "STRING") {
                    return node.children[0].lexeme
                }

                throw NotConstantIntegerExpression()
            }

            "CastExpression" -> {
                if (packageManager.isString(node.getType())) {
                    val unaryExpressionNotPlusMinus = node.getChild("UnaryExpressionNotPlusMinus")
                    if (packageManager.isString(unaryExpressionNotPlusMinus.getType())) {
                        return evalString(unaryExpressionNotPlusMinus)
                    }
                }

                throw NotConstantIntegerExpression()
            }
            else -> throw NotConstantIntegerExpression()
        }
    }

    fun evalBool(node: CSTNode): Boolean {
        when (node.name) {
            "Expression" -> {
                return evalBool(node.getChild("AssignmentExpression"))
            }

            "AssignmentExpression" -> {
                if (node.children[0].name == "Assignment") {
                    throw NotConstantBooleanExpression()
                }

                return evalBool(node.getChild("ConditionalOrExpression"))
            }

            "ConditionalOrExpression" -> {
                if (node.children[0].name == "ConditionalAndExpression") {
                    return evalBool(node.getChild("ConditionalAndExpression"))
                }

                return evalBool(node.getChild("ConditionalOrExpression")) || evalBool(node.getChild("ConditionalAndExpression"))
            }

            "ConditionalAndExpression" -> {
                if (node.children[0].name == "OrExpression") {
                    return evalBool(node.getChild("OrExpression"))
                }

                return evalBool(node.getChild("ConditionalAndExpression")) && evalBool(node.getChild("OrExpression"))
            }

            "OrExpression" -> {
                if (node.children[0].name == "AndExpression") {
                    return evalBool(node.getChild("AndExpression"))
                }

                return evalBool(node.getChild("OrExpression")) || evalBool(node.getChild("AndExpression"))
            }

            "AndExpression" -> {
                if (node.children[0].name == "EqualityExpression") {
                    return evalBool(node.getChild("EqualityExpression"))
                }

                return evalBool(node.getChild("AndExpression")) && evalBool(node.getChild("EqualityExpression"))
            }

            "EqualityExpression" -> {
                if (node.children[0].name == "RelationalExpression") {
                    return evalBool(node.getChild("RelationalExpression"))
                }

                val equalityExpression = node.getChild("EqualityExpression")
                val relationalExpression = node.getChild("RelationalExpression")

                val typeOfEqualityExpression = equalityExpression.getType()
                val typeOfRelationalExpression = relationalExpression.getType()

                if (typeOfEqualityExpression.isNumeric() && typeOfRelationalExpression.isNumeric()) {
                    if (node.children[1].name == "==") {
                        return evalInt(node.getChild("EqualityExpression")) == evalInt(node.getChild("RelationalExpression"))
                    }

                    return evalInt(node.getChild("EqualityExpression")) != evalInt(node.getChild("RelationalExpression"))
                }

                if (typeOfEqualityExpression.isBoolean() && typeOfRelationalExpression.isBoolean()) {
                    if (node.children[1].name == "==") {
                        return evalBool(node.getChild("EqualityExpression")) == evalBool(node.getChild("RelationalExpression"))
                    }

                    return evalBool(node.getChild("EqualityExpression")) != evalBool(node.getChild("RelationalExpression"))
                }

                if (packageManager.isString(typeOfEqualityExpression) && packageManager.isString(typeOfRelationalExpression)) {
                    if (node.children[1].name == "==") {
                        return evalString(node.getChild("EqualityExpression")) == evalString(node.getChild("RelationalExpression"))
                    }

                    return evalString(node.getChild("EqualityExpression")) != evalString(node.getChild("RelationalExpression"))
                }

                throw NotConstantBooleanExpression()
            }

            "RelationalExpression" -> {
                if (node.children[0].name == "AdditiveExpression") {
                    return evalBool(node.getChild("AdditiveExpression"))
                }

                when (node.children[1].name) {
                    "<" -> return evalInt(node.getChild("RelationalExpression")) < evalInt(node.getChild("AdditiveExpression"))
                    ">" -> return evalInt(node.getChild("RelationalExpression")) > evalInt(node.getChild("AdditiveExpression"))
                    "<=" -> return evalInt(node.getChild("RelationalExpression")) <= evalInt(node.getChild("AdditiveExpression"))
                    ">=" -> return evalInt(node.getChild("RelationalExpression")) >= evalInt(node.getChild("AdditiveExpression"))
                }

                throw NotConstantBooleanExpression()
            }

            "AdditiveExpression" -> {
                if (node.children[0].name == "MultiplicativeExpression") {
                    return evalBool(node.getChild("MultiplicativeExpression"))
                }

                throw NotConstantBooleanExpression()
            }

            "MultiplicativeExpression" -> {
                if (node.children[0].name == "UnaryExpression") {
                    return evalBool(node.getChild("UnaryExpression"))
                }

                throw NotConstantBooleanExpression()
            }

            "UnaryExpression" -> {
                if (node.children[0].name == "UnaryExpressionNotPlusMinus") {
                    return evalBool(node.getChild("UnaryExpressionNotPlusMinus"))
                }

                throw NotConstantBooleanExpression()
            }

            "UnaryExpressionNotPlusMinus" -> {
                when (node.children[0].name) {
                    "!" -> return evalBool(node.getChild("UnaryExpression"))
                    "Primary" -> return evalBool(node.getChild("Primary"))
                    "CastExpression" -> return evalBool(node.getChild("CastExpression"))
                }

                throw NotConstantBooleanExpression()
            }

            "Primary" -> {
                if (node.children[0].name == "PrimaryNoNewArray") {
                    return evalBool(node.getChild("PrimaryNoNewArray"))
                }

                throw NotConstantBooleanExpression()
            }

            "PrimaryNoNewArray" -> {
                when (node.children[0].name) {
                    "Literal" -> return evalBool(node.getChild("Literal"))
                    "(" -> return evalBool(node.getChild("Expression"))
                }

                throw NotConstantBooleanExpression()
            }

            "Literal" -> {
                if (node.children[0].name == "BOOLEAN") {
                    when (node.children[0].lexeme) {
                        "true" -> return true
                        "false" -> return false
                    }
                }

                throw NotConstantBooleanExpression()
            }

            "CastExpression" -> {
                if (node.getType().isBoolean()) {
                    val unaryExpression = node.getChild("UnaryExpression")
                    if (unaryExpression.getType().isBoolean()) {
                        return evalBool(unaryExpression)
                    }
                }

                throw NotConstantBooleanExpression()
            }

            else -> throw NotConstantBooleanExpression()
        }
    }


    private val In = mutableMapOf<CSTNode, Answer>()
    private val Out = mutableMapOf<CSTNode, Answer>()

    override fun visitIfThenStatement(node: CSTNode) {
        val statement = node.getChild("Statement")

        In[statement] = In[node]!!

        super.visitIfThenStatement(node)

        Out[node] = In[node]!!
    }

    override fun visitIfThenElseStatement(node: CSTNode) {
        val thenStatement = node.getChild("StatementNoShortIf")
        val elseStatement = node.getChild("Statement")

        In[thenStatement] = In[node]!!
        In[elseStatement] = In[node]!!

        super.visitIfThenElseStatement(node)

        Out[node] = join(Out[thenStatement]!!, Out[elseStatement]!!)
    }

    override fun visitIfThenElseStatementNoShortIf(node: CSTNode) {
        val (thenStatement, elseStatement) = node.getChildren("StatementNoShortIf")

        In[thenStatement] = In[node]!!
        In[elseStatement] = In[node]!!

        super.visitIfThenElseStatement(node)

        Out[node] = join(Out[thenStatement]!!, Out[elseStatement]!!)
    }

    override fun visitStatementNoShortIf(node: CSTNode) {
        calculateChildStatementReachability(node)
    }


    override fun visitWhileStatement(node: CSTNode) {
        val booleanLexeme = getBooleanConstantExpressionLexeme(node.getChild("Expression"))
        val statement = node.getChild("Statement")

        when (booleanLexeme) {
            "true" -> { In[statement] = In[node]!! }
            "false" -> { In[statement] = Answer.No }
            else -> { In[statement] = In[node]!! }
        }

        super.visitWhileStatement(node)

        when (booleanLexeme) {
            "true" -> { Out[node] = Answer.No }
            "false" -> { Out[node] = In[node]!! }
            else -> { Out[node] = In[node]!! }
        }
    }

    override fun visitWhileStatementNoShortIf(node: CSTNode) {
        val booleanLexeme = getBooleanConstantExpressionLexeme(node.getChild("Expression"))
        val statement = node.getChild("StatementNoShortIf")

        when (booleanLexeme) {
            "true" -> { In[statement] = In[node]!! }
            "false" -> { In[statement] = Answer.No }
            else -> { In[statement] = In[node]!! }
        }

        super.visitWhileStatementNoShortIf(node)

        when (booleanLexeme) {
            "true" -> { Out[node] = Answer.No }
            "false" -> { Out[node] = In[node]!! }
            else -> { Out[node] = In[node]!! }
        }
    }

    override fun visitReturnStatement(node: CSTNode) {
        Out[node] = Answer.No
    }

    override fun visitForStatement(node: CSTNode) {
        val booleanLexeme = getBooleanConstantExpressionLexeme(node.getChild("ExpressionOpt"))
        val statement = node.getChild("Statement")

        when (booleanLexeme) {
            "true" -> { In[statement] = In[node]!! }
            "false" -> { In[statement] = Answer.No }
            else -> { In[statement] = In[node]!! }
        }

        super.visitForStatement(node)

        when (booleanLexeme) {
            "true" -> { Out[node] = Answer.No }
            "false" -> { Out[node] = In[node]!! }
            else -> { Out[node] = In[node]!! }
        }
    }

    override fun visitForStatementNoShortIf(node: CSTNode) {
        val booleanLexeme = getBooleanConstantExpressionLexeme(node.getChild("ExpressionOpt"))
        val statement = node.getChild("StatementNoShortIf")

        when (booleanLexeme) {
            "true" -> { In[statement] = In[node]!! }
            "false" -> { In[statement] = Answer.No }
            else -> { In[statement] = In[node]!! }
        }

        super.visitForStatementNoShortIf(node)

        when (booleanLexeme) {
            "true" -> { Out[node] = Answer.No }
            "false" -> { Out[node] = In[node]!! }
            else -> { Out[node] = In[node]!! }
        }
    }

    /**
     * Start from Constructor Body and Method Body and work your way down through all statements, to ensure each
     * statement is reachable.
     */

    override fun visitConstructorBody(node: CSTNode) {
        val blockStatementsOpt = node.getChild("BlockStatementsOpt")
        In[blockStatementsOpt] = Answer.Maybe

        super.visitConstructorBody(node)

        Out[node] = Out[blockStatementsOpt]!!
    }

    override fun visitMethodBody(node: CSTNode) {
        if (node.children[0].name == "Block") {
            val block = node.getChild("Block")
            In[block] = Answer.Maybe

            super.visitMethodBody(node)

            Out[node] = Out[block]!!
        } else {
            super.visitMethodBody(node)
        }
    }

    override fun visitBlock(node: CSTNode) {
        val blockStatementsOpt = node.getChild("BlockStatementsOpt")
        In[blockStatementsOpt] = In[node]!!

        super.visitConstructorBody(node)

        Out[node] = Out[blockStatementsOpt]!!
    }

    override fun visitBlockStatementsOpt(node: CSTNode) {
        if (node.children.size == 0) {
            Out[node] = In[node]!!
            return
        }

        val blockStatements = node.getChild("BlockStatements")
        In[blockStatements] = In[node]!!

        super.visitBlockStatementsOpt(node)

        Out[node] = Out[blockStatements]!!
    }

    override fun visitBlockStatements(node: CSTNode) {
        when (node.children.size) {
            1 -> {
                val blockStatement = node.getChild("BlockStatement")
                In[blockStatement] = In[node]!!

                super.visitBlockStatements(node)

                Out[node] = Out[blockStatement]!!
            }

            2 -> {
                val blockStatements = node.getChild("BlockStatements")
                In[blockStatements] = In[node]!!
                this.visit(blockStatements)

                val blockStatement = node.getChild("BlockStatement")
                In[blockStatement] = Out[blockStatements]!!
                this.visit(blockStatement)

                Out[node] = Out[blockStatement]!!
            }
        }
    }

    fun calculateChildStatementReachability(node: CSTNode) {
        if (node.children.size != 1) {
            throw Exception("Expected node to have only one child, found ${node.children.size}")
        }

        val child = node.children[0]

        In[child] = In[node]!!
        this.visit(child)
        Out[node] = Out[child]!!
    }

    override fun visitBlockStatement(node: CSTNode) {
        calculateChildStatementReachability(node)
    }

    override fun visitLocalVariableDeclarationStatement(node: CSTNode) {
        Out[node] = In[node]!!
    }

    override fun visitStatement(node: CSTNode) {
        calculateChildStatementReachability(node)
    }

    override fun visitStatementWithoutTrailingSubstatement(node: CSTNode) {
        calculateChildStatementReachability(node)
    }

    override fun visitEmptyStatement(node: CSTNode) {
        Out[node] = In[node]!!
    }

    override fun visitExpressionStatement(node: CSTNode) {
        Out[node] = In[node]!!
    }

    /**
     * Ensure that for for no S, In[s] = Answer.No
     * Ensure that
     */
    override fun visitMethodDeclaration(node: CSTNode) {
        In.clear()
        Out.clear()

        val methodHeader = node.getChild("MethodHeader")
        val isVoid = methodHeader.children[1].name == "void"
        val methodName = getDeclarationName(node)

        super.visitMethodDeclaration(node)

        if (!isVoid) {
            val methodBody = node.getChild("MethodBody")
            if (Out[methodBody] == Answer.Maybe) {
                throw DeadCodeDetectorException("Method \"$methodName\" is non-void, but may not return an expression.")
            }
        }

        if (In.any({(_, answer) -> answer == Answer.No})) {
            throw DeadCodeDetectorException("Detected dead code in method \"$methodName\".")
        }
    }

    override fun visitConstructorDeclaration(node: CSTNode) {
        In.clear()
        Out.clear()

        super.visitConstructorDeclaration(node)

        if (In.any({(_, answer) -> answer == Answer.No})) {
            throw DeadCodeDetectorException("Detected dead code inside constructor.")
        }
    }

    fun getBooleanConstantExpressionLexeme(node: CSTNode): String {
        val expression =
            if (node.name == "Expression") {
                node
            } else if (node.name == "ExpressionOpt" && node.children.size == 1) {
                node.getChild("Expression")
            } else {
                return ""
            }

        return try {
            evalBool(expression).toString()
        } catch (ex: NotConstantExpression) {
            ""
        }
    }

    companion object {
        fun join(a:Answer, b:Answer):Answer {
            if (a ==Answer.Maybe || b ==Answer.Maybe) {
                return Answer.Maybe
            }
            return Answer.No
        }
    }
}
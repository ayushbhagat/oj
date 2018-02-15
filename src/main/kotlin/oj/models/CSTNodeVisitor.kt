package oj.models

class CSTNodeVisitorException(reason: String) : Exception(reason)

open class CSTNodeVisitor {
    fun visit(node: CSTNode) {
        when (node.name) {
            "S" -> visitS(node)
            "Literal" -> visitLiteral(node)
            "Type" -> visitType(node)
            "PrimitiveType" -> visitPrimitiveType(node)
            "ReferenceType" -> visitReferenceType(node)
            "ClassOrInterfaceType" -> visitClassOrInterfaceType(node)
            "ClassType" -> visitClassType(node)
            "InterfaceType" -> visitInterfaceType(node)
            "ArrayType" -> visitArrayType(node)
            "Name" -> visitName(node)
            "SimpleName" -> visitSimpleName(node)
            "QualifiedName" -> visitQualifiedName(node)
            "CompilationUnit" -> visitCompilationUnit(node)
            "PackageDeclarationOpt" -> visitPackageDeclarationOpt(node)
            "ImportDeclarationsOpt" -> visitImportDeclarationsOpt(node)
            "TypeDeclarationsOpt" -> visitTypeDeclarationsOpt(node)
            "ImportDeclarations" -> visitImportDeclarations(node)
            "TypeDeclarations" -> visitTypeDeclarations(node)
            "PackageDeclaration" -> visitPackageDeclaration(node)
            "ImportDeclaration" -> visitImportDeclaration(node)
            "SingleTypeImportDeclaration" -> visitSingleTypeImportDeclaration(node)
            "TypeImportOnDemandDeclaration" -> visitTypeImportOnDemandDeclaration(node)
            "TypeDeclaration" -> visitTypeDeclaration(node)
            "Modifiers" -> visitModifiers(node)
            "Modifier" -> visitModifier(node)
            "ClassDeclaration" -> visitClassDeclaration(node)
            "SuperOpt" -> visitSuperOpt(node)
            "InterfacesOpt" -> visitInterfacesOpt(node)
            "Super" -> visitSuper(node)
            "Interfaces" -> visitInterfaces(node)
            "InterfaceTypeList" -> visitInterfaceTypeList(node)
            "ClassBody" -> visitClassBody(node)
            "ClassBodyDeclarationsOpt" -> visitClassBodyDeclarationsOpt(node)
            "ClassBodyDeclarations" -> visitClassBodyDeclarations(node)
            "ClassBodyDeclaration" -> visitClassBodyDeclaration(node)
            "ClassMemberDeclaration" -> visitClassMemberDeclaration(node)
            "FieldDeclaration" -> visitFieldDeclaration(node)
            "VariableDeclarator" -> visitVariableDeclarator(node)
            "VariableDeclaratorId" -> visitVariableDeclaratorId(node)
            "VariableInitializer" -> visitVariableInitializer(node)
            "MethodDeclaration" -> visitMethodDeclaration(node)
            "MethodHeader" -> visitMethodHeader(node)
            "MethodDeclarator" -> visitMethodDeclarator(node)
            "FormalParameterListOpt" -> visitFormalParameterListOpt(node)
            "FormalParameterList" -> visitFormalParameterList(node)
            "FormalParameter" -> visitFormalParameter(node)
            "ClassTypeList" -> visitClassTypeList(node)
            "MethodBody" -> visitMethodBody(node)
            "ConstructorDeclaration" -> visitConstructorDeclaration(node)
            "ConstructorDeclarator" -> visitConstructorDeclarator(node)
            "ConstructorBody" -> visitConstructorBody(node)
            "BlockStatementsOpt" -> visitBlockStatementsOpt(node)
            "InterfaceDeclaration" -> visitInterfaceDeclaration(node)
            "ExtendsInterfacesOpt" -> visitExtendsInterfacesOpt(node)
            "ExtendsInterfaces" -> visitExtendsInterfaces(node)
            "InterfaceBody" -> visitInterfaceBody(node)
            "InterfaceMemberDeclarationsOpt" -> visitInterfaceMemberDeclarationsOpt(node)
            "InterfaceMemberDeclarations" -> visitInterfaceMemberDeclarations(node)
            "InterfaceMemberDeclaration" -> visitInterfaceMemberDeclaration(node)
            "ConstantDeclaration" -> visitConstantDeclaration(node)
            "AbstractMethodDeclaration" -> visitAbstractMethodDeclaration(node)
            "Block" -> visitBlock(node)
            "BlockStatements" -> visitBlockStatements(node)
            "BlockStatement" -> visitBlockStatement(node)
            "LocalVariableDeclarationStatement" -> visitLocalVariableDeclarationStatement(node)
            "LocalVariableDeclaration" -> visitLocalVariableDeclaration(node)
            "Statement" -> visitStatement(node)
            "StatementNoShortIf" -> visitStatementNoShortIf(node)
            "StatementWithoutTrailingSubstatement" -> visitStatementWithoutTrailingSubstatement(node)
            "EmptyStatement" -> visitEmptyStatement(node)
            "ExpressionStatement" -> visitExpressionStatement(node)
            "StatementExpression" -> visitStatementExpression(node)
            "IfThenStatement" -> visitIfThenStatement(node)
            "IfThenElseStatement" -> visitIfThenElseStatement(node)
            "IfThenElseStatementNoShortIf" -> visitIfThenElseStatementNoShortIf(node)
            "WhileStatement" -> visitWhileStatement(node)
            "WhileStatementNoShortIf" -> visitWhileStatementNoShortIf(node)
            "ForStatement" -> visitForStatement(node)
            "ForInitOpt" -> visitForInitOpt(node)
            "ExpressionOpt" -> visitExpressionOpt(node)
            "ForUpdateOpt" -> visitForUpdateOpt(node)
            "ForStatementNoShortIf" -> visitForStatementNoShortIf(node)
            "ForInit" -> visitForInit(node)
            "ForUpdate" -> visitForUpdate(node)
            "ReturnStatement" -> visitReturnStatement(node)
            "Primary" -> visitPrimary(node)
            "PrimaryNoNewArray" -> visitPrimaryNoNewArray(node)
            "ClassInstanceCreationExpression" -> visitClassInstanceCreationExpression(node)
            "ArgumentListOpt" -> visitArgumentListOpt(node)
            "ArgumentList" -> visitArgumentList(node)
            "ArrayCreationExpression" -> visitArrayCreationExpression(node)
            "DimExpr" -> visitDimExpr(node)
            "FieldAccess" -> visitFieldAccess(node)
            "MethodInvocation" -> visitMethodInvocation(node)
            "ArrayAccess" -> visitArrayAccess(node)
            "UnaryExpression" -> visitUnaryExpression(node)
            "UnaryExpressionNotPlusMinus" -> visitUnaryExpressionNotPlusMinus(node)
            "CastExpression" -> visitCastExpression(node)
            "DimOpt" -> visitDimOpt(node)
            "Dim" -> visitDim(node)
            "MultiplicativeExpression" -> visitMultiplicativeExpression(node)
            "AdditiveExpression" -> visitAdditiveExpression(node)
            "RelationalExpression" -> visitRelationalExpression(node)
            "EqualityExpression" -> visitEqualityExpression(node)
            "ConditionalAndExpression" -> visitConditionalAndExpression(node)
            "ConditionalOrExpression" -> visitConditionalOrExpression(node)
            "AssignmentExpression" -> visitAssignmentExpression(node)
            "Assignment" -> visitAssignment(node)
            "LeftHandSide" -> visitLeftHandSide(node)
            "AssignmentOperator" -> visitAssignmentOperator(node)
            "Expression" -> visitExpression(node)
            else -> {
                node.children.forEach({ child -> visit(child) })
            }
        }
    }

    open fun visitS(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitLiteral(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitType(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitPrimitiveType(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitReferenceType(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassOrInterfaceType(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassType(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfaceType(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitArrayType(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitName(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitSimpleName(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitQualifiedName(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitCompilationUnit(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitPackageDeclarationOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitImportDeclarationsOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitTypeDeclarationsOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitImportDeclarations(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitTypeDeclarations(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitPackageDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitImportDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitSingleTypeImportDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitTypeImportOnDemandDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitTypeDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitModifiers(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitModifier(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitSuperOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfacesOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitSuper(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfaces(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfaceTypeList(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassBody(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassBodyDeclarationsOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassBodyDeclarations(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassBodyDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassMemberDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitFieldDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitVariableDeclarator(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitVariableDeclaratorId(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitVariableInitializer(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitMethodDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitMethodHeader(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitMethodDeclarator(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitFormalParameterListOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitFormalParameterList(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitFormalParameter(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassTypeList(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitMethodBody(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitConstructorDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitConstructorDeclarator(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitConstructorBody(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitBlockStatementsOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfaceDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitExtendsInterfacesOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitExtendsInterfaces(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfaceBody(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfaceMemberDeclarationsOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfaceMemberDeclarations(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitInterfaceMemberDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitConstantDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitAbstractMethodDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitBlock(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitBlockStatements(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitBlockStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitLocalVariableDeclarationStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitLocalVariableDeclaration(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitStatementNoShortIf(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitStatementWithoutTrailingSubstatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitEmptyStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitExpressionStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitStatementExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitIfThenStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitIfThenElseStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitIfThenElseStatementNoShortIf(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitWhileStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitWhileStatementNoShortIf(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitForStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitForInitOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitExpressionOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitForUpdateOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitForStatementNoShortIf(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitForInit(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitForUpdate(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitReturnStatement(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitPrimary(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitPrimaryNoNewArray(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitClassInstanceCreationExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitArgumentListOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitArgumentList(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitArrayCreationExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitDimExpr(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitFieldAccess(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitMethodInvocation(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitArrayAccess(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitUnaryExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitUnaryExpressionNotPlusMinus(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitCastExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitDimOpt(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitDim(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitMultiplicativeExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitAdditiveExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitRelationalExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitEqualityExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitConditionalAndExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitConditionalOrExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitAssignmentExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitAssignment(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitLeftHandSide(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitAssignmentOperator(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }

    open fun visitExpression(node: CSTNode) {
        node.children.forEach({ child -> visit(child) })
    }
}
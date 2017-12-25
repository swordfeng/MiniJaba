package moe.taiho.minijaba

import moe.taiho.minijaba.ast.*

object Template {
    fun print(n: BaseDecl) {
        when (n) {
        /* declarations */
            is Goal -> {}
            is MainClassDecl -> {}
            is ClassDecl -> {}
            is MethodDecl -> {}
            is VarDecl -> {}

        /* statements */
            is BlockStmt -> {}
            is IfStmt -> {}
            is WhileStmt -> {}
            is AssignStmt -> {}
            is ArrayAssignStmt -> {}
            is PrintlnStmt -> {}

        /* types */
            is IntArrayType -> {}
            is BoolType -> {}
            is IntType -> {}
            is ClassType -> {}

        /* expressions */
            is BracketExp -> {}
            is AddExp -> {}
            is SubExp -> {}
            is MulExp -> {}
            is AndExp -> {}
            is LessThanExp -> {}
            is NotExp -> {}

            is ArrayAccessExp -> {}
            is ArrayLengthExp -> {}
            is MethodCallExp -> {}
            is ArrayAllocExp -> {}
            is ObjectAllocExp -> {}

            is ThisExp -> {}
            is TrueExp -> {}
            is FalseExp -> {}
            is IntLiteralExp -> {}
            is IdentExp -> {}

            else -> {}
        }
    }
}
package moe.taiho.minijaba.ast


class MethodDecl(
        val ident: String,
        val returnType: Type,
        val paramList: List<VarDecl>,
        val varList: List<VarDecl>,
        val stmtList: List<Stmt>,
        val returnExp: Exp
) : BaseDecl()
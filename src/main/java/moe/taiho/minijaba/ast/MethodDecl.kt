package moe.taiho.minijaba.ast


class MethodDecl(
        var ident: String,
        var returnType: Type,
        var paramList: List<VarDecl>,
        var varList: List<VarDecl>,
        var stmtList: List<Stmt>,
        var returnExp: Exp
) : BaseDecl()
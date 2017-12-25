package moe.taiho.minijaba.ast

class ArrayAssignStmt(var ident: String, var index: Exp, var value: Exp) : Stmt()

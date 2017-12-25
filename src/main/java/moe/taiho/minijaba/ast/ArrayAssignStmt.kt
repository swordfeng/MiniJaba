package moe.taiho.minijaba.ast

class ArrayAssignStmt(val ident: String, val index: Exp, val value: Exp) : Stmt()

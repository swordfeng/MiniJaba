package moe.taiho.minijaba.ast

class IfStmt(val cond: Exp, val onTrue: Stmt, val onFalse: Stmt) : Stmt()

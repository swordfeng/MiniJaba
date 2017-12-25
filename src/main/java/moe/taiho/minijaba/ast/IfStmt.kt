package moe.taiho.minijaba.ast

class IfStmt(var cond: Exp, var trueStmt: Stmt, var falseStmt: Stmt) : Stmt()

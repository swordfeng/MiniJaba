package moe.taiho.minijaba.ast

class MethodCallExp(var obj: Exp, var methodName: String, var args: List<Exp>) : Exp()

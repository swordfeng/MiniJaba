package moe.taiho.minijaba.ast

class MethodCallExp(val obj: Exp, val methodName: String, val args: List<Exp>) : Exp()

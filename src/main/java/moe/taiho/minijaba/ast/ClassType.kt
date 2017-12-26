package moe.taiho.minijaba.ast

class ClassType(var ident: String) : Type() {
    override fun typeName(): String {
        return "ident";
    }
}

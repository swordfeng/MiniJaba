package moe.taiho.minijaba.ast

abstract class Type : BaseDecl() {
    abstract fun typeName(): String
}

package moe.taiho.minijaba.ast

abstract class BaseDecl {
    val nodeType: String
        get() {
            return this.javaClass.simpleName
        }
}
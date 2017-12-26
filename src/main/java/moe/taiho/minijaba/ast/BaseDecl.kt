package moe.taiho.minijaba.ast

import moe.taiho.minijaba.Parser
import moe.taiho.minijaba.Position

abstract class BaseDecl {
    val nodeType: String
        get() {
            return this.javaClass.simpleName
        }
    var begin: Position? = null
    var end: Position? = null
    fun setPos(begin: Position, end: Position) {
        this.begin = begin
        this.end = end
    }
    fun setPos(loc: Parser.Location) {
        this.begin = loc.begin
        this.end = loc.end
    }
}
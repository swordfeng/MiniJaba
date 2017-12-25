package moe.taiho.minijaba.ast

class ClassDecl(val ident: String, val baseIdent: String?,
                val varList: List<VarDecl>, val methodList: List<MethodDecl>) : BaseDecl()
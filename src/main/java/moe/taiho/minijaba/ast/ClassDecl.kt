package moe.taiho.minijaba.ast

class ClassDecl(
        var ident: String,
        var baseClass: String?,
        var varList: List<VarDecl>,
        var methodList: List<MethodDecl>
) : BaseDecl()
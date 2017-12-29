package moe.taiho.minijaba

import moe.taiho.minijaba.ast.*

object Analyzer {

    class GoalScope(val goal: Goal) {
        val classes = HashMap<String, ClassDecl>()
        val classScopes = HashMap<String, ClassScope>()
        var hasError = false
        init {
            goal.classes.forEach { c ->
                if (classes.containsKey(c.ident)) {
                    printError(c, "semantic error redefinition of class ${c.ident}")
                    hasError = true
                } else {
                    classes[c.ident] = c
                }
            }
            goal.classes.forEach { c ->
                initClassScope(c)
            }
        }
        private fun initClassScope(c: ClassDecl) {
            if (classScopes.containsKey(c.ident)) return
            if (c.baseClass != null) {
                val base = classes[c.baseClass!!]
                if (base == null) {
                    printError(c, "semantic error base class undefined")
                    hasError = true
                    return
                }
                initClassScope(base)
                classScopes[c.ident] = ClassScope(c, this)
            }
            classScopes[c.ident] = ClassScope(c, this)
        }
        fun typeCheck() {
            typeCheck(this)
        }
    }

    class ClassScope(val decl: ClassDecl, val ctx: GoalScope) {
        val variables = HashMap<String, VarDecl>()
        val methods = HashMap<String, MethodDecl>()
        init {
            decl.varList.forEach { v ->
                var err = false
                if (v.type is ClassType) {
                    if (!ctx.classes.containsKey((v.type as ClassType).ident)) {
                        printError(v, "type error unknown type ${(v.type as ClassType).ident}")
                        err = true
                    }
                }
                if (variables.containsKey(v.ident)) {
                    printError(v, "semantic error redefinition of member variable ${v.ident}")
                    err = true
                }
                if (!err){
                    variables[v.ident] = v
                } else {
                    ctx.hasError = true
                }
            }
            decl.methodList.forEach { m ->
                if (methods.containsKey(m.ident)) {
                    printError(m, "semantic error redefinition of member method ${m.ident}")
                    ctx.hasError = true
                } else {
                    methods[m.ident] = m
                }
            }
        }
        fun findMethod(methodName: String): MethodDecl? {
            return methods[methodName] ?: decl.baseClass?.let { base ->
                ctx.classScopes[base]!!.findMethod(methodName)
            }
        }
        fun findVar(varName: String): VarDecl? {
            return variables[varName] ?: decl.baseClass?.let { base ->
                ctx.classScopes[base]!!.findVar(varName)
            }
        }
        fun findVarScope(varName: String): ClassScope? {
            if (variables.containsKey(varName)) return this
            return decl.baseClass?.let { base ->
                ctx.classScopes[base]!!.findVarScope(varName)
            }
        }
    }

    class MethodScope(val decl: MethodDecl, val ctx: ClassScope) {
        val variables = HashMap<String, VarDecl>()
        init {
            decl.paramList.forEach { p ->
                var err = false
                if (p.type is ClassType) {
                    if (!ctx.ctx.classes.containsKey((p.type as ClassType).ident)) {
                        printError(p, "type error unknown type ${(p.type as ClassType).ident}")
                        err = true
                    }
                }
                if (variables.containsKey(p.ident)) {
                    printError(p, "semantic error redefinition of parameter ${p.ident}")
                    err = true
                }
                if (!err){
                    variables[p.ident] = p
                } else {
                    ctx.ctx.hasError = true
                }
            }
            decl.varList.forEach { v ->
                var err = false
                if (v.type is ClassType) {
                    if (!ctx.ctx.classes.containsKey((v.type as ClassType).ident)) {
                        printError(v, "type error unknown type ${(v.type as ClassType).ident}")
                        err = true
                    }
                }
                if (variables.containsKey(v.ident)) {
                    printError(v, "semantic error redefinition of variable ${v.ident}")
                    err = true
                }
                if (!err) {
                    variables[v.ident] = v
                } else {
                    ctx.ctx.hasError = true
                }
            }
        }
        fun findVar(varName: String): VarDecl? {
            return variables[varName] ?: ctx.findVar(varName)
        }
    }

    fun typeCheck(goalScope: GoalScope) {
        //goalScope.goal.mainClass.stmt
        goalScope.classScopes.forEach { _, classScope ->
            typeCheck(classScope)
        }
    }

    fun typeCheck(classScope: ClassScope) {
        classScope.decl.methodList.forEach { m ->
            typeCheck(MethodScope(m, classScope))
        }
    }

    fun typeCheck(methodScope: MethodScope) {
        val methodDecl = methodScope.decl
        val baseMethodDecl = methodScope.ctx.decl.baseClass?.let { base ->
            methodScope.ctx.ctx.classScopes[base]!!.findMethod(methodDecl.ident)
        }
        if (baseMethodDecl != null) {
            if (!isSame(methodDecl.returnType, baseMethodDecl.returnType)) {
                printError(methodDecl.returnType, "type error inconsistent method return type " +
                        "expected ${baseMethodDecl.returnType.typeName()} actual ${methodDecl.returnType.typeName()}")
            }
            if (methodDecl.paramList.size != baseMethodDecl.paramList.size) {
                printError(methodDecl.returnType, "type error inconsistent number of method parameters")
            } else {
                methodDecl.paramList.zip(baseMethodDecl.paramList).forEach { (mp, bp) ->
                    if (!isSame(mp.type, bp.type)) {
                        printError(mp, "type error inconsistent method parameter type " +
                                "expected ${bp.type.typeName()} actual ${mp.type.typeName()}")
                    }
                }
            }
        }
        methodDecl.stmtList.forEach { s ->
            typeCheck(s, methodScope)
        }
        typeCheck(methodScope.decl.returnExp, methodScope.decl.returnType, methodScope)
    }

    fun typeCheck(stmt: Stmt, methodScope: MethodScope) {
        when (stmt) {
            is BlockStmt -> {
                stmt.stmtList.forEach { s -> typeCheck(s, methodScope) }
            }
            is IfStmt -> {
                typeCheck(stmt.cond, BoolType(), methodScope)
                typeCheck(stmt.trueStmt, methodScope)
                typeCheck(stmt.falseStmt, methodScope)
            }
            is WhileStmt -> {
                typeCheck(stmt.cond, BoolType(), methodScope)
                typeCheck(stmt.loopBody, methodScope)
            }
            is AssignStmt -> {
                val varDecl = methodScope.findVar(stmt.ident)
                if (varDecl == null) {
                    printError(stmt, "semantic error assign to undefined variable ${stmt.ident}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(stmt.value, varDecl?.type, methodScope)
            }
            is ArrayAssignStmt -> {
                val varDecl = methodScope.findVar(stmt.ident)
                if (varDecl == null) {
                    printError(stmt, "semantic error assign to undefined variable ${stmt.ident}")
                    methodScope.ctx.ctx.hasError = true
                } else if (!(varDecl.type is IntArrayType)) {
                    printError(stmt, "type error assign element to non-array ${stmt.ident}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(stmt.index, IntType(), methodScope)
                typeCheck(stmt.value, IntType(), methodScope)
            }
            is PrintlnStmt -> {
                typeCheck(stmt.exp, null, methodScope)
            }

            is InvStmt -> { methodScope.ctx.ctx.hasError = true }
            else -> {
                methodScope.ctx.ctx.hasError = true
                throw UnknownError("unexpected statement")
            }
        }
    }

    fun typeCheck(exp: Exp, t: Type?, methodScope: MethodScope): Type? {
        return when (exp) {
            is BracketExp -> typeCheck(exp.value, t, methodScope)
            is AddExp -> {
                if (t != null && !(t is IntType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${IntType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.left, IntType(), methodScope)
                typeCheck(exp.right, IntType(), methodScope)
                return IntType()
            }
            is SubExp -> {
                if (t != null && !(t is IntType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${IntType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.left, IntType(), methodScope)
                typeCheck(exp.right, IntType(), methodScope)
                return IntType()
            }
            is MulExp -> {
                if (t != null && !(t is IntType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${IntType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.left, IntType(), methodScope)
                typeCheck(exp.right, IntType(), methodScope)
                return IntType()
            }
            is AndExp -> {
                if (t != null && !(t is BoolType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${BoolType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.left, BoolType(), methodScope)
                typeCheck(exp.right, BoolType(), methodScope)
                return BoolType()
            }
            is LessThanExp -> {
                if (t != null && !(t is BoolType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${BoolType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.left, IntType(), methodScope)
                typeCheck(exp.right, IntType(), methodScope)
                return BoolType()
            }
            is NotExp -> {
                if (t != null && !(t is BoolType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${BoolType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.value, BoolType(), methodScope)
                return BoolType()
            }
            is ArrayAccessExp -> {
                if (t != null && !(t is IntType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${IntType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.arr, IntArrayType(), methodScope)
                typeCheck(exp.index, IntType(), methodScope)
                return IntType()
            }
            is ArrayLengthExp -> {
                if (t != null && !(t is IntType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${IntType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.arr, IntArrayType(), methodScope)
                return IntType()
            }
            is MethodCallExp -> {
                val objType = typeCheck(exp.obj, null, methodScope)
                if (objType != null && !(objType is ClassType)) {
                    printError(exp.obj, "type error expected an object actual ${objType.typeName()}")
                    methodScope.ctx.ctx.hasError = true
                    return null
                }
                if (objType == null) return null
                val classScope = methodScope.ctx.ctx.classScopes[(objType as ClassType).ident]
                if (classScope == null) {
                    printError(exp.obj, "semantic error unknown type ${objType.ident}")
                    methodScope.ctx.ctx.hasError = true
                    return null
                }
                val methodDecl = classScope.methods[exp.methodName]
                if (methodDecl == null) {
                    printError(exp, "semantic error unknown method ${exp.methodName}")
                    methodScope.ctx.ctx.hasError = true
                    return null
                }
                if (t != null && !canCastTo(methodDecl.returnType, t, methodScope.ctx.ctx)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${methodDecl.returnType.typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                if (exp.args.size != methodDecl.paramList.size) {
                    printError(exp, "type error param num mismatch expected ${methodDecl.paramList.size} actual ${exp.args.size}")
                    methodScope.ctx.ctx.hasError = true
                } else {
                    exp.args.zip(methodDecl.paramList).forEach { (a, p) ->
                        typeCheck(a, p.type, methodScope)
                    }
                }
                return methodDecl.returnType
            }
            is ArrayAllocExp -> {
                if (t != null && !(t is IntArrayType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${IntArrayType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                typeCheck(exp.size, IntType(), methodScope)
                return IntArrayType()
            }
            is ObjectAllocExp -> {
                if (t != null && !canCastTo(ClassType(exp.className), t, methodScope.ctx.ctx)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${exp.className}")
                    methodScope.ctx.ctx.hasError = true
                }
                return ClassType(exp.className)
            }

            is ThisExp -> {
                if (t != null && !canCastTo(ClassType(methodScope.ctx.decl.ident), t, methodScope.ctx.ctx)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${methodScope.ctx.decl.ident}")
                    methodScope.ctx.ctx.hasError = true
                }
                return ClassType(methodScope.ctx.decl.ident)
            }
            is TrueExp -> {
                if (t != null && !(t is BoolType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${BoolType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                return BoolType()
            }
            is FalseExp -> {
                if (t != null && !(t is BoolType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${BoolType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                return BoolType()
            }
            is IntLiteralExp -> {
                if (t != null && !(t is IntType)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${IntType().typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                return IntType()
            }
            is IdentExp -> {
                val varDecl = methodScope.findVar(exp.ident)
                if (varDecl == null) {
                    printError(exp, "semantic error unknown identifier ${exp.ident}")
                    methodScope.ctx.ctx.hasError = true
                    return null
                }
                if (t != null && !canCastTo(varDecl.type, t, methodScope.ctx.ctx)) {
                    printError(exp, "type error expected ${t.typeName()} actual ${varDecl.type.typeName()}")
                    methodScope.ctx.ctx.hasError = true
                }
                return varDecl.type
            }

            is InvExp -> {
                methodScope.ctx.ctx.hasError = true
                return null
            }
            else -> {
                methodScope.ctx.ctx.hasError = true
                throw UnknownError("unexpected expression")
            }
        }
    }

    fun canCastTo(t1: Type, t2: Type, goalScope: GoalScope): Boolean {
        return when (t1) {
            is IntArrayType -> t2 is IntArrayType
            is BoolType -> t2 is BoolType
            is IntType -> t2 is IntType
            is ClassType -> {
                if (!(t2 is ClassType)) return false
                var t = goalScope.classes[t1.ident]
                while (t != null) {
                    if (t.ident == t2.ident) return true
                    t = t.baseClass?.let { bc -> goalScope.classes[bc] }
                }
                return false
            }
            else -> false
        }
    }

    fun isSame(t1: Type, t2: Type): Boolean {
        return when (t1) {
            is IntArrayType -> t2 is IntArrayType
            is BoolType -> t2 is BoolType
            is IntType -> t2 is IntType
            is ClassType -> t2 is ClassType && t1.ident == t2.ident
            else -> false
        }
    }

    fun printError(decl: BaseDecl, msg: String) {
        System.err.println("Error@${decl.begin}-${decl.end}: ${msg}")
    }

    fun extractType(e: Exp, methodScope: MethodScope): Type {
        return when (e) {
            is BracketExp -> extractType(e.value, methodScope)
            is AddExp -> IntType()
            is SubExp -> IntType()
            is MulExp -> IntType()
            is AndExp -> BoolType()
            is LessThanExp -> BoolType()
            is NotExp -> BoolType()

            is ArrayAccessExp -> IntType()
            is ArrayLengthExp -> IntType()
            is MethodCallExp -> methodScope.ctx.ctx
                    .classScopes[(extractType(e.obj, methodScope) as ClassType).ident]!!
                    .methods[e.methodName]!!
                    .returnType
            is ArrayAllocExp -> IntArrayType()
            is ObjectAllocExp -> ClassType(e.className)

            is ThisExp -> ClassType(methodScope.ctx.decl.ident)
            is TrueExp -> BoolType()
            is FalseExp -> BoolType()
            is IntLiteralExp -> IntType()
            is IdentExp -> methodScope.findVar(e.ident)!!.type

            else -> throw Exception()
        }
    }

}
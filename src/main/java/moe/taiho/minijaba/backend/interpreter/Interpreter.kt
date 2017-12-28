package moe.taiho.minijaba.backend.interpreter

import moe.taiho.minijaba.ast.*

object Interpreter {
    fun checkType(type: Type, value: Any, ctx: Context) {
        if (value is Null) return
        if (! when (type) {
            is IntArrayType -> value is Array<*>
            is BoolType -> value is Boolean
            is IntType -> value is Int
            is ClassType -> value is Obj && value.typeDecl.ident == type.ident
            else -> false
        }) {
            if ((type is ClassType || type is IntArrayType) && value is Null) return
            if (type is ClassType && value is Obj) {
                var t: ClassDecl? = value.typeDecl
                while (t != null) {
                    if (t.ident == type.ident) return
                    if (t.baseClass == null) break
                    t = ctx.classes[t.baseClass!!]
                }
            }
            val tName = if (type is ClassType) type.ident else type.javaClass.simpleName
            val vtName = if (value is Obj) value.typeDecl.ident else value.javaClass.simpleName
            throw UnknownError("type mismatch ${tName} -> ${vtName}")
        }
    }

    val BASE_IDENT = "\$base";
    class Null
    class Obj(val typeDecl: ClassDecl, ctx: Context) {
        val variables: HashMap<String, Any> = HashMap()
        init {
            if (typeDecl.baseClass != null) {
                variables[BASE_IDENT] = Obj(ctx.findClass(typeDecl.baseClass!!), ctx)
            }
            typeDecl.varList.forEach { v -> variables[v.ident] = ctx.initValue(v.type) }
        }

        fun findVar(varName: String): Any? {
            if (variables.containsKey(varName)) return variables[varName]
            else if (typeDecl.baseClass != null) return (variables[BASE_IDENT] as Obj).findVar(varName)
            else return null
        }
        fun setVar(varName: String, value: Any): Boolean {
            if (variables.containsKey(varName)) {
                val oldvalue = variables[varName]!!
                if (!(oldvalue is Null) && !(value is Null) && oldvalue.javaClass != value.javaClass) {
                    throw UnknownError("assigned with different type: ${varName} " +
                            "(${oldvalue.javaClass.simpleName} => ${value.javaClass.simpleName})")
                }
                variables[varName] = value
                return true
            }
            else if (typeDecl.baseClass != null) return (variables[BASE_IDENT] as Obj).setVar(varName, value)
            return false
        }

        fun findMethod(methodName: String): MethodDecl {
            val decls = typeDecl.methodList.filter { m -> m.ident == methodName }
            if (decls.size > 0) return decls[0]
            return (variables[BASE_IDENT] as Obj).findMethod(methodName)
        }

        fun callMethod(methodName: String, args: List<Any>, ctx: Context): Any {
            val methodDecl = findMethod(methodName)
            val scope = Scope(ctx.rootScope, this)
            val oldscope = ctx.curScope
            ctx.curScope = scope
            methodDecl.paramList.zip(args).forEach { (p, a) ->
                checkType(p.type, a, ctx)
                scope.variables[p.ident] = a
            }
            methodDecl.varList.forEach { v -> scope.variables[v.ident] = ctx.initValue(v.type) }
            methodDecl.stmtList.forEach { s -> ctx.evalStmt(s) }
            val ret = ctx.evalExp(methodDecl.returnExp)
            checkType(methodDecl.returnType, ret, ctx)
            ctx.curScope = oldscope
            return ret
        }
    }

    class Scope(val parent: Scope?, private val thisObj: Obj?) {
        val variables: HashMap<String, Any> = HashMap()
        fun findVar(varName: String): Any? {
            return variables[varName] ?: thisObj?.findVar(varName) ?: parent?.findVar(varName)
        }
        fun setVar(varName: String, value: Any) {
            if (variables.containsKey(varName)) {
                val oldvalue = variables[varName]!!
                if (!(oldvalue is Null) && !(value is Null) && oldvalue.javaClass != value.javaClass) {
                    throw UnknownError("assigned with different type: ${varName} " +
                            "(${oldvalue.javaClass.simpleName} => ${value.javaClass.simpleName})")
                }
                variables[varName] = value
            } else if (thisObj?.setVar(varName, value) == true) {
                return
            } else {
                parent!!.setVar(varName, value)
            }
        }
        fun getThis(): Obj {
            return thisObj ?: parent!!.getThis()
        }
    }

    class Context(val goal: Goal) {
        val classes: HashMap<String, ClassDecl> = HashMap()
        val rootScope: Scope = Scope(null, null)
        var curScope: Scope = rootScope

        init {
            goal.classes.forEach { c -> classes[c.ident] = c }
        }

        fun run() {
            evalStmt(goal.mainClass.stmt)
        }

        fun findVar(varName: String): Any {
            return curScope.findVar(varName)!!
        }
        fun setVar(varName: String, value: Any) {
            curScope.setVar(varName, value)
        }
        fun findClass(className: String): ClassDecl {
            return classes[className]!!
        }
        fun initValue(type: Type): Any {
            return when (type) {
                is IntArrayType -> Null()
                is BoolType -> false
                is IntType -> 0
                is ClassType -> Null()
                else -> throw UnknownError("Trying to initialize: ${type.javaClass.name}")
            }
        }
        fun evalStmt(s: Stmt) {
            when (s) {
                is BlockStmt -> {
                    val oldscope = curScope
                    curScope = Scope(oldscope, null)
                    s.stmtList.forEach { st -> evalStmt(st) }
                    curScope = oldscope
                }
                is IfStmt -> {
                    if (evalExp(s.cond) as Boolean) {
                        evalStmt(s.trueStmt)
                    } else {
                        evalStmt(s.falseStmt)
                    }
                }
                is WhileStmt -> {
                    while (evalExp(s.cond) as Boolean) {
                        evalStmt(s.loopBody)
                    }
                }
                is AssignStmt -> setVar(s.ident, evalExp(s.value))
                is ArrayAssignStmt -> {
                    val index = evalExp(s.index)
                    checkType(IntType(), index, this)
                    val value = evalExp(s.value)
                    checkType(IntType(), value, this)
                    (findVar(s.ident) as Array<Int>)[index as Int] = value as Int
                }
                is PrintlnStmt -> println(evalExp(s.exp))

                else -> throw UnknownError("unknown stmt ${s}")
            }
        }
        fun evalExp(e: Exp): Any {
            return when (e) {
                is BracketExp -> evalExp(e.value)
                is AddExp -> (evalExp(e.left) as Int) + (evalExp(e.right) as Int)
                is SubExp -> (evalExp(e.left) as Int) - (evalExp(e.right) as Int)
                is MulExp -> (evalExp(e.left) as Int) * (evalExp(e.right) as Int)
                is LessThanExp -> (evalExp(e.left) as Int) < (evalExp(e.right) as Int)
                is AndExp -> (evalExp(e.left) as Boolean) && (evalExp(e.right) as Boolean)
                is NotExp -> !(evalExp(e.value) as Boolean)

                is ArrayAccessExp -> (evalExp(e.arr) as Array<*>)[evalExp(e.index) as Int]!!
                is ArrayLengthExp -> (evalExp(e.arr) as Array<*>).size
                is MethodCallExp -> (evalExp(e.obj) as Obj).callMethod(e.methodName, e.args.map { a -> evalExp(a) }, this)
                is ArrayAllocExp -> Array(evalExp(e.size) as Int, { 0 })
                is ObjectAllocExp -> Obj(findClass(e.className), this)

                is ThisExp -> curScope.getThis()
                is TrueExp -> true
                is FalseExp -> false
                is IntLiteralExp -> e.value
                is IdentExp -> findVar(e.ident)

                else -> throw UnknownError("unknown exp ${e}")
            }
        }
    }
}
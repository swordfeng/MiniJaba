package moe.taiho.minijaba.backend.interpreter

import moe.taiho.minijaba.ast.*

object Interpreter {
    fun checkType(type: Type, value: Any) {
        if (! when (type) {
            is IntArrayType -> value is Array<*>
            is BoolType -> value is Boolean
            is IntType -> value is Int
            is ClassType -> value is Obj && value.typeDecl.ident == type.ident
            else -> false
        }) {
            throw UnknownError("type mismatch ${type.javaClass.simpleName} -> ${value}")
        }
    }

    fun varInit(vs: HashMap<String, Any>, t: ClassDecl, ctx: Context) {
        if (t.baseClass != null) varInit(vs, ctx.findClass(t.baseClass!!), ctx)
        t.varList.forEach { v -> vs[v.ident] = ctx.initValue(v.type) }
    }

    class Obj(val typeDecl: ClassDecl, ctx: Context) {
        val variables: HashMap<String, Any> = HashMap()
        init {
            varInit(variables, typeDecl, ctx)
        }

        fun callMethod(methodName: String, args: List<Any>, ctx: Context): Any {
            val methodDecl = typeDecl.methodList.filter { m -> m.ident == methodName }[0]
            val scope = Scope(ctx.rootScope, this)
            val oldscope = ctx.curScope
            ctx.curScope = scope
            methodDecl.paramList.zip(args).forEach { (p, a) ->
                checkType(p.type, a)
                scope.variables[p.ident] = a
            }
            methodDecl.varList.forEach { v -> scope.variables[v.ident] = ctx.initValue(v.type) }
            methodDecl.stmtList.forEach { s -> ctx.evalStmt(s) }
            val ret = ctx.evalExp(methodDecl.returnExp)
            checkType(methodDecl.returnType, ret)
            ctx.curScope = oldscope
            return ret
        }
    }

    class Scope(val parent: Scope?, private val thisObj: Obj?) {
        val variables: HashMap<String, Any> = HashMap()
        fun findVar(varName: String): Any? {
            return variables[varName] ?: thisObj?.variables?.get(varName) ?: parent?.findVar(varName)
        }
        fun setVar(varName: String, value: Any) {
            if (variables.containsKey(varName)) {
                if (variables[varName]!!.javaClass != value.javaClass) {
                    throw UnknownError("assigned with different type: ${varName}")
                }
                variables[varName] = value
            } else if (thisObj?.variables?.containsKey(varName) == true) {
                if (thisObj.variables[varName]!!.javaClass != value.javaClass) {
                    throw UnknownError("assigned with different type: ${varName}")
                }
                thisObj.variables[varName] = value
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
                is IntArrayType -> arrayOf<Int>()
                is BoolType -> false
                is IntType -> 0
                is ClassType -> Obj(findClass(type.ident), this)
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
                    checkType(IntType(), index)
                    val value = evalExp(s.value)
                    checkType(IntType(), value)
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
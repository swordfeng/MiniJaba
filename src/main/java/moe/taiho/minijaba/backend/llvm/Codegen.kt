package moe.taiho.minijaba.backend.llvm

import moe.taiho.minijaba.Analyzer
import moe.taiho.minijaba.ast.*
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.LLVM.*
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer

class Codegen(val goalScope: Analyzer.GoalScope) {

    companion object {
        private fun <T : Pointer> makepp(vararg args: T): PointerPointer<T> {
            return PointerPointer<T>(*args)
        }
    }

    val lctx: LLVMContextRef = LLVMContextCreate()
    val mod = LLVMModuleCreateWithNameInContext("moe.taiho.minijaba.generated", lctx)

    val intType = LLVMInt32TypeInContext(lctx)
    val int8Type = LLVMInt8TypeInContext(lctx)
    val boolType = LLVMInt1TypeInContext(lctx)
    //val intArrayRefStruct = LLVMStructTypeInContext(lctx,
    //        makepp(LLVMPointerType(intType, 0), intType), 2, 0)
    val arrayType = LLVMPointerType(intType, 0)

    val funcPtr = LLVMPointerType(int8Type, 0)
    val vtRef = LLVMPointerType(funcPtr, 0)

    val coreStruct = LLVMStructTypeInContext(lctx, makepp(vtRef), 1, 0)

    val PRINT_INT = LLVMAddGlobal(mod, LLVMArrayType(int8Type, 4),
            ".str")
    init {
        LLVMSetInitializer(PRINT_INT, LLVMConstArray(int8Type,
                makepp(*("%d\n\u0000".map { c -> LLVMConstInt(int8Type, c.toLong(), 0) }
                        .toTypedArray())), 4))
        LLVMSetGlobalConstant(PRINT_INT, 1)
        LLVMSetLinkage(PRINT_INT, LLVMInternalLinkage)
        LLVMSetAlignment(PRINT_INT, 1)
    }

    val classLayouts = HashMap<String, ClassLayout>()
    val functions = HashMap<String, LLVMValueRef>()

    class ClassLayout(val ctx: Codegen, val classScope: Analyzer.ClassScope, val base: ClassLayout?) {
        val lctx = ctx.lctx
        val funcPtr = ctx.funcPtr
        val struct = LLVMStructCreateNamed(lctx, classScope.decl.ident)
        val fieldMap = HashMap<String, Int>()
        private val baseStruct = base?.struct ?: ctx.coreStruct
        fun initFields() {
            val fields = Array<LLVMTypeRef>(classScope.decl.varList.size + 1) { i ->
                if (i == 0) LLVMPointerType(baseStruct, 0)
                else {
                    val varDecl = classScope.decl.varList[i - 1]
                    fieldMap[varDecl.ident] = i
                    val t = varDecl.type
                    ctx.getType(t)
                }
            }
            LLVMStructSetBody(struct, makepp(*fields), fields.size, 0)
        }
        val methodMap = HashMap<String, Int>()
        init {
            base?.methodMap?.forEach { (n, i) ->
                methodMap[n] = i
            } // copy parent first
            var methodIndex = methodMap.size
            classScope.decl.methodList.forEach { m ->
                if (!methodMap.containsKey(m.ident)) {
                    // new method
                    methodMap[m.ident] = methodIndex++
                }
            }
        }
        var vtDefs: Array<String?>? = null
        var vt: LLVMValueRef? = null
        fun initVTable() {
            vtDefs = Array(methodMap.size, { null })
            if (base != null) {
                for (i in 0..base.vtDefs!!.size-1) {
                    vtDefs!![i] = base.vtDefs!![i]
                }
            }
            classScope.decl.methodList.forEach { m ->
                val i = methodMap[m.ident]!!
                vtDefs!![i] = "${classScope.decl.ident}$${m.ident}"
            }
            val vtfuncs = vtDefs!!.map { funcName ->
                LLVMConstPointerCast(ctx.functions[funcName!!]!!, funcPtr)
            }.toTypedArray()
            val vtData = LLVMConstArray(funcPtr, makepp(*vtfuncs), vtDefs!!.size)
            vt = LLVMAddGlobal(ctx.mod, LLVMArrayType(funcPtr, vtDefs!!.size),
                    "${classScope.decl.ident}$$")
            LLVMSetInitializer(vt, vtData)
        }
    }

    fun initClassLayouts() {
        goalScope.classScopes.forEach { (n, c) -> genClassLayout(n) }
        classLayouts.forEach { (n, l) -> l.initFields() }
    }

    fun genClassLayout(className: String): ClassLayout {
        val l = classLayouts[className]
        if (l != null) return l
        val classScope = goalScope.classScopes[className]!!
        val baseLayout = classScope.decl.baseClass?.let { base -> genClassLayout(base) }
        val layout = ClassLayout(this, classScope, baseLayout)
        classLayouts[className] = layout
        return layout
    }

    fun genMod() {
        // declare external C funcs
        LLVMAddFunction(mod, "printf", LLVMFunctionType(intType,
                makepp(LLVMPointerType(int8Type, 0)), 1, 1))
        LLVMAddFunction(mod, "GC_malloc", LLVMFunctionType(LLVMPointerType(int8Type, 0),
                makepp(intType), 1, 0))
        LLVMAddFunction(mod, "malloc", LLVMFunctionType(LLVMPointerType(int8Type, 0),
                makepp(intType), 1, 0))
        LLVMAddFunction(mod, "GC_collect_a_little", LLVMFunctionType(intType,
                makepp<LLVMTypeRef>(), 0, 0))

        initClassLayouts()
        // gen function signatures
        goalScope.classScopes.forEach { (className, classScope) ->
            classScope.methods.forEach { (methodName, methodDecl) ->
                val functionName = "${className}$${methodName}"
                val function = LLVMAddFunction(mod, functionName, genFuncType(methodDecl, classScope.decl))
                functions[functionName] = function
            }
        }
        // gen vtables
        classLayouts.forEach { className, layout ->
            layout.initVTable()
        }
        // gen function bodies
        goalScope.classScopes.forEach { (className, classScope) ->
            classScope.methods.forEach { (methodName, methodDecl) ->
                val functionName = "${className}$${methodName}"
                val methodScope = Analyzer.MethodScope(methodDecl, classScope)
                genFunction(functions[functionName]!!, methodScope)
            }
        }

        val mainClassDecl = ClassDecl(goalScope.goal.mainClass.ident, null, listOf(), listOf())
        val mainClassScope = Analyzer.ClassScope(mainClassDecl, goalScope)
        val mainMethodDecl = MethodDecl("main", IntType(), listOf(), listOf(), listOf(), InvExp())
        val mainMethodScope = Analyzer.MethodScope(mainMethodDecl, mainClassScope)
        val mainFunc = LLVMAddFunction(mod, "main", LLVMFunctionType(
                intType, makepp<LLVMValueRef>(), 0, 0))
        var block = LLVMAppendBasicBlockInContext(lctx, mainFunc, "entry")
        block = genStatement(mainFunc, block, goalScope.goal.mainClass.stmt, mainMethodScope,
                HashMap(), Counter())
        val builder = LLVMCreateBuilderInContext(lctx)
        LLVMPositionBuilderAtEnd(builder, block)
        LLVMBuildRet(builder, LLVMConstInt(intType, 0, 0))
    }

    fun printMod() {
        println(LLVMPrintModuleToString(mod).string)
    }

    fun genFuncType(methodDecl: MethodDecl, classDecl: ClassDecl): LLVMTypeRef {
        val className = classDecl.ident
        val retType = getType(methodDecl.returnType)
        val paramsType = (listOf(LLVMPointerType(classLayouts[className]!!.struct, 0))
                + methodDecl.paramList.map { p -> getType(p.type) }).toTypedArray()
        return LLVMFunctionType(retType, makepp(*paramsType), paramsType.size, 0)
    }

    class Counter {
        private var value = 0
        fun next(): Int = value++
    }

    fun genFunction(fn: LLVMValueRef, methodScope: Analyzer.MethodScope) {
        val m = methodScope.decl

        val varMap = HashMap<String, LLVMValueRef>()
        val counter = Counter()

        // initialize frame
        var lastblock = LLVMAppendBasicBlockInContext(lctx, fn, "entry")
        val beginBuilder = LLVMCreateBuilderInContext(lctx)
        LLVMPositionBuilderAtEnd(beginBuilder, lastblock)
        varMap["this"] = LLVMGetParam(fn, 0)
        m.paramList.zip(1..m.paramList.size).forEach { (p, i) ->
            val paramPtr = LLVMBuildAlloca(beginBuilder, getType(p.type), "${counter.next()}.p.${p.ident}")
            varMap[p.ident] = paramPtr
            val paramVal = LLVMGetParam(fn, i)
            LLVMBuildStore(beginBuilder, paramVal, paramPtr)
        }
        m.varList.forEach { v ->
            val varPtr = LLVMBuildAlloca(beginBuilder, getType(v.type), "${counter.next()}.l.${v.ident}")
            varMap[v.ident] = varPtr
            LLVMBuildStore(beginBuilder, getConst(v.type), varPtr)
        }

        m.stmtList.forEach { s -> lastblock = genStatement(fn, lastblock, s, methodScope, varMap, counter) }

        val endBuilder = LLVMCreateBuilderInContext(lctx)
        LLVMPositionBuilderAtEnd(endBuilder, lastblock)
        val retval = genExpression(endBuilder, m.returnExp, methodScope, varMap, counter)
        LLVMBuildRet(endBuilder, retval)
    }

    fun genStatement(fn: LLVMValueRef, block: LLVMBasicBlockRef, s: Stmt, methodScope: Analyzer.MethodScope,
                     varMap: HashMap<String, LLVMValueRef>, counter: Counter): LLVMBasicBlockRef {
        return when (s) {
            is BlockStmt -> {
                var lastblock = block
                s.stmtList.forEach { st -> lastblock = genStatement(fn, lastblock, st, methodScope, varMap, counter) }
                lastblock
            }
            is IfStmt -> {
                val builder = LLVMCreateBuilderInContext(lctx)
                LLVMPositionBuilderAtEnd(builder, block)
                val cond = genExpression(builder, s.cond, methodScope, varMap, counter)

                val stcount = counter.next()
                var trueBlock = LLVMAppendBasicBlock(fn, "${stcount}.iftrue")
                var falseBlock = LLVMAppendBasicBlock(fn, "${stcount}.iffalse")
                val endBlock = LLVMAppendBasicBlock(fn, "${stcount}.ifend")
                LLVMBuildCondBr(builder, cond, trueBlock, falseBlock)

                trueBlock = genStatement(fn, trueBlock, s.trueStmt, methodScope, varMap, counter)
                LLVMPositionBuilderAtEnd(builder, trueBlock)
                LLVMBuildBr(builder, endBlock)

                falseBlock = genStatement(fn, falseBlock, s.falseStmt, methodScope, varMap, counter)
                LLVMPositionBuilderAtEnd(builder, falseBlock)
                LLVMBuildBr(builder, endBlock)
                endBlock
            }
            is WhileStmt -> {
                val builder = LLVMCreateBuilderInContext(lctx)
                LLVMPositionBuilderAtEnd(builder, block)

                val stcount = counter.next()
                var loopBlock = LLVMAppendBasicBlock(fn, "${stcount}.whilebody")
                val chkBlock = LLVMAppendBasicBlock(fn, "${stcount}.whilecond")
                val endBlock = LLVMAppendBasicBlock(fn, "${stcount}.whileend")
                LLVMBuildBr(builder, chkBlock)

                LLVMPositionBuilderAtEnd(builder, chkBlock)
                val cond = genExpression(builder, s.cond, methodScope, varMap, counter)
                LLVMBuildCondBr(builder, cond, loopBlock, endBlock)

                loopBlock = genStatement(fn, loopBlock, s.loopBody, methodScope, varMap, counter)
                LLVMPositionBuilderAtEnd(builder, loopBlock)
                LLVMBuildBr(builder, chkBlock)

                endBlock
            }
            is AssignStmt -> {
                val builder = LLVMCreateBuilderInContext(lctx)
                LLVMPositionBuilderAtEnd(builder, block)
                val value = genExpression(builder, s.value, methodScope, varMap, counter)
                val ptr = getVal(builder, s.ident, methodScope.ctx, varMap, counter)
                LLVMBuildStore(builder, value, ptr)
                block
            }
            is ArrayAssignStmt -> {
                val builder = LLVMCreateBuilderInContext(lctx)
                LLVMPositionBuilderAtEnd(builder, block)
                val index = genExpression(builder, s.index, methodScope, varMap, counter)
                val value = genExpression(builder, s.value, methodScope, varMap, counter)
                val arrayRefPtr = getVal(builder, s.ident, methodScope.ctx, varMap, counter)
                val arrayRef = LLVMBuildLoad(builder, arrayRefPtr, "${counter.next()}")
                val arrayPtr = LLVMBuildGEP(builder, arrayRef, makepp(LLVMConstInt(intType, 1, 0)),
                        1, "${counter.next()}")
                val elemPtr = LLVMBuildInBoundsGEP(builder, arrayPtr,
                        makepp(index), 1, "${counter.next()}")
                LLVMBuildStore(builder, value, elemPtr)
                block
            }
            is PrintlnStmt -> {
                val printf = LLVMGetNamedFunction(mod, "printf")
                val builder = LLVMCreateBuilderInContext(lctx)
                LLVMPositionBuilderAtEnd(builder, block)
                val value = genExpression(builder, s.exp, methodScope, varMap, counter)
                val fmt = LLVMBuildInBoundsGEP(builder, PRINT_INT,
                        makepp(LLVMConstInt(intType, 0, 0),
                                LLVMConstInt(intType, 0, 0)), 2, "${counter.next()}")
                LLVMBuildCall(builder, printf,
                        makepp(fmt, value), 2, "${counter.next()}")
                block
            }

            else -> throw Exception("compile error")
        }
    }

    fun genExpression(builder: LLVMBuilderRef, e: Exp, methodScope: Analyzer.MethodScope,
                     varMap: HashMap<String, LLVMValueRef>, counter: Counter): LLVMValueRef {
        return when (e) {
            is BracketExp -> genExpression(builder, e.value, methodScope, varMap, counter)
            is AddExp ->
                LLVMBuildAdd(builder,
                    genExpression(builder, e.left, methodScope, varMap, counter),
                    genExpression(builder, e.right, methodScope, varMap, counter),
                    "${counter.next()}")
            is SubExp ->
                LLVMBuildSub(builder,
                    genExpression(builder, e.left, methodScope, varMap, counter),
                    genExpression(builder, e.right, methodScope, varMap, counter),
                    "${counter.next()}")
            is MulExp ->
                LLVMBuildMul(builder,
                        genExpression(builder, e.left, methodScope, varMap, counter),
                        genExpression(builder, e.right, methodScope, varMap, counter),
                        "${counter.next()}")
            is AndExp ->
                LLVMBuildAnd(builder,
                        genExpression(builder, e.left, methodScope, varMap, counter),
                        genExpression(builder, e.right, methodScope, varMap, counter),
                        "${counter.next()}")
            is LessThanExp ->
                LLVMBuildICmp(builder, LLVMIntSLT,
                        genExpression(builder, e.left, methodScope, varMap, counter),
                        genExpression(builder, e.right, methodScope, varMap, counter),
                        "${counter.next()}")
            is NotExp -> LLVMBuildXor(builder,
                    genExpression(builder, e.value, methodScope, varMap, counter),
                    LLVMConstInt(boolType, 1, 0),
                    "${counter.next()}")

            is ArrayAccessExp -> {
                val arrayRef = genExpression(builder, e.arr, methodScope, varMap, counter)
                val arrayPtr = LLVMBuildGEP(builder, arrayRef, makepp(LLVMConstInt(intType, 1, 0)),
                        1, "${counter.next()}")
                val index = genExpression(builder, e.index, methodScope, varMap, counter)
                val valPtr = LLVMBuildInBoundsGEP(builder, arrayPtr,
                        makepp(index), 1, "${counter.next()}")
                LLVMBuildLoad(builder, valPtr, "${counter.next()}")
            }
            is ArrayLengthExp -> {
                val arrayRef = genExpression(builder, e.arr, methodScope, varMap, counter)
                LLVMBuildLoad(builder, arrayRef, "${counter.next()}")
            }
            is MethodCallExp -> {
                val objType = Analyzer.extractType(e.obj, methodScope) as ClassType
                val objClassLayout = classLayouts[objType.ident]!!
                val funcIndex = objClassLayout.methodMap[e.methodName]!!
                val targetClassScope = goalScope.classScopes[objType.ident]!!
                val targetClassDecl = targetClassScope.decl
                val targetMethodDecl = targetClassScope.methods[e.methodName]!!

                val obj = genExpression(builder, e.obj, methodScope, varMap, counter)
                val args = mutableListOf(obj)
                e.args.zip(targetMethodDecl.paramList).forEach { (a, p) ->
                    val arg = genExpression(builder, a, methodScope, varMap, counter)
                    val argc = LLVMBuildBitCast(builder, arg, getType(p.type), "${counter.next()}")
                    args.add(argc)
                }

                val objV = LLVMBuildBitCast(builder, obj, LLVMPointerType(coreStruct, 0), "${counter.next()}")
                val vtField = LLVMBuildGEP(builder, objV,
                        makepp(LLVMConstInt(intType, 0, 0),
                                LLVMConstInt(intType, 0, 0)), 2, "${counter.next()}")
                val vtPtr = LLVMBuildLoad(builder, vtField, "${counter.next()}")
                val funcPtrField = LLVMBuildGEP(builder, vtPtr,
                        makepp(LLVMConstInt(intType, funcIndex.toLong(), 0)), 1, "${counter.next()}")
                val funcPtr = LLVMBuildLoad(builder, funcPtrField, "${counter.next()}")
                val func = LLVMBuildBitCast(builder, funcPtr, LLVMPointerType(genFuncType(targetMethodDecl, targetClassDecl), 0), "${counter.next()}")
                LLVMBuildCall(builder, func, makepp(*args.toTypedArray()), args.size, "${counter.next()}")
            }
            is ArrayAllocExp -> {
                val arrSize = genExpression(builder, e.size, methodScope, varMap, counter)
                val mallocSize = LLVMBuildAdd(builder, arrSize, LLVMConstInt(intType, 1, 0), "${counter.next()}")
                val arrPtr = LLVMBuildArrayMalloc(builder, intType, mallocSize, "${counter.next()}")
                LLVMBuildStore(builder, arrSize, arrPtr)
                // todo initialize
                arrPtr
            }
            is ObjectAllocExp -> {
                val layout = classLayouts[e.className]!!
                val obj = LLVMBuildMalloc(builder, layout.struct, "${counter.next()}")
                val objV = LLVMBuildBitCast(builder, obj, LLVMPointerType(coreStruct, 0), "${counter.next()}")
                val vtField = LLVMBuildGEP(builder, objV,
                        makepp(LLVMConstInt(intType, 0, 0),
                                LLVMConstInt(intType, 0, 0)), 2, "${counter.next()}")
                val vt = LLVMBuildGEP(builder, layout.vt,
                        makepp(LLVMConstInt(intType, 0, 0),
                                LLVMConstInt(intType, 0, 0)), 2, "${counter.next()}")
                LLVMBuildStore(builder, vt, vtField)
                // todo initialize
                obj
            }

            is ThisExp -> varMap["this"]!!
            is TrueExp -> LLVMConstInt(boolType, 1, 0)
            is FalseExp -> LLVMConstInt(boolType, 1, 0)
            is IntLiteralExp -> LLVMConstInt(intType, e.value.toLong(), 1)
            is IdentExp -> {
                val ptr = getVal(builder, e.ident, methodScope.ctx, varMap, counter)
                LLVMBuildLoad(builder, ptr, "${counter.next()}")
            }

            else -> throw Exception("compile error")
        }
    }

    private fun genSizeOf(builder: LLVMBuilderRef, t: LLVMTypeRef, counter: Counter): LLVMValueRef {
        val zeroPtr = LLVMBuildInBoundsGEP(builder, LLVMConstNull(LLVMPointerType(t, 0)),
                makepp(LLVMConstInt(intType, 1, 0)), 1, "${counter.next()}")
        return LLVMBuildPtrToInt(builder, zeroPtr, intType, "${counter.next()}")
    }

    private fun getVal(builder: LLVMBuilderRef, ident: String, classScope: Analyzer.ClassScope,
                       varMap: HashMap<String, LLVMValueRef>, counter: Counter):
            LLVMValueRef {
        if (varMap.containsKey(ident)) {
            return varMap[ident]!!
        } else {
            var cScope = classScope
            while (!cScope.variables.containsKey(ident)) {
                cScope = goalScope.classScopes[cScope.decl.baseClass!!]!!
            }
            val layout = classLayouts[cScope.decl.ident]!!
            val thisPtr = LLVMBuildBitCast(builder, varMap["this"], LLVMPointerType(layout.struct, 0), "${counter.next()}")
            val fieldPtr = LLVMBuildStructGEP(builder, thisPtr, layout.fieldMap[ident]!!,
                    "${counter.next()}")
            return fieldPtr
        }
    }


    private fun getType(t: Type): LLVMTypeRef {
        return when (t) {
            is IntType -> intType
            is BoolType -> boolType
            is IntArrayType -> arrayType
            is ClassType -> LLVMPointerType(classLayouts[t.ident]!!.struct, 0)
            else -> throw Exception("compile error")
        }
    }

    private fun getConst(t: Type): LLVMValueRef {
        return LLVMConstNull(getType(t))
    }

}
package moe.taiho.minijaba.backend.native

import moe.taiho.minijaba.Analyzer
import moe.taiho.minijaba.ast.*
import org.bytedeco.javacpp.LLVM.*
import org.bytedeco.javacpp.PointerPointer

class Codegen(val goalScope: Analyzer.GoalScope) {

    val lctx: LLVMContextRef = LLVMContextCreate()
    val mod = LLVMModuleCreateWithNameInContext("moe_taiho_minijaba_generated", lctx)

    val PRINT_INT = LLVMConstString("%d\n", 3, 0)

    val vtRef = LLVMPointerType(LLVMPointerType(LLVMInt8Type(), 0), 0)
    val baseStruct = LLVMStructTypeInContext(lctx,
            PointerPointer<LLVMTypeRef>(*arrayOf(vtRef)), 1, 0)

    val intArrayRefStruct = LLVMStructTypeInContext(lctx,
            PointerPointer(LLVMInt32Type(), LLVMPointerType(LLVMInt32Type(), 0)), 2, 0)

    val classLayouts = HashMap<String, ClassLayout>()
    val functions = HashMap<String, LLVMValueRef>()

    class ClassLayout(val ctx: Codegen, val classScope: Analyzer.ClassScope, base: ClassLayout?) {
        val lctx = ctx.lctx
        val struct = LLVMStructCreateNamed(lctx, classScope.decl.ident)
        val fieldMap = HashMap<String, Int>()
        private val baseStruct = base?.struct ?: ctx.baseStruct
        fun initFields() {
            val fields = Array<LLVMTypeRef>(classScope.decl.varList.size + 1) { i ->
                if (i == 0) baseStruct
                else {
                    val varDecl = classScope.decl.varList[i - 1]
                    fieldMap[varDecl.ident] = i
                    val t = varDecl.type
                    ctx.getType(t)
                }
            }
            LLVMStructSetBody(struct, PointerPointer(*fields), fields.size, 0)
        }
        val methodMap = HashMap<String, Int>()
        init {
            base?.methodMap?.forEach { (n, i) -> methodMap[n] = i } // copy names first
            var methodIndex = methodMap.size
            classScope.decl.methodList.forEach { m ->
                if (!methodMap.containsKey(m.ident)) {
                    // new method
                    methodMap[m.ident] = methodIndex++
                }
            }
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

    fun gen() {
        initClassLayouts()
        // declare external C funcs
        LLVMAddFunction(mod, "printf", LLVMFunctionType(LLVMInt32Type(),
                PointerPointer<LLVMTypeRef>(LLVMPointerType(LLVMInt8Type(), 0)), 1, 1))
        LLVMAddFunction(mod, "GC_malloc", LLVMFunctionType(LLVMPointerType(LLVMInt8Type(), 0),
                PointerPointer<LLVMTypeRef>(LLVMInt32Type()), 1, 0))
        LLVMAddFunction(mod, "GC_collect_a_little", LLVMFunctionType(LLVMInt32Type(),
                PointerPointer<LLVMTypeRef>(), 0, 0))
        // gen function signatures
        goalScope.classScopes.forEach { (className, classScope) ->
            classScope.methods.forEach { (methodName, methodDecl) ->
                val functionName = "${className}$${methodName}"
                val function = LLVMAddFunction(mod, functionName, genFuncType(methodDecl, classScope.decl))
                functions[functionName] = function
            }
        }
        goalScope.classScopes.forEach { (className, classScope) ->
            classScope.methods.forEach { (methodName, methodDecl) ->
                val functionName = "${className}$${methodName}"
                val methodScope = Analyzer.MethodScope(methodDecl, classScope)
                genFunction(functions[functionName]!!, methodScope)
            }
        }
    }

    fun genFuncType(methodDecl: MethodDecl, classDecl: ClassDecl): LLVMTypeRef {
        val className = classDecl.ident
        val methodName = methodDecl.ident
        val retType = getType(methodDecl.returnType)
        val paramsType = (listOf(classLayouts[className]!!.struct) + methodDecl.paramList.map { p -> getType(p.type) }).toTypedArray()
        return LLVMFunctionType(retType, PointerPointer(*paramsType), paramsType.size, 0)
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
        var lastblock = LLVMAppendBasicBlock(fn, "entry")
        val beginBuilder = LLVMCreateBuilder()
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

        val endBuilder = LLVMCreateBuilder()
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
                val builder = LLVMCreateBuilder()
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
                val builder = LLVMCreateBuilder()
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
                val builder = LLVMCreateBuilder()
                LLVMPositionBuilderAtEnd(builder, block)
                val value = genExpression(builder, s.value, methodScope, varMap, counter)
                val ptr = getVal(builder, s.ident, methodScope.ctx, varMap, counter)
                LLVMBuildStore(builder, value, ptr)
                block
            }
            is ArrayAssignStmt -> {
                val builder = LLVMCreateBuilder()
                LLVMPositionBuilderAtEnd(builder, block)
                val index = genExpression(builder, s.index, methodScope, varMap, counter)
                val value = genExpression(builder, s.value, methodScope, varMap, counter)
                val arrayRef = getVal(builder, s.ident, methodScope.ctx, varMap, counter)
                val arrayPtr = LLVMBuildExtractValue(builder, arrayRef, 1, "${counter.next()}")
                val elemPtr = LLVMBuildInBoundsGEP(builder, arrayPtr,
                        PointerPointer<LLVMValueRef>(*arrayOf(index)), 1, "${counter.next()}")
                LLVMBuildStore(builder, value, elemPtr)
                block
            }
            is PrintlnStmt -> {
                val printf = LLVMGetNamedFunction(mod, "printf")
                val builder = LLVMCreateBuilder()
                LLVMPositionBuilderAtEnd(builder, block)
                val value = genExpression(builder, s.exp, methodScope, varMap, counter)
                LLVMBuildCall(builder, printf,
                        PointerPointer<LLVMValueRef>(PRINT_INT, value), 2, "${counter.next()}")
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
                    LLVMConstInt(LLVMInt1Type(), 1, 0),
                    "${counter.next()}")

            is ArrayAccessExp -> {
                val arrayRef = genExpression(builder, e.arr, methodScope, varMap, counter)
                val arrayPtr = LLVMBuildExtractValue(builder, arrayRef, 1, "${counter.next()}")
                val index = genExpression(builder, e.index, methodScope, varMap, counter)
                LLVMBuildInBoundsGEP(builder, arrayPtr,
                        PointerPointer<LLVMValueRef>(*arrayOf(index)), 1, "${counter.next()}")
            }
            is ArrayLengthExp -> {
                val arrayRef = genExpression(builder, e.arr, methodScope, varMap, counter)
                LLVMBuildExtractValue(builder, arrayRef, 0, "${counter.next()}")
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

                val objV = LLVMBuildBitCast(builder, obj, LLVMPointerType(baseStruct, 0), "${counter.next()}")
                val vtField = LLVMBuildGEP(builder, objV,
                        PointerPointer<LLVMValueRef>(LLVMConstInt(LLVMInt32Type(), 0, 0)), 1, "${counter.next()}")
                val vtPtr = LLVMBuildLoad(builder, vtField, "${counter.next()}")
                val funcPtr = LLVMBuildGEP(builder, vtPtr,
                        PointerPointer<LLVMValueRef>(LLVMConstInt(LLVMInt32Type(), funcIndex.toLong(), 0)), 1, "${counter.next()}")
                val func = LLVMBuildBitCast(builder, funcPtr, genFuncType(targetMethodDecl, targetClassDecl), "${counter.next()}")
                LLVMBuildCall(builder, func, PointerPointer(*args.toTypedArray()), args.size, "${counter.next()}")
            }
            is ArrayAllocExp -> {
                val arrSize = genExpression(builder, e.size, methodScope, varMap, counter)
                val arrPtr = LLVMBuildArrayMalloc(builder, LLVMInt32Type(), arrSize, "${counter.next()}")
                LLVMConstStruct(PointerPointer(arrPtr, arrSize), 2, 0)
            }
            is ObjectAllocExp -> {
                val layout = classLayouts[e.className]!!
                val objPtr = LLVMBuildMalloc(builder, layout.struct, "${counter.next()}")
                // todo set vtable
                objPtr
            }

            is ThisExp -> varMap["this"]!!
            is TrueExp -> LLVMConstInt(LLVMInt1Type(), 1, 0)
            is FalseExp -> LLVMConstInt(LLVMInt1Type(), 1, 0)
            is IntLiteralExp -> LLVMConstInt(LLVMInt32Type(), e.value.toLong(), 1)
            is IdentExp -> getVal(builder, e.ident, methodScope.ctx, varMap, counter)

            else -> throw Exception("compile error")
        }
    }

    private fun genSizeOf(builder: LLVMBuilderRef, t: LLVMTypeRef, counter: Counter): LLVMValueRef {
        val zeroPtr = LLVMBuildInBoundsGEP(builder, LLVMConstNull(LLVMPointerType(t, 0)),
                PointerPointer<LLVMValueRef>(*arrayOf(LLVMConstInt(LLVMInt32Type(), 1, 0))), 1, "${counter.next()}")
        return LLVMBuildPtrToInt(builder, zeroPtr, LLVMInt32Type(), "${counter.next()}")
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
            is IntType -> LLVMInt32Type()
            is BoolType -> LLVMInt1Type()
            is IntArrayType -> intArrayRefStruct
            is ClassType -> classLayouts[t.ident]!!.struct
            else -> throw Exception("compile error")
        }
    }

    private fun getConst(t: Type): LLVMValueRef {
        return when (t) {
            is IntType -> LLVMConstInt(LLVMInt32Type(), 0, 1)
            is BoolType -> LLVMConstInt(LLVMInt1Type(), 0, 1)
            is IntArrayType -> LLVMConstStruct(
                    PointerPointer(LLVMConstInt(LLVMInt32Type(), 0, 0),
                    LLVMConstNull(LLVMInt32Type())), 2, 0)
            is ClassType -> LLVMConstNull(classLayouts[t.ident]!!.struct)
            else -> throw Exception("compile error")
        }
    }

}
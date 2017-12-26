package moe.taiho.minijaba.backend.bytecode

import moe.taiho.minijaba.Analyzer
import moe.taiho.minijaba.ast.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*

class Codegen(val ctx: Analyzer.GoalScope) {
    val CLASS_NAME_PREFIX = "moe/taiho/minijaba/generated/"

    fun genMainClass(): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(V1_8, ACC_PUBLIC, CLASS_NAME_PREFIX + ctx.goal.mainClass.ident, null,
                "java/lang/Object", null)
        val mainClassDecl = ClassDecl(ctx.goal.mainClass.ident, null, listOf(), listOf())
        val mainClassScope = Analyzer.ClassScope(mainClassDecl, ctx)
        val mainMethodDecl = MethodDecl("main", IntType(), listOf(), listOf(), listOf(), InvExp())
        val mainMethodScope = Analyzer.MethodScope(mainMethodDecl, mainClassScope)
        val mw = cw.visitMethod(ACC_PUBLIC or ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        mw.visitCode()
        val methodStart = Label()
        val methodEnd = Label()
        mw.visitLocalVariable(ctx.goal.mainClass.mainArg, "[Ljava/lang/String;", null, methodStart, methodEnd, 0)
        mw.visitLabel(methodStart)
        genStatement(mw, ctx.goal.mainClass.stmt, mainMethodScope, HashMap())
        mw.visitLabel(methodEnd)
        mw.visitInsn(RETURN)
        mw.visitEnd()
        mw.visitMaxs(-1, -1)
        genInitMethod(cw, mainClassScope)
        cw.visitEnd()
        return cw.toByteArray()
    }

    fun genClass(classScope: Analyzer.ClassScope): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        val classDecl = classScope.decl
        cw.visit(V1_8, ACC_PUBLIC, CLASS_NAME_PREFIX + classDecl.ident, null,
                classDecl.baseClass?.let { v -> CLASS_NAME_PREFIX + v } ?: "java/lang/Object", null);
        classDecl.varList.forEach { v ->
            cw.visitField(ACC_PUBLIC, v.ident, genDescriptor(v.type), null, genDefault(v.type)).visitEnd()
        }
        genInitMethod(cw, classScope)
        classDecl.methodList.forEach { m ->
            genMethod(cw, Analyzer.MethodScope(m, classScope))
        }
        cw.visitEnd()
        return cw.toByteArray()
    }

    fun genInitMethod(cw: ClassWriter, classScope: Analyzer.ClassScope) {
        val classDecl = classScope.decl
        val mw = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
        mw.visitCode()
        mw.visitIntInsn(ALOAD, 0)
        mw.visitMethodInsn(INVOKESPECIAL, classDecl.baseClass?.let { v -> CLASS_NAME_PREFIX + v } ?: "java/lang/Object",
                "<init>", "()V", false)
        mw.visitInsn(RETURN)
        mw.visitEnd()
        mw.visitMaxs(-1, -1)
    }

    fun genMethod(cw: ClassWriter, methodScope: Analyzer.MethodScope) {
        val methodDecl = methodScope.decl
        val mw = cw.visitMethod(ACC_PUBLIC, methodDecl.ident, genDescriptor(methodDecl), null, null)
        mw.visitCode()
        val methodStart = Label()
        val methodEnd = Label()
        mw.visitLabel(methodStart)
        var varIndex = 0
        val varMap = HashMap<String, Int>()
        methodDecl.paramList.forEach { p ->
            mw.visitParameter(p.ident, 0)
        }
        mw.visitLocalVariable("this", genDescriptor(ClassType(methodScope.ctx.decl.ident)), null, methodStart, methodEnd, varIndex++)
        methodDecl.paramList.forEach { p ->
            varMap[p.ident] = varIndex
            mw.visitLocalVariable(p.ident, genDescriptor(p.type), null, methodStart, methodEnd, varIndex)
            varIndex++
        }
        methodDecl.varList.forEach { v ->
            varMap[v.ident] = varIndex
            mw.visitLocalVariable(v.ident, genDescriptor(v.type), null, methodStart, methodEnd, varIndex)
            varIndex++
        }
        methodDecl.stmtList.forEach { s ->
            genStatement(mw, s, methodScope, varMap)
        }
        genExpression(mw, methodDecl.returnExp, methodScope, varMap)
        mw.visitLabel(methodEnd)
        when (methodDecl.returnType) {
            is IntArrayType -> mw.visitInsn(ARETURN)
            is BoolType -> mw.visitInsn(IRETURN)
            is IntType -> mw.visitInsn(IRETURN)
            is ClassType -> mw.visitInsn(ARETURN)
            else -> throw Exception("compile error")
        }
        mw.visitEnd()
        mw.visitMaxs(-1, -1)
    }

    fun genStatement(mw: MethodVisitor, s: Stmt, methodScope: Analyzer.MethodScope, varMap: HashMap<String, Int>) {
        when (s) {
            is BlockStmt -> {
                s.stmtList.forEach { st -> genStatement(mw, st, methodScope, varMap) }
            }
            is IfStmt -> {
                val falseLabel = Label()
                val endLabel = Label()
                genExpression(mw, s.cond, methodScope, varMap)
                mw.visitJumpInsn(IFEQ, falseLabel)
                genStatement(mw, s.trueStmt, methodScope, varMap)
                mw.visitJumpInsn(GOTO, endLabel)
                mw.visitLabel(falseLabel)
                genStatement(mw, s.falseStmt, methodScope, varMap)
                mw.visitLabel(endLabel)
            }
            is WhileStmt -> {
                val chkLabel = Label()
                val startLabel = Label()
                mw.visitJumpInsn(GOTO, chkLabel)
                mw.visitLabel(startLabel)
                genStatement(mw, s.loopBody, methodScope, varMap)
                mw.visitLabel(chkLabel)
                genExpression(mw, s.cond, methodScope, varMap)
                mw.visitJumpInsn(IFNE, startLabel)
            }
            is AssignStmt -> {
                val t = methodScope.findVar(s.ident)!!.type
                if (varMap.containsKey(s.ident)) {
                    // local variable
                    genExpression(mw, s.value, methodScope, varMap)
                    val index = varMap[s.ident]!!
                    when (t) {
                        is IntType, is BoolType -> mw.visitIntInsn(ISTORE, index)
                        else -> mw.visitIntInsn(ASTORE, index)
                    }
                } else {
                    // field
                    mw.visitIntInsn(ALOAD, 0)
                    genExpression(mw, s.value, methodScope, varMap)
                    mw.visitFieldInsn(PUTFIELD, CLASS_NAME_PREFIX + methodScope.ctx.decl.ident, s.ident, genDescriptor(t))
                }
            }
            is ArrayAssignStmt -> {
                if (varMap.containsKey(s.ident)) {
                    // local
                    mw.visitIntInsn(ALOAD, varMap[s.ident]!!)
                    genExpression(mw, s.index, methodScope, varMap)
                    genExpression(mw, s.value, methodScope, varMap)
                    mw.visitInsn(IASTORE)
                } else {
                    // field
                    mw.visitIntInsn(ALOAD, 0)
                    mw.visitFieldInsn(GETFIELD, CLASS_NAME_PREFIX + methodScope.ctx.decl.ident, s.ident, "[I")
                    genExpression(mw, s.index, methodScope, varMap)
                    genExpression(mw, s.value, methodScope, varMap)
                    mw.visitInsn(IASTORE)
                }
            }
            is PrintlnStmt -> {
                mw.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                val t = genExpression(mw, s.exp, methodScope, varMap)
                mw.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(${genDescriptor(t)})V", false)
            }
            else -> throw Exception("compile error")
        }
    }
    fun genExpression(mw: MethodVisitor, e: Exp, methodScope: Analyzer.MethodScope, varMap: HashMap<String, Int>): Type {
        return when (e) {
            is BracketExp -> genExpression(mw, e.value, methodScope, varMap)
            is AddExp -> {
                genExpression(mw, e.left, methodScope, varMap)
                genExpression(mw, e.right, methodScope, varMap)
                mw.visitInsn(IADD)
                IntType()
            }
            is SubExp -> {
                genExpression(mw, e.left, methodScope, varMap)
                genExpression(mw, e.right, methodScope, varMap)
                mw.visitInsn(ISUB)
                IntType()
            }
            is MulExp -> {
                genExpression(mw, e.left, methodScope, varMap)
                genExpression(mw, e.right, methodScope, varMap)
                mw.visitInsn(IMUL)
                IntType()
            }
            is AndExp -> {
                genExpression(mw, e.left, methodScope, varMap)
                genExpression(mw, e.right, methodScope, varMap)
                mw.visitInsn(IAND)
                IntType()
            }
            is LessThanExp -> {
                genExpression(mw, e.left, methodScope, varMap)
                genExpression(mw, e.right, methodScope, varMap)
                mw.visitInsn(ISUB)
                val ltLabel = Label()
                val endLabel = Label()
                mw.visitJumpInsn(IFLT, ltLabel)
                mw.visitInsn(ICONST_0)
                mw.visitJumpInsn(GOTO, endLabel)
                mw.visitLabel(ltLabel)
                mw.visitInsn(ICONST_1)
                mw.visitLabel(endLabel)
                BoolType()
            }
            is NotExp -> {
                genExpression(mw, e.value, methodScope, varMap)
                val falseLabel = Label()
                val endLabel = Label()
                mw.visitJumpInsn(IFEQ, falseLabel)
                mw.visitInsn(ICONST_0)
                mw.visitJumpInsn(GOTO, endLabel)
                mw.visitLabel(falseLabel)
                mw.visitInsn(ICONST_1)
                mw.visitLabel(endLabel)
                BoolType()
            }
            is ArrayAccessExp -> {
                genExpression(mw, e.arr, methodScope, varMap)
                genExpression(mw, e.index, methodScope, varMap)
                mw.visitInsn(IALOAD)
                IntType()
            }
            is ArrayLengthExp -> {
                genExpression(mw, e.arr, methodScope, varMap)
                mw.visitInsn(ARRAYLENGTH)
                IntType()
            }
            is MethodCallExp -> {
                val t = genExpression(mw, e.obj, methodScope, varMap) as ClassType
                e.args.forEach { a -> genExpression(mw, a, methodScope, varMap) }
                val classScope = methodScope.ctx.ctx.classScopes[t.ident]!!
                val methodDecl = classScope.findMethod(e.methodName)!!
                val desc = genDescriptor(methodDecl)
                mw.visitMethodInsn(INVOKEVIRTUAL, CLASS_NAME_PREFIX + classScope.decl.ident,
                        e.methodName, desc, false)
                methodDecl.returnType
            }
            is ArrayAllocExp -> {
                genExpression(mw, e.size, methodScope, varMap)
                mw.visitIntInsn(NEWARRAY, T_INT)
                IntArrayType()
            }
            is ObjectAllocExp -> {
                val classScope = methodScope.ctx.ctx.classScopes[e.className]!!
                val classDecl = classScope.decl
                mw.visitTypeInsn(NEW, CLASS_NAME_PREFIX + classDecl.ident)
                mw.visitInsn(DUP)
                mw.visitMethodInsn(INVOKESPECIAL, CLASS_NAME_PREFIX + classDecl.ident,
                        "<init>", "()V", false)
                ClassType(classDecl.ident)
            }

            is ThisExp -> {
                mw.visitIntInsn(ALOAD, 0)
                ClassType(methodScope.ctx.decl.ident)
            }
            is TrueExp -> {
                mw.visitInsn(ICONST_1)
                BoolType()
            }
            is FalseExp -> {
                mw.visitInsn(ICONST_0)
                BoolType()
            }
            is IntLiteralExp -> {
                mw.visitLdcInsn(e.value)
                IntType()
            }
            is IdentExp -> {
                if (varMap.containsKey(e.ident)) {
                    // local
                    val t = methodScope.variables[e.ident]!!.type
                    when (t) {
                        is IntType, is BoolType -> mw.visitIntInsn(ILOAD, varMap[e.ident]!!)
                        else -> mw.visitIntInsn(ALOAD, varMap[e.ident]!!)
                    }
                    t
                } else {
                    // field
                    val t = methodScope.ctx.variables[e.ident]!!.type
                    mw.visitIntInsn(ALOAD, 0)
                    mw.visitFieldInsn(GETFIELD, CLASS_NAME_PREFIX + methodScope.ctx.decl.ident, e.ident, genDescriptor(t))
                    t
                }
            }
            else -> throw Exception("compile error")
        }
    }


    fun genDescriptor(t: Type): String {
        return when (t) {
            is IntArrayType -> "[I"
            is BoolType -> "Z"
            is IntType -> "I"
            is ClassType -> "L${CLASS_NAME_PREFIX + t.ident};"
            else -> throw Exception("compile error")
        }
    }

    fun genDescriptor(m: MethodDecl): String {
        var methodDescriptor = "("
        m.paramList.forEach { p ->
            methodDescriptor += genDescriptor(p.type)
        }
        methodDescriptor += ")" + genDescriptor(m.returnType)
        return methodDescriptor
    }

    fun genDefault(t: Type): Any? {
        return when (t) {
            is IntArrayType -> null
            is BoolType -> false
            is IntType -> 0
            is ClassType -> null
            else -> {
                throw Exception("compile error")
            }
        }
    }
}
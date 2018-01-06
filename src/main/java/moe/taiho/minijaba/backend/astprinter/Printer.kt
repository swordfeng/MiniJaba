package moe.taiho.minijaba.backend.astprinter

import moe.taiho.minijaba.ast.*

class Printer(var i: Int) {
    fun print(s: String) {
        println("  ".repeat(i) + s)
    }
    fun withIndent(action: () -> Unit) {
        i++
        action()
        i--
    }
    fun print(n: BaseDecl) {
        when (n) {
        /* declarations */
            is Goal -> {
                print("Goal")
                withIndent {
                    print("mainclass:")
                    withIndent {
                        print(n.mainClass)
                    }
                    print("classes:")
                    withIndent {
                        n.classes.forEach { c -> print(c) }
                    }
                }
            }
            is MainClassDecl -> {
                print("MainClassDecl")
                withIndent {
                    print("name: ${n.ident}")
                    print("arg: ${n.mainArg}")
                    print("statement:")
                    withIndent {
                        print(n.stmt)
                    }
                }
            }
            is ClassDecl -> {
                print("ClassDecl")
                withIndent {
                    print("name: ${n.ident}")
                    print("baseClass: ${n.baseClass}")
                    print("varList:")
                    withIndent {
                        n.varList.forEach { v -> print(v) }
                    }
                    print("methodList:")
                    withIndent {
                        n.methodList.forEach { m -> print(m) }
                    }
                }
            }
            is MethodDecl -> {
                print("MethodDecl")
                withIndent {
                    print("name: ${n.ident}")
                    print("returnType:")
                    withIndent { print(n.returnType) }
                    print("varList:")
                    withIndent {
                        n.varList.forEach { v -> print(v) }
                    }
                    print("statements:")
                    withIndent {
                        n.stmtList.forEach { s -> print(s) }
                    }
                }
            }
            is VarDecl -> {
                print("VarDecl")
                withIndent {
                    print("name: ${n.ident}")
                    print("type:")
                    withIndent { print(n.type) }
                }
            }

        /* statements */
            is BlockStmt -> {
                print("BlockStmt")
                withIndent {
                    print("statements:")
                    withIndent { n.stmtList.forEach { s -> print(s) } }
                }
            }
            is IfStmt -> {
                print("IfStmt")
                withIndent {
                    print("condition:")
                    withIndent { print(n.cond) }
                    print("trueStatement:")
                    withIndent { print(n.trueStmt) }
                    print("falseStatement:")
                    withIndent { print(n.falseStmt) }
                }
            }
            is WhileStmt -> {
                print("WhileStmt")
                withIndent {
                    print("condition:")
                    withIndent { print(n.cond) }
                    print("loopBody:")
                    withIndent { print(n.loopBody) }
                }
            }
            is AssignStmt -> {
                print("AssignStmt")
                withIndent {
                    print("varName: ${n.ident}")
                    print("value:")
                    withIndent { print(n.value) }
                }
            }
            is ArrayAssignStmt -> {
                print("ArrayAssignStmt")
                withIndent {
                    print("varName: ${n.ident}")
                    print("index:")
                    withIndent { print(n.index) }
                    print("value:")
                    withIndent { print(n.value) }
                }
            }
            is PrintlnStmt -> {
                print("PrintlnStmt")
                withIndent {
                    print("value:")
                    withIndent { print(n.exp) }
                }
            }

        /* types */
            is IntArrayType -> {
                print("IntArrayType")
            }
            is BoolType -> {
                print("BoolType")
            }
            is IntType -> {
                print("IntType")
            }
            is ClassType -> {
                print("ClassType")
                withIndent {
                    print("name: ${n.ident}")
                }
            }

        /* expressions */
            is BracketExp -> {
                print("BracketExp")
                withIndent {
                    print("value:")
                    withIndent { print(n.value) }
                }
            }
            is AddExp -> {
                print("AddExp")
                withIndent {
                    print("left:")
                    withIndent { print(n.left) }
                    print("right:")
                    withIndent { print(n.right) }
                }
            }
            is SubExp -> {
                print("SubExp")
                withIndent {
                    print("left:")
                    withIndent { print(n.left) }
                    print("right:")
                    withIndent { print(n.right) }
                }
            }
            is MulExp -> {
                print("MulExp")
                withIndent {
                    print("left:")
                    withIndent { print(n.left) }
                    print("right:")
                    withIndent { print(n.right) }
                }
            }
            is AndExp -> {
                print("AndExp")
                withIndent {
                    print("left:")
                    withIndent { print(n.left) }
                    print("right:")
                    withIndent { print(n.right) }
                }
            }
            is LessThanExp -> {
                print("LessThanExp")
                withIndent {
                    print("left:")
                    withIndent { print(n.left) }
                    print("right:")
                    withIndent { print(n.right) }
                }
            }
            is NotExp -> {
                print("NotExp")
                withIndent {
                    print("value:")
                    withIndent { print(n.value) }
                }
            }

            is ArrayAccessExp -> {
                print("ArrayAccessExp")
                withIndent {
                    print("array:")
                    withIndent { print(n.arr) }
                    print("index:")
                    withIndent { print(n.index) }
                }
            }
            is ArrayLengthExp -> {
                print("ArrayLengthExp")
                withIndent {
                    print("array:")
                    withIndent { print(n.arr) }
                }
            }
            is MethodCallExp -> {
                print("MethodCallExp")
                withIndent {
                    print("object:")
                    withIndent { print(n.obj) }
                    print("method: ${n.methodName}")
                    print("args:")
                    withIndent { n.args.forEach { a -> print(a) } }
                }
            }
            is ArrayAllocExp -> {
                print("ArrayAllocExp")
                withIndent {
                    print("size:")
                    withIndent { print(n.size) }
                }
            }
            is ObjectAllocExp -> {
                print("ArrayAllocExp")
                withIndent {
                    print("class: ${n.className}")
                }
            }

            is ThisExp -> {
                print("ThisExp")
            }
            is TrueExp -> {
                print("TrueExp")
            }
            is FalseExp -> {
                print("FalseExp")
            }
            is IntLiteralExp -> {
                print("IntLiteralExp")
                withIndent {
                    print("value: ${n.value}")
                }
            }
            is IdentExp -> {
                print("IdentExp")
                withIndent {
                    print("ident: ${n.ident}")
                }
            }

            else -> {
                print("**invalid**")
            }
        }
    }
}
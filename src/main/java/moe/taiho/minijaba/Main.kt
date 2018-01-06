package moe.taiho.minijaba

import moe.taiho.minijaba.backend.astprinter.Printer
import moe.taiho.minijaba.backend.interpreter.Interpreter
import java.io.*
import moe.taiho.minijaba.backend.bytecode.Codegen as JCodegen

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        var help: Boolean = false
        /*
        0: print
        1: bytecode
        2: llvm bitcode
        3: native
        4: interpreter
        */
        var target: Int = 0
        var input: String = ""
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-h", "--help" -> {
                    help = true
                }
                "-i", "--interpret" -> {
                    target = 4
                }
                "-j", "--bytecode" -> {
                    target = 1
                }
                "-l", "--bitcode" -> {
                    target = 2
                }
                "-n", "--native" -> {
                    target = 3
                }
                "-p", "--print" -> {
                    target = 0
                }
                else -> {
                    input = args[i]
                }
            }
            i++
        }
        if (help || input == "") {
            println("usage: compile [options] inputfile")
            println("  -h, --help      help message")
            println("targets:")
            println("  -i, --interpret Run in interpreter")
            println("  -j, --bytecode  JVM bytecode")
            println("  -l, --bitcode   LLVM bitcode")
            println("  -n, --native    native code")
            println("  -p, --print     print AST")
            return
        }

        val reader = BufferedReader(FileReader(input))
        val lexer = Lexer(reader)
        val parser = Parser(lexer)
        parser.parse()
        val goal = parser.result
        val ctx = Analyzer.GoalScope(goal)
        ctx.typeCheck()
        if (lexer.hasError || ctx.hasError) return

        when (target) {
            0 -> {
                val printer = Printer(0)
                printer.print(goal)
            }
            1 -> {
                val compiler = JCodegen(ctx)
                jcompilerWriteAll(compiler, "moe/taiho/minijaba/generated")
            }
            2 -> {
                println("LLVM is not supported in this build!")
            }
            3 -> {
                println("LLVM is not supported in this build!")
            }
            4 -> {
                val interp = Interpreter.Context(goal)
                interp.run()
            }
        }
    }

    private fun jcompilerWriteAll(compiler: JCodegen, path: String) {
        val goal = compiler.ctx.goal
        var m = goal.mainClass.ident
        var r = compiler.genMainClass()
        jcompilerWriteFile(r, "$path/$m.class")
        for (c in goal.classes) {
            m = c.ident
            r = compiler.genClass(compiler.ctx.classScopes[m]!!)
            jcompilerWriteFile(r, "$path/$m.class")
        }
    }

    private fun jcompilerWriteFile(data: ByteArray, path: String) {
        val file = File(path)
        file.parentFile.mkdirs()
        val s = BufferedOutputStream(FileOutputStream(file))
        s.write(data)
        s.flush()
        s.close()
    }
}
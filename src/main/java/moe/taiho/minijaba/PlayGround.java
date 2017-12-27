package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.ClassDecl;
import moe.taiho.minijaba.ast.Goal;
import moe.taiho.minijaba.backend.bytecode.Codegen;
import moe.taiho.minijaba.backend.interpreter.Interpreter;

import java.io.*;

import static org.bytedeco.javacpp.LLVM.LLVMModuleCreateWithName;
import static org.bytedeco.javacpp.LLVM.LLVMStructCreateNamed;

public class PlayGround {
    static public void main(String[] args) throws IOException {
        LLVMModuleCreateWithName("generated");
        /*
        Reader reader = new BufferedReader(new FileReader("a.java"));
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.parse();
        Goal goal = parser.getResult();
        Analyzer.GoalScope ctx = new Analyzer.GoalScope(goal);
        ctx.typeCheck();

        Codegen compiler = new Codegen(ctx);
        writeAll(compiler, "sampleout/moe/taiho/minijaba/generated");
        */
    }

    static void writeAll(Codegen compiler, String path) throws IOException {
        Goal goal = compiler.getCtx().getGoal();
        String m = goal.getMainClass().getIdent();
        byte[] r = compiler.genMainClass();
        writeFile(r, path + "/" + m + ".class");
        for (ClassDecl c : goal.getClasses()) {
            m = c.getIdent();
            r = compiler.genClass(compiler.getCtx().getClassScopes().get(m));
            writeFile(r, path + "/" + m + ".class");
        }
    }

    static void writeFile(byte[] data, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        OutputStream s = new BufferedOutputStream(new FileOutputStream(file));
        s.write(data);
        s.close();
    }
}


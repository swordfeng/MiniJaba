package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.Goal;
import moe.taiho.minijaba.backend.bytecode.Codegen;
import moe.taiho.minijaba.backend.interpreter.Interpreter;

import java.io.*;

public class PlayGround {
    static public void main(String[] args) throws IOException {
        Reader reader = new BufferedReader(new FileReader("samples/binarysearch.java"));
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.parse();
        Goal goal = parser.getResult();
        Analyzer.GoalScope ctx = new Analyzer.GoalScope(goal);
        ctx.typeCheck();

        Codegen compiler = new Codegen(ctx);

        

        byte[] result = compiler.genClass(ctx.getClassScopes().get("BS"));
        OutputStream s = new BufferedOutputStream(new FileOutputStream("t/moe/taiho/minijaba/generated/BS.class"));
        s.write(result);
        s.close();

        result = compiler.genMainClass();
        s = new BufferedOutputStream(new FileOutputStream("t/moe/taiho/minijaba/generated/BinarySearch.class"));
        s.write(result);
        s.close();
    }

    static void writeFile(byte[] data, String file) throws IOException {
        OutputStream s = new BufferedOutputStream(new FileOutputStream(file));
        s.write(data);
        s.close();
    }
}


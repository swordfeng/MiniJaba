package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.Goal;
import moe.taiho.minijaba.backend.llvm.Codegen;

import java.io.*;


public class PlayGround {
    static public void main(String[] args) throws IOException {
        Reader reader = new BufferedReader(new FileReader("samples/treevisitor.java"));
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.parse();
        Goal goal = parser.getResult();
        Analyzer.GoalScope ctx = new Analyzer.GoalScope(goal);
        ctx.typeCheck();

        Codegen compiler = new Codegen(ctx);
        compiler.gen();
    }
}


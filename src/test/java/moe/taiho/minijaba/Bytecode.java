package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.ClassDecl;
import moe.taiho.minijaba.ast.Goal;
import moe.taiho.minijaba.backend.bytecode.Codegen;
import moe.taiho.minijaba.backend.interpreter.Interpreter;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;

public class Bytecode {
    @Test
    public void BinarySearchCompile() throws IOException {
        runCompile("samples/binarysearch.java");
    }
    @Test
    public void BinaryTreeCompile() throws IOException {
        runCompile("samples/binarytree.java");
    }
    @Test
    public void BubbleSortCompile() throws IOException {
        runCompile("samples/bubblesort.java");
    }
    @Test
    public void FactorialCompile() throws IOException {
        runCompile("samples/factorial.java");
    }
    @Test
    public void LinearSearchCompile() throws IOException {
        runCompile("samples/linearsearch.java");
    }
    @Test
    public void LinkedListCompile() throws IOException {
        runCompile("samples/linkedlist.java");
    }
    @Test
    public void QuickSortCompile() throws IOException {
        runCompile("samples/quicksort.java");
    }
    @Test
    public void TreeVisitorCompile() throws IOException {
        runCompile("samples/treevisitor.java");
    }


    private void runCompile(String sourceFile) throws IOException {
        Reader reader = new BufferedReader(new FileReader(sourceFile));
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.parse();
        Goal goal = parser.getResult();
        Analyzer.GoalScope ctx = new Analyzer.GoalScope(goal);
        ctx.typeCheck();

        Codegen compiler = new Codegen(ctx);
        compilerWriteAll(compiler, "sampleout/moe/taiho/minijaba/generated");
    }

    private void compilerWriteAll(Codegen compiler, String path) throws IOException {
        Goal goal = compiler.getCtx().getGoal();
        String m = goal.getMainClass().getIdent();
        byte[] r = compiler.genMainClass();
        compilerWriteFile(r, path + "/" + m + ".class");
        for (ClassDecl c : goal.getClasses()) {
            m = c.getIdent();
            r = compiler.genClass(compiler.getCtx().getClassScopes().get(m));
            compilerWriteFile(r, path + "/" + m + ".class");
        }
    }

    private void compilerWriteFile(byte[] data, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();
        OutputStream s = new BufferedOutputStream(new FileOutputStream(file));
        s.write(data);
        s.flush();
        s.close();
    }
}

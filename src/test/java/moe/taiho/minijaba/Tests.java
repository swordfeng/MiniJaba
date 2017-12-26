package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.Goal;
import moe.taiho.minijaba.backend.interpreter.Interpreter;
import org.junit.Test;

import java.io.*;

public class Tests {
    @Test
    public void BinarySearchIntepreter() throws IOException {
        runIntepreter("samples/binarysearch.java");
    }
    @Test
    public void BinaryTreeIntepreter() throws IOException {
        runIntepreter("samples/binarytree.java");
    }
    @Test
    public void BubbleSortIntepreter() throws IOException {
        runIntepreter("samples/bubblesort.java");
    }
    @Test
    public void FactorialIntepreter() throws IOException {
        runIntepreter("samples/factorial.java");
    }
    @Test
    public void LinearSearchIntepreter() throws IOException {
        runIntepreter("samples/linearsearch.java");
    }
    @Test
    public void LinkedListIntepreter() throws IOException {
        runIntepreter("samples/linkedlist.java");
    }
    @Test
    public void QuickSortIntepreter() throws IOException {
        runIntepreter("samples/quicksort.java");
    }
    @Test
    public void TreeVisitorIntepreter() throws IOException {
        runIntepreter("samples/treevisitor.java");
    }

    private void runIntepreter(String sourceFile) throws IOException {
        Reader reader = new BufferedReader(new FileReader(sourceFile));
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.parse();
        Goal goal = parser.getResult();
        Interpreter.Context ctx = new Interpreter.Context(goal);
        ctx.run();
    }
}

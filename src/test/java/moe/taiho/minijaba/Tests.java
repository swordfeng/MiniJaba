package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.Goal;
import moe.taiho.minijaba.backend.interpreter.Interpreter;
import org.junit.Assert;
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

    @Test
    public void BinarySearchChecker() throws IOException {
        runChecker("samples/binarysearch.java");
    }
    @Test
    public void BinaryTreeChecker() throws IOException {
        runChecker("samples/binarytree.java");
    }
    @Test
    public void BubbleSortChecker() throws IOException {
        runChecker("samples/bubblesort.java");
    }
    @Test
    public void FactorialChecker() throws IOException {
        runChecker("samples/factorial.java");
    }
    @Test
    public void LinearSearchChecker() throws IOException {
        runChecker("samples/linearsearch.java");
    }
    @Test
    public void LinkedListChecker() throws IOException {
        runChecker("samples/linkedlist.java");
    }
    @Test
    public void QuickSortChecker() throws IOException {
        runChecker("samples/quicksort.java");
    }
    @Test
    public void TreeVisitorChecker() throws IOException {
        runChecker("samples/treevisitor.java");
    }

    private void runChecker(String sourceFile) throws IOException {
        Reader reader = new BufferedReader(new FileReader(sourceFile));
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.parse();
        Goal goal = parser.getResult();
        Analyzer.GoalScope ctx = new Analyzer.GoalScope(goal);
        ctx.typeCheck();
        Assert.assertFalse(ctx.getHaserror());
    }

    @Test
    public void Errors() throws IOException {
        Reader reader = new BufferedReader(new FileReader("samples/errors.java"));
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.parse();
        Goal goal = parser.getResult();
        Analyzer.GoalScope ctx = new Analyzer.GoalScope(goal);
        ctx.typeCheck();
        Assert.assertTrue(ctx.getHaserror());
    }
}

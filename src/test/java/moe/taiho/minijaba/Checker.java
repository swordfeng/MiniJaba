package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.Goal;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;

public class Checker {
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
        Assert.assertFalse(ctx.getHasError());
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
        Assert.assertTrue(ctx.getHasError());
    }
}

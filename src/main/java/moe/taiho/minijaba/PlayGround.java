package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.Goal;
import moe.taiho.minijaba.backend.interpreter.Interpreter;

import java.io.*;

public class PlayGround {
    static public void main(String[] args) throws IOException {
        A a = new B();
        System.out.println(a.getY());
    }
}

class A {
    int x = 5;
    int getX() { return x; }
    int getY() { return getX(); }
}

class B extends A {
    int x = 7;
    int getY() { return getX(); }
}
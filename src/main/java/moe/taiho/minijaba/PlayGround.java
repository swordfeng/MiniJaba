package moe.taiho.minijaba;

import moe.taiho.minijaba.ast.Goal;
import moe.taiho.minijaba.backend.Interpreter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class PlayGround {
    static public void main(String[] args) throws IOException {
        Reader reader = new BufferedReader(new FileReader("samples/binarysearch.java"));
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.parse();
        Goal goal = parser.getResult();
        Interpreter.Context ctx = new Interpreter.Context(goal);
        ctx.run();
    }
}

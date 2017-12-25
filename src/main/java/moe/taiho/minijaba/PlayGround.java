package moe.taiho.minijaba;

import java.io.*;

public class PlayGround {
    static public void main(String[] args) throws IOException {
        String code =
                "class Factorial{\n" +
                "    public static void main(String[] a){\n" +
                "\tSystem.out.println(new Fac().ComputeFac(10));\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "class Fac {\n" +
                "\n" +
                "    public int ComputeFac(int num){\n" +
                "\tint num_aux ;\n" +
                "\tif (num < 1)\n" +
                "\t    num_aux = 1 ;\n" +
                "\telse \n" +
                "\t    num_aux = num * (this.ComputeFac(num-1)) ;\n" +
                "\treturn num_aux ;\n" +
                "    }\n" +
                "\n" +
                "}\n";
        Reader reader = new StringReader(code);
        Lexer lexer = new Lexer(reader);
        Parser parser = new Parser(lexer);
        parser.setDebugLevel(10);;
        parser.parse();
    }
}

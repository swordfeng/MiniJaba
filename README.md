MiniJaba
========
MiniJaba is my course project for COMP130014.02 Compilers @ Fudan Univ.

MiniJaba is an implementation for [MiniJava](http://www.cambridge.org/us/features/052182060X/).

The project uses [JFlex](http://jflex.de/) and [Bison](https://www.gnu.org/software/bison/) to generate the parser,
and [ASM](http://asm.ow2.org/) / [javacpp-llvm](https://github.com/bytedeco/javacpp-presets/tree/master/llvm) to
generate JVM Bytecode / LLVM IR or native code.

Prerequisites
-------------
* JDK 8+
* JFlex (optional)
* Bison (optional)

Build
-----
Generate lexer: (Optional)
`./gradlew jflex`
Generate parser: (Optional)
`./gradlew bison`
Build:
`./gradlew build`
Generate fat jar:
`./gradlew shadowJar`
If you don't want to or cannot build the LLVM backend, please checkout the `nollvm` branch.

Run
---
Print AST:
`java -jar build/libs/MiniJaba-all.jar -p samples/binarysearch.java`
Compile to bytecode:
`java -jar build/libs/MiniJaba-all.jar -j samples/binarysearch.java`
Run bytecode:
`java moe.taiho.minijaba.generated.BinarySearch`
Compile to bitcode:
`java -jar build/libs/MiniJaba-all.jar -l samples/binarysearch.java`
Run bitcode:
`lli BinarySearch.bc`
Compile to native code:
`java -jar build/libs/MiniJaba-all.jar -n samples/binarysearch.java`
Link native code:
`gcc -static BinarySearch.o -o BinarySearch`
Run native code:
`./BinarySearch`


%language "Java"
%define package {moe.taiho.minijaba}
%define parser_class_name {Parser}
%define public
%locations

%code imports {
    import java.util.ArrayList;
    import moe.taiho.minijaba.ast.*;
}

%code {
    private Goal result = null;
    public Goal getResult() {
        return result;
    }
}

%token K_CLASS "class"
%token K_PUBLIC "public"
%token K_STATIC "static"
%token K_VOID "void"
%token K_MAIN "main"
%token K_STRING "String"
%token K_EXTENDS "extends"
%token K_RETURN "return"
%token K_INT "int"
%token K_BOOLEAN "boolean"
%token K_IF "if"
%token K_ELSE "else"
%token K_WHILE "while"
%token K_PRINTLN
%token K_LENGTH "length"
%token K_TRUE "true"
%token K_FALSE "false"
%token K_THIS "this"
%token K_NEW "new"

%token S_LBRACE "{"
%token S_RBRACE "}"
%token S_LBRACKET "("
%token S_RBRACKET ")"
%token S_LSBRACKET "["
%token S_RSBRACKET "]"
%token S_SEMICOLON ";"
%token S_COMMA ","
%token S_DOT "."

%token O_ASSIGN "="
%token O_AND "&&"
%token O_LT "<"
%token O_ADD "+"
%token O_SUB "-"
%token O_MUL "*"
%token O_NOT "!"

%token <int> INTEGER_LITERAL
%token <String> IDENTIFIER

%token UNEXPECTED

%left "&&"
%left "<"
%left "+" "-"
%left "*"
%right "!"
%precedence "[" "."


%type <Goal> goal
%type <ArrayList<ClassDecl>> classes
%type <MainClassDecl> main_class
%type <ClassDecl> class_declaration
%type <String> extends
%type <ArrayList<VarDecl>> vars
%type <ArrayList<MethodDecl>> methods
%type <VarDecl> var_declaration
%type <MethodDecl> method_declaration
%type <ArrayList<VarDecl>> params
%type <ArrayList<VarDecl>> params_nonempty
%type <VarDecl> param_declaration
%type <ArrayList<Stmt>> statements
%type <ArrayList<Stmt>> statements_nonempty
%type <Type> type
%type <Stmt> statement
%type <Exp> expression
%type <ArrayList<Exp>> args
%type <ArrayList<Exp>> args_nonempty

%%

goal:
  main_class classes { result = new Goal($1, $2); $$ = result; return YYACCEPT; }
;

classes:
  %empty { $$ = new ArrayList<ClassDecl>(); }
| classes class_declaration { $1.add($2); $$ = $1; }
;

main_class:
  "class" IDENTIFIER "{" "public" "static" "void" "main" "(" "String" "[" "]" IDENTIFIER ")" "{" statement "}" "}" { $$ = new MainClassDecl($2, $15, $12); }
;

class_declaration:
  "class" IDENTIFIER extends "{" vars methods "}" { $$= new ClassDecl($2, $3, $5, $6); }
;

extends:
  %empty { $$ = null; }
| "extends" IDENTIFIER { $$ = $2; }
;

vars:
  %empty { $$ = new ArrayList<VarDecl>(); }
| vars var_declaration { $1.add($2); $$ = $1; }
;

methods:
  %empty { $$ = new ArrayList<MethodDecl>(); }
| methods method_declaration { $1.add($2); $$ = $1; }
;

var_declaration:
  type IDENTIFIER ";" { $$ = new VarDecl($2, $1); }
;

method_declaration:
  "public" type IDENTIFIER "(" params ")" "{" vars statements "return" expression ";" "}" { $$ = new MethodDecl($3, $2, $5, $8, $9, $11); }
;

params:
  %empty { $$ = new ArrayList<VarDecl>(); }
| params_nonempty { $$ = $1; }
;

params_nonempty:
  param_declaration { ArrayList<VarDecl> l = new ArrayList<>(); l.add($1); $$ = l; }
| params_nonempty "," param_declaration { $1.add($3); $$ = $1; }
;

param_declaration:
  type IDENTIFIER { $$ = new VarDecl($2, $1); }
;

statements:
  %empty { $$ = new ArrayList<Stmt>(); }
| statements_nonempty { $$ = $1; }
;

statements_nonempty:
  statement { ArrayList<Stmt> l = new ArrayList<>(); l.add($1); $$ = l; }
| statements_nonempty statement { $1.add($2); $$ = $1; }
;

type:
  "int" "[" "]" { $$ = new IntArrayType(); }
| "boolean" { $$ = new BoolType(); }
| "int" { $$ = new IntType(); }
| IDENTIFIER { $$ = new ClassType($1); }
;

statement:
  "{" statements "}" { $$ = new BlockStmt($2); }
| "if" "(" expression ")" statement "else" statement { $$ = new IfStmt($3, $5, $7); }
| "while" "(" expression ")" statement { $$ = new WhileStmt($3, $5); }
| K_PRINTLN "(" expression ")" ";" { $$ = new PrintlnStmt($3); }
| IDENTIFIER "=" expression ";" { $$ = new AssignStmt($1, $3); }
| IDENTIFIER "[" expression "]" "=" expression ";" { $$ = new ArrayAssignStmt($1, $3, $6); }
;

expression:
  expression "&&" expression { $$ = new AndExp($1, $3); }
| expression "<" expression { $$ = new LessThanExp($1, $3); }
| expression "+" expression { $$ = new AddExp($1, $3); }
| expression "-" expression { $$ = new SubExp($1, $3); }
| expression "*" expression { $$ = new MulExp($1, $3); }
| expression "[" expression "]" { $$ = new ArrayAccessExp($1, $3); }
| expression "." "length" { $$ = new ArrayLengthExp($1); }
| expression "." IDENTIFIER "(" args ")" { $$ = new MethodCallExp($1, $3, $5); }
| INTEGER_LITERAL { $$ = new IntLiteralExp($1); }
| "true" { $$ = new TrueExp(); }
| "false" { $$ = new FalseExp(); }
| IDENTIFIER { $$ = new IdentExp($1); }
| "this" { $$ = new ThisExp(); }
| "new" "int" "[" expression "]" { $$ = new ArrayAllocExp($4); }
| "new" IDENTIFIER "(" ")" { $$ = new ObjectAllocExp($2); }
| "!" expression { $$ = new NotExp($2); }
| "(" expression ")" { $$ = new BracketExp($2); }
;

args:
  %empty { $$ = new ArrayList<Exp>(); }
| args_nonempty { $$ = $1; }
;

args_nonempty:
  expression { ArrayList<Exp> l = new ArrayList<Exp>(); l.add($1); $$ = l; }
| args_nonempty "," expression { $1.add($3); $$ = $1; }
;

%%

class Position {
    public int line;
    public int column;
    public int charpos;
    Position(int line, int column, int charpos) {
        this.line = line;
        this.column = column;
        this.charpos = charpos;
    }
    @Override
    public String toString() {
        return line + ":" + column + "(" + charpos + ")";
    }
    @Override
    public boolean equals(Object rhs) {
        if (!(rhs instanceof Position)) {
            return false;
        }
        Position p = (Position) rhs;
        return charpos == p.charpos;
    }
}
%language "Java"
%define package {moe.taiho.minijaba}
%define parser_class_name {Parser}

%code imports {
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

%token INTEGER_LITERAL
%token IDENTIFIER

%left "&&"
%left "<"
%left "+" "-"
%left "*"
%right "!"
%precedence "[" "."

%%

goal:
  main_class classes
;

classes:
  %empty
| classes class_declaration
;

main_class:
  "class" IDENTIFIER "{" "public" "static" "void" "main" "(" "String" "[" "]" IDENTIFIER ")" "{" statement "}" "}"
;

class_declaration:
  "class" IDENTIFIER extends "{" vars methods "}"
;

extends:
  %empty
| "extends" IDENTIFIER
;

vars:
  %empty
| vars var_declaration
;

methods:
  %empty
| methods method_declaration
;

var_declaration:
  type IDENTIFIER ";"
;

method_declaration:
  "public" type IDENTIFIER "(" params ")" "{" vars statements "return" expression ";" "}"
;

params:
  %empty
| params_nonempty
;

params_nonempty:
  param_declaration
| params_nonempty "," param_declaration
;

param_declaration:
  type IDENTIFIER
;

statements:
  %empty
| statements statement
;

type:
  "int" "[" "]"
| "boolean"
| "int"
| IDENTIFIER
;

statement:
  "{" statements "}"
| "if" "(" expression ")" statement "else" statement
| "while" "(" expression ")" statement
| K_PRINTLN "(" expression ")" ";"
| IDENTIFIER "=" expression ";"
| IDENTIFIER "[" expression "]" "=" expression ";"
;

expression:
  expression "&&" expression
| expression "<" expression
| expression "+" expression
| expression "-" expression
| expression "*" expression
| expression "[" expression "]"
| expression "." "length"
| expression "." IDENTIFIER "(" args ")"
| INTEGER_LITERAL
| "true"
| "false"
| IDENTIFIER
| "this"
| "new" "int" "[" expression "]"
| "new" IDENTIFIER "(" ")"
| "!" expression
| "(" expression ")"
;

args:
  %empty
| args_nonempty
;

args_nonempty:
  expression
| args_nonempty "," expression
;

%%

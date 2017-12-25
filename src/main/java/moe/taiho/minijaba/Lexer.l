package moe.taiho.minijaba;

import moe.taiho.minijaba.Parser;

%%

%class Lexer
%implements Parser.Lexer

%int

%unicode
%line
%column

%{
    private Object yylval;
    public Object getLVal() {
        return yylval;
    }
    public void yyerror(String msg) {
        System.out.println(msg);
    }
%}


EOL = \r|\n|\r\n
INPUT_CHARACTERS = [^\r\n]
WHITE_SPACE = {EOL} | [ \t\f]


COMMENT = "/*" ~ "*/" | "//" {INPUT_CHARACTERS}* {EOL}?

INTEGER_LITERAL = 0 | [1-9][0-9]*
IDENTIFIER = [A-Za-z_][A-Za-z0-9_]*

%%

/* keywords */
"class" { return K_CLASS; }
"public" { return K_PUBLIC; }
"static" { return K_STATIC; }
"void" { return K_VOID; }
"main" { return K_MAIN; }
"String" { return K_STRING; }
"extends" { return K_EXTENDS; }
"return" { return K_RETURN; }
"int" { return K_INT; }
"boolean" { return K_BOOLEAN; }
"if" { return K_IF; }
"else" { return K_ELSE; }
"while" { return K_WHILE; }
"System.out.println" { return K_PRINTLN; }
"length" { return K_LENGTH; }
"true" { return K_TRUE; }
"false" { return K_FALSE; }
"this" { return K_THIS; }
"new" { return K_NEW; }
/* separators */
"{" { return S_LBRACE; }
"}" { return S_RBRACE; }
"(" { return S_LBRACKET; }
")" { return S_RBRACKET; }
"[" { return S_LSBRACKET; }
"]" { return S_RSBRACKET; }
";" { return S_SEMICOLON; }
"," { return S_COMMA; }
"." { return S_DOT; }
/* operators */
"=" { return O_ASSIGN; }
"&&" { return O_AND; }
"<" { return O_LT; }
"+" { return O_ADD; }
"-" { return O_SUB; }
"*" { return O_MUL; }
"!" { return O_NOT; }

{INTEGER_LITERAL} { yylval = Integer.valueOf(yytext()); return INTEGER_LITERAL; }
{IDENTIFIER} { yylval = yytext(); return IDENTIFIER; }

{WHITE_SPACE} {}
{COMMENT} {}

<<EOF>> { return EOF; }

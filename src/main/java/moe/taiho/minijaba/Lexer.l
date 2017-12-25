package moe.taiho.minijaba;

import moe.taiho.minijaba.Parser;

%%

%class Lexer
%implements Parser.Lexer

%int

%unicode
%char
%line
%column

%{
    private Object yylval;
    public Object getLVal() {
        return yylval;
    }

    private int lline = 0;
    private int lcolumn = 0;
    private int lchar = 0;
    public Position getStartPos() {
        return new Position(lline, lcolumn, lchar);
    }
    public Position getEndPos() {
        return new Position(yyline, yycolumn, yychar);
    }
    private void updateLastPos() {
        lline = yyline;
        lcolumn = yycolumn;
        lchar = yychar;
    }

    public void yyerror(Parser.Location location, String msg) {
        System.err.println("Error@" +
            location.begin.toString() +
            (location.begin.equals(location.end) ? "" : "-" + location.end.toString())
            + ": " + msg);
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
"class" { updateLastPos(); return K_CLASS; }
"public" { updateLastPos(); return K_PUBLIC; }
"static" { updateLastPos(); return K_STATIC; }
"void" { updateLastPos(); return K_VOID; }
"main" { updateLastPos(); return K_MAIN; }
"String" { updateLastPos(); return K_STRING; }
"extends" { updateLastPos(); return K_EXTENDS; }
"return" { updateLastPos(); return K_RETURN; }
"int" { updateLastPos(); return K_INT; }
"boolean" { updateLastPos(); return K_BOOLEAN; }
"if" { updateLastPos(); return K_IF; }
"else" { updateLastPos(); return K_ELSE; }
"while" { updateLastPos(); return K_WHILE; }
"System.out.println" { updateLastPos(); return K_PRINTLN; }
"length" { updateLastPos(); return K_LENGTH; }
"true" { updateLastPos(); return K_TRUE; }
"false" { updateLastPos(); return K_FALSE; }
"this" { updateLastPos(); return K_THIS; }
"new" { updateLastPos(); return K_NEW; }
/* separators */
"{" { updateLastPos(); return S_LBRACE; }
"}" { updateLastPos(); return S_RBRACE; }
"(" { updateLastPos(); return S_LBRACKET; }
")" { updateLastPos(); return S_RBRACKET; }
"[" { updateLastPos(); return S_LSBRACKET; }
"]" { updateLastPos(); return S_RSBRACKET; }
";" { updateLastPos(); return S_SEMICOLON; }
"," { updateLastPos(); return S_COMMA; }
"." { updateLastPos(); return S_DOT; }
/* operators */
"=" { updateLastPos(); return O_ASSIGN; }
"&&" { updateLastPos(); return O_AND; }
"<" { updateLastPos(); return O_LT; }
"+" { updateLastPos(); return O_ADD; }
"-" { updateLastPos(); return O_SUB; }
"*" { updateLastPos(); return O_MUL; }
"!" { updateLastPos(); return O_NOT; }

{INTEGER_LITERAL} { yylval = Integer.valueOf(yytext()); updateLastPos(); return INTEGER_LITERAL; }
{IDENTIFIER} { yylval = yytext(); updateLastPos(); return IDENTIFIER; }

{WHITE_SPACE} {}
{COMMENT} {}

<<EOF>> { updateLastPos(); return EOF; }

[^] { updateLastPos(); return UNEXPECTED; }
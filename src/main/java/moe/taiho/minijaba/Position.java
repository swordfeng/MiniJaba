package moe.taiho.minijaba;

public class Position {
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

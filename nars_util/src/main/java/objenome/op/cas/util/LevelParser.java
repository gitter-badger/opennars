package objenome.op.cas.util;

import java.text.ParseException;

public interface LevelParser {
    
    public Token<Object> parseLevel(Token[] delims, TokenList<Object> tokens) throws ParseException;
    
}

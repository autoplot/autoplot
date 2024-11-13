
package org.das2.jythoncompletion;

import java.awt.Graphics2D;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.das2.datum.LoggerManager;
import org.python.parser.*;

/**
 * support functions for Jython editor completions.
 * @author jbf
 */
public class CompletionSupport {
    
    private static final Logger logger= LoggerManager.getLogger("jython.editor.completion");
    
    private CompletionSupport() {
        // utility class cannot be instanciated.
    }
    
    /**
     * 
     * @param tokens
     * @param offset first token
     * @param end last token index, exclusive
     * @return
     */
    private static String join( List<Token> tokens, int offset, int end ) {
        StringBuilder result= new StringBuilder( tokens.get(offset).image );
        for ( int i=1; i<(end-offset); i++ ) {
            result.append( tokens.get(offset+i).image );
        }
        return result.toString();
    }
    
    /**
     * return true if this line is a continuation of the previous line.
     * @param possible the previous line
     * @param tail the line which we are to test if it is a continuation.
     * @return true if this line is a continuation of the previous line.
     */
    private static boolean isContinuation( String possible, String tail ) {
        if ( possible.trim().endsWith("\\") ) {
            return true;
        } else {
            int ipos1= 0;
            while ( ipos1<possible.length() && Character.isWhitespace( possible.charAt(ipos1) ) ) ipos1++;
            if ( possible.trim().length()==0 ) {
                return false;
            }
            int ipos2= 0;
            while ( ipos2<tail.length() && Character.isWhitespace( tail.charAt(ipos2) ) ) ipos2++;
            return ipos1<ipos2;
        }
    }
    
    /**
     * is the carot within a subclass of an identifiable Java class?
     * TODO: this is a proof-of-concept kludge right now, where it just looks for g.*
     * @param editor
     * @return null or the CompletionContext for this.
     * @throws javax.swing.text.BadLocationException
     */
    public static CompletionContext checkJavaSubClass( JTextComponent editor ) throws BadLocationException {
        // get the AST, check that we are in a routine which is a subclass (e.g. Painter), and get the Java types from it.
        int pos= editor.getCaretPosition();
        int i0= Utilities.getRowStart( editor, pos );
        //int i2= Utilities.getRowEnd( editor, pos );
        
        String line= editor.getText( i0, pos-i0 );
        Pattern p= Pattern.compile("\\s*(g)\\.([a-zA-Z]*)");
        Matcher m= p.matcher(line);
        if ( m.matches() ) {
            CompletionContext result= new CompletionContext( CompletionContext.CLASS_METHOD_NAME, m.group(1), m.group(2) );
            result.setContextObjectClass( Graphics2D.class );
            return result;
        }
        
        return null;
    }
    
    /**
     * get the completion context for the editor at the carot position.
     * @param editor the editor component containing the script and the carot position.
     * @return the completion context
     * @throws BadLocationException 
     */
    public static CompletionContext getCompletionContext( JTextComponent editor ) throws BadLocationException {
        int pos= editor.getCaretPosition();
        int i0= Utilities.getRowStart( editor, pos );
        int i2= Utilities.getRowEnd( editor, pos );
        
        String line= editor.getText( i0, i2-i0-1 );
        int i1= i0;
        
        if ( i1==i2 ) return new CompletionContext( CompletionContext.DEFAULT_NAME, null, "" );
        
        CompletionContext result;
        
        result= checkJavaSubClass( editor );
        if ( result!=null ) {
            return result;
        }
               
        if ( i0>0 ) {
            int im1= Utilities.getRowStart( editor, i0-1 );
            String prevLine= editor.getText( im1, i0-im1-1 );
            if ( isContinuation( prevLine, line ) ) { // rfe363: what about second continuation line? 
                logger.finer("carot line is continuation, joining with previous line.");
                do {                    
                    im1= Utilities.getRowStart( editor, i0-1 );
                    String prevLine1= prevLine.trim();
                    if ( prevLine1.endsWith("\\") ) {
                        prevLine1= prevLine1.substring(0,prevLine1.length()-1);
                    }
                    line= prevLine1 + " " + line; // space is because the newline was removed.
                    pos= pos - ( prevLine.length() - prevLine1.length() );
                    int lastLineStart= Utilities.getRowStart( editor, im1-1 );
                    prevLine= editor.getText( lastLineStart, im1-lastLineStart );
                    if ( isContinuation( prevLine, line ) ) {
                        i0= im1;
                    }
                } while ( isContinuation( prevLine, line ) );
                i0= im1;
            }
        }
        
        pos= pos - i0;
        //i2= i2- i0;
	i2= pos;
        i1= i1- i0;
        i0= 0;
        
        result= getCompletionContext( line, pos, i0, i1, i2 );
        
        logger.log(Level.FINE, "CompletionContext: {0}", result);
        return result;
    }
    
    /**
     * finish off strings so python can parse.  return python tokens
     * so that the line is parsable, possibly trimming off code after carot.
     * @param line
     * @return
     */
    private static String preProcess( String line, int pos ) {
        char squote= '\'';
        int i;
        String[] ss= line.substring(0,pos).split("'",-2);
        boolean inQuote= ss.length % 2 == 0;
        if ( inQuote ) {
            i= line.indexOf(squote,pos);
            if ( i==-1 ) {
                return line.substring(0,pos) + squote;
            } else {
                return line.substring(0,i+1);
            }
        } else {
            squote= '\"';
            ss= line.substring(0,pos).split("\"",-2);
            inQuote= ss.length % 2 == 0;
            if ( inQuote ) {
                i= line.indexOf(squote,pos);
                if ( i==-1 ) {
                    return line.substring(0,pos) + squote;
                } else {
                    return line.substring(0,i+1);
                }
            } else {
                return line.substring(0,pos);
            }
        }
    }

    private static String exprBeforeDot( List<Token> tokens, int pos ) {
            String contextString= tokens.get(pos-1).image;

            int i= pos-1;

            if ( i>1 && ( tokens.get(pos-1).kind==PythonGrammarConstants.RPAREN ||tokens.get(pos-1).kind==PythonGrammarConstants.RBRACKET ) ) {
                int rparCount= tokens.get(pos-1).kind==PythonGrammarConstants.RPAREN ? 1 : 0;
                int rbackCount= tokens.get(pos-1).kind==PythonGrammarConstants.RBRACKET ? 1 : 0;
                int lpar= i-1;
                while ( lpar>0 && ( rparCount>0 || rbackCount>0 ) ) {
                    contextString= tokens.get(lpar).image + contextString;
                    switch (tokens.get(lpar).kind) {
                        case PythonGrammarConstants.LPAREN:
                            rparCount--;
                            break;
                        case PythonGrammarConstants.RPAREN:
                            rparCount++;
                            break;
                        case PythonGrammarConstants.LBRACKET:
                            rbackCount--;
                            break;
                        case PythonGrammarConstants.RBRACKET:
                            rbackCount++;
                            break;
                        default:
                            break;
                    }
                    if ( rparCount==0 ) {
                        if ( lpar>0 && tokens.get(lpar-1).kind==PythonGrammarConstants.NAME ) {
                            contextString= tokens.get(lpar-1).image + contextString;
                            if ( lpar>1 && tokens.get(lpar-2).kind==PythonGrammarConstants.DOT ) { // recurse to find the expr before that
                                String before= exprBeforeDot( tokens, lpar-2 );
                                return before + "." + contextString;
                            } else {
                                return contextString;
                            }
                        }
                    }
                    lpar--;
                }
            }

            boolean notdone;
            notdone= i>1 && tokens.get(i-1).kind==PythonGrammarConstants.DOT;
            while ( notdone ) {
                if ( tokens.get(i-2).kind==PythonGrammarConstants.RBRACKET
                        && i>=5
                        && tokens.get(i-4).kind==PythonGrammarConstants.LBRACKET
                        && tokens.get(i-5).kind==PythonGrammarConstants.NAME ) {
                    contextString= tokens.get(i-5).image + tokens.get(i-4).image + tokens.get(i-3).image + tokens.get(i-2).image + tokens.get(i-1).image + contextString;
                    i=i-5;
                } else if ( tokens.get(i-2).kind==PythonGrammarConstants.RPAREN && i>=4 && tokens.get(i-3).kind==PythonGrammarConstants.LPAREN ) {
                    contextString= tokens.get(i-4).image + tokens.get(i-3).image + tokens.get(i-2).image + tokens.get(i-1).image + contextString;
                    i=i-4;
                } else if ( tokens.get(i-2).kind==PythonGrammarConstants.RPAREN 
                        && i>=5 
                        && tokens.get(i-4).kind==PythonGrammarConstants.LPAREN 
                        && tokens.get(i-5).kind==PythonGrammarConstants.NAME ) {
                    contextString=  tokens.get(i-5).image + tokens.get(i-4).image + tokens.get(i-3).image + tokens.get(i-2).image + tokens.get(i-1).image + contextString;
                    i=i-5;
                } else {
                    contextString = tokens.get(i-2).image + tokens.get(i-1).image + contextString;
                    i=i-2;
                }
                notdone= i>1 && tokens.get(i-1).kind==PythonGrammarConstants.DOT;
            }
            return contextString;
    }

    private static Map<Integer,String> grammarConstantLookup;
    
    static {
        // hacked code from org/python/parser/PythonGrammarConstants.java
          Map<String,Integer> m= new HashMap<>();
          m.put( "EOF", 0);
          m.put( "SPACE", 1);
          m.put( "CONTINUATION", 4);
          m.put( "NEWLINE1", 5);
          m.put( "NEWLINE", 6);
          m.put( "NEWLINE2", 7);
          m.put( "CRLF1", 12);
          m.put( "DEDENT", 14);
          m.put( "INDENT", 15);
          m.put( "TRAILING_COMMENT", 16);
          m.put( "SINGLE_LINE_COMMENT", 17);
          m.put( "LPAREN", 18);
          m.put( "RPAREN", 19);
          m.put( "LBRACE", 20);
          m.put( "RBRACE", 21);
          m.put( "LBRACKET", 22);
          m.put( "RBRACKET", 23);
          m.put( "SEMICOLON", 24);
          m.put( "COMMA", 25);
          m.put( "DOT", 26);
          m.put( "COLON", 27);
          m.put( "PLUS", 28);
          m.put( "MINUS", 29);
          m.put( "MULTIPLY", 30);
          m.put( "DIVIDE", 31);
          m.put( "FLOORDIVIDE", 32);
          m.put( "POWER", 33);
          m.put( "LSHIFT", 34);
          m.put( "RSHIFT", 35);
          m.put( "MODULO", 36);
          m.put( "NOT", 37);
          m.put( "XOR", 38);
          m.put( "OR", 39);
          m.put( "AND", 40);
          m.put( "EQUAL", 41);
          m.put( "GREATER", 42);
          m.put( "LESS", 43);
          m.put( "EQEQUAL", 44);
          m.put( "EQLESS", 45);
          m.put( "EQGREATER", 46);
          m.put( "LESSGREATER", 47);
          m.put( "NOTEQUAL", 48);
          m.put( "PLUSEQ", 49);
          m.put( "MINUSEQ", 50);
          m.put( "MULTIPLYEQ", 51);
          m.put( "DIVIDEEQ", 52);
          m.put( "FLOORDIVIDEEQ", 53);
          m.put( "MODULOEQ", 54);
          m.put( "ANDEQ", 55);
          m.put( "OREQ", 56);
          m.put( "XOREQ", 57);
          m.put( "LSHIFTEQ", 58);
          m.put( "RSHIFTEQ", 59);
          m.put( "POWEREQ", 60);
          m.put( "OR_BOOL", 61);
          m.put( "AND_BOOL", 62);
          m.put( "NOT_BOOL", 63);
          m.put( "IS", 64);
          m.put( "IN", 65);
          m.put( "LAMBDA", 66);
          m.put( "IF", 67);
          m.put( "ELSE", 68);
          m.put( "ELIF", 69);
          m.put( "WHILE", 70);
          m.put( "FOR", 71);
          m.put( "TRY", 72);
          m.put( "EXCEPT", 73);
          m.put( "DEF", 74);
          m.put( "CLASS", 75);
          m.put( "FINALLY", 76);
          m.put( "PRINT", 77);
          m.put( "PASS", 78);
          m.put( "BREAK", 79);
          m.put( "CONTINUE", 80);
          m.put( "RETURN", 81);
          m.put( "YIELD", 82);
          m.put( "IMPORT", 83);
          m.put( "FROM", 84);
          m.put( "DEL", 85);
          m.put( "RAISE", 86);
          m.put( "GLOBAL", 87);
          m.put( "EXEC", 88);
          m.put( "ASSERT", 89);
          m.put( "AS", 90);
          m.put( "NAME", 91);
          m.put( "LETTER", 92);
          m.put( "DECNUMBER", 93);
          m.put( "HEXNUMBER", 94);
          m.put( "OCTNUMBER", 95);
          m.put( "FLOAT", 96);
          m.put( "COMPLEX", 97);
          m.put( "EXPONENT", 98);
          m.put( "DIGIT", 99);
          m.put( "SINGLE_STRING", 108);
          m.put( "SINGLE_STRING2", 109);
          m.put( "TRIPLE_STRING", 110);
          m.put( "TRIPLE_STRING2", 111);
          m.put( "SINGLE_USTRING", 112);
          m.put( "SINGLE_USTRING2", 113);
          m.put( "TRIPLE_USTRING", 114);
          m.put( "TRIPLE_USTRING2", 115);
        
        Map<Integer,String> fmap= new HashMap<>();
        
        for ( Entry<String,Integer> e: m.entrySet() ) {
            fmap.put( e.getValue(), e.getKey() );
        }
        
        grammarConstantLookup= fmap;
        
    }
    
    /**
     * Get the completion context, locating the carot within the code and 
     * identifying it as needing a package name, variable name, function, etc.
     * This parses the line using PythonGrammar
     * @param line the line
     * @param pos the position within the line
     * @param i0 always 0 (not used)
     * @param i1 always 0 (not used)
     * @param i2 the position within the line (not used)
     * @return the completion context
     */
    public static CompletionContext getCompletionContext( String line, int pos, int i0, int i1, int i2 ) {

        List<Token> tokens= new ArrayList<>(20);
        Token t;
        int myTokenIndex=-1;
        int thisTokenIndex=-1;
        int lastTokenEndPos=-1;
        
        String completable=null;
        
        line= preProcess( line, pos );
        PythonGrammar g= new PythonGrammar( new ReaderCharStream( new StringReader( line ) ) );
        
        do  {
            try {
                t= g.getNextToken();
            } catch ( TokenMgrError ex ) {
                return new CompletionContext( CompletionContext.DEFAULT_NAME, null, "" );
            }

            thisTokenIndex++;
            
            tokens.add(t);
            if ( myTokenIndex==-1 ) { // kludge for newline token
                if ( pos <= t.endColumn ) {
                    myTokenIndex= thisTokenIndex;
                    if ( pos>=t.beginColumn && t.image.length()>=(pos+1)-t.beginColumn ) {
                        completable= t.image.substring( 0,(pos+1)-t.beginColumn );
                    } else {
                        completable= "";
                    }
                }
            }
            lastTokenEndPos= t.endColumn;
            
        } while ( ( t.kind!=PythonGrammarConstants.EOF && t.kind!=PythonGrammarConstants.NEWLINE ) );
                
        if ( myTokenIndex==-1 && pos >= lastTokenEndPos ) {
            myTokenIndex= thisTokenIndex;
            completable= "";
        }
        
        CompletionContext result= null;
        
        logger.log(Level.FINE, "completions finds {0} tokens in {1}", new Object[] { tokens.size(), line } );
        if ( logger.isLoggable(Level.FINER) ) {
            int i=0;
            for ( Token t1: tokens ) {
                logger.log(Level.FINER, "{0}:\t{1}\t{2}", new Object[]{i++, t1.toString(), grammarConstantLookup.get(t1.kind)});
            }
        }
        
        //HERE IS COMPLETIONS
        if ( tokens.isEmpty() ) {
            return new CompletionContext( CompletionContext.DEFAULT_NAME, null, "" );
        } else {
            if ( tokens.get(0).kind==PythonGrammarConstants.FROM ) {
                int importTokenIndex= -1;
                for ( int i=1; importTokenIndex==-1 && i<tokens.size(); i++ ) {
                    if ( tokens.get(i).kind==PythonGrammarConstants.IMPORT ) importTokenIndex=i;
                }
                if ( importTokenIndex!=-1 && myTokenIndex>importTokenIndex ) {
                    result= new CompletionContext( CompletionContext.MODULE_NAME, join(tokens,1,importTokenIndex), completable );
                } else if ( myTokenIndex<importTokenIndex || importTokenIndex==-1 ) {
                    int ti= myTokenIndex;
                    if ( ti>0 && tokens.get(ti-1).kind== PythonGrammarConstants.DOT ) ti--;
                    if ( tokens.get(myTokenIndex).kind==PythonGrammarConstants.DOT ) completable= "";
                    result= new CompletionContext( CompletionContext.PACKAGE_NAME, join(tokens,1,ti), completable );
                }
            } else if ( tokens.get(0).kind==PythonGrammarConstants.IMPORT ) {
                if ( completable==null || completable.equals(".") ) completable="";
                if ( myTokenIndex>0 && tokens.get(myTokenIndex-1).image.equals(".") ) myTokenIndex= myTokenIndex-1;
                result= new CompletionContext( CompletionContext.PACKAGE_NAME, join(tokens,1,myTokenIndex), completable );
                
            } else if ( tokens.get(myTokenIndex).kind==PythonGrammarConstants.DOT && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.NAME ) {
                String contextString= exprBeforeDot(tokens, myTokenIndex);
                return new CompletionContext( CompletionContext.METHOD_NAME, contextString, "" );
            } else if ( tokens.get(myTokenIndex).kind==PythonGrammarConstants.DOT && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.RPAREN ) {
                // ds= PlasmaModelDataSet().<COMP>
                // DasLogger.getLogger(DasLogger.GRAPHICS_LOG).<COMP
                String contextString= exprBeforeDot(tokens, myTokenIndex);
                return new CompletionContext( CompletionContext.METHOD_NAME, contextString, "" );
            } else if ( tokens.get(myTokenIndex).kind==PythonGrammarConstants.DOT && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.RBRACKET ) {
                // dom.plots[0].<COMP>
                String contextString= exprBeforeDot(tokens, myTokenIndex);
                return new CompletionContext( CompletionContext.METHOD_NAME, contextString, "" );
            } else if ( myTokenIndex>2 && tokens.get(myTokenIndex).kind==PythonGrammarConstants.NAME && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.DOT && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.RBRACKET ) {
                // dom.plots[0].c<COMP>
                String contextString= exprBeforeDot(tokens, myTokenIndex-1);
                return new CompletionContext( CompletionContext.METHOD_NAME, contextString, completable );
            } else if ( myTokenIndex>1 && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.DOT && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.NAME ) {
                String contextString= exprBeforeDot(tokens, myTokenIndex-1);
                return new CompletionContext( CompletionContext.METHOD_NAME, contextString, completable );
            } else if ( myTokenIndex>1 && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.DOT && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.RPAREN ) {
                String contextString= exprBeforeDot(tokens, myTokenIndex-1);
                return new CompletionContext( CompletionContext.METHOD_NAME, contextString, tokens.get(myTokenIndex).image );
            } else if ( tokens.get(myTokenIndex).kind==PythonGrammarConstants.SINGLE_STRING  // some completions provided for strings.
                    ||  tokens.get(myTokenIndex).kind==PythonGrammarConstants.SINGLE_STRING2 ) {
                if ( myTokenIndex>3 && tokens.get(myTokenIndex-4).kind==PythonGrammarConstants.NAME 
                        && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.NAME 
                        && tokens.get(myTokenIndex-3).kind!=PythonGrammarConstants.EQUAL ) { // phib= getDataSet( f + '?column=' )
                    return new CompletionContext( CompletionContext.STRING_LITERAL_ARGUMENT, tokens.get(myTokenIndex-4).image, tokens.get(myTokenIndex).image );
                } else if ( myTokenIndex>1 && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.NAME ) { // phib= getDataSet( 'https://rbspgway.jhuapl.edu/share/ac6/data/AC6-A/2014/AC6-A_20141231_V03.tgz/AC6-A_20141231_L2_survey_V03.csv' )
                    return new CompletionContext( CompletionContext.STRING_LITERAL_ARGUMENT, tokens.get(myTokenIndex-2).image, tokens.get(myTokenIndex).image );
                } else if ( myTokenIndex>1 && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.PRINT ) { 
                    return new CompletionContext( CompletionContext.STRING_LITERAL_ARGUMENT, tokens.get(myTokenIndex-2).image, tokens.get(myTokenIndex).image );
                } else if ( myTokenIndex>1 && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.SINGLE_STRING && tokens.get(myTokenIndex-2).image.equals("'resourceURI'") ) { // getParam
                    return new CompletionContext( CompletionContext.STRING_LITERAL_ARGUMENT, tokens.get(myTokenIndex-2).image, tokens.get(myTokenIndex).image );
                } else if ( myTokenIndex>1 
                    && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.SINGLE_STRING 
                    && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.PLUS 
                    && tokens.get(myTokenIndex-0).kind==PythonGrammarConstants.SINGLE_STRING) { // "  " + "  "
                    return new CompletionContext( CompletionContext.STRING_LITERAL_ARGUMENT, tokens.get(myTokenIndex-2).image, tokens.get(myTokenIndex).image );                    
                } else {
                    return null;
                }
            } else {
                int closeParenCount= 0;
                for ( int i= myTokenIndex; i>0; i--) { // look for function call, because we want the completions for the function.
                    if ( tokens.get(i).kind==PythonGrammarConstants.RPAREN ) {
                        closeParenCount++;
                    } else if ( tokens.get(i).kind==PythonGrammarConstants.LPAREN ) {
                        closeParenCount--;
                        if ( closeParenCount<0 && tokens.get(i-1).kind==PythonGrammarConstants.NAME ) {
                            String contextString= tokens.get(i-1).image;
                            return new CompletionContext( CompletionContext.COMMAND_ARGUMENT, contextString, tokens.get(myTokenIndex).image );
                        }
                    }
                }
                if ( tokens.get(0).kind==PythonGrammarConstants.NAME ) { // why this?
                    if ( tokens.size()==3 ) {
                        return new CompletionContext( CompletionContext.DEFAULT_NAME, null, "" );
                    } else {
                        return new CompletionContext( CompletionContext.DEFAULT_NAME, null, completable );
                    }                
                }
            }
        }
        
        if ( result==null ) {
            return new CompletionContext( CompletionContext.DEFAULT_NAME, null, completable );
        }
        return result;
    }
}

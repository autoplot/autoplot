/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import org.python.parser.*;

/**
 *
 * @author jbf
 */
public class CompletionSupport {
    
    /**
     * 
     * @param tokens
     * @param offset first token
     * @param end last token index, exclusive
     * @return
     */
    private static String join( List<Token> tokens, int offset, int end ) {
        StringBuffer result= new StringBuffer( tokens.get(offset).image );
        for ( int i=1; i<(end-offset); i++ ) {
            result.append( tokens.get(offset+i).image );
        }
        return result.toString();
    }
    
    
    
    public static CompletionContext getCompletionContext( JTextComponent editor ) throws BadLocationException {
        int pos= editor.getCaretPosition();
        int i0= Utilities.getRowStart( editor, pos );
        int i2= Utilities.getRowEnd( editor, pos );
        
        String line= editor.getText( i0, i2-i0 );
        int i1= i0;
        
        if ( i1==i2 ) return new CompletionContext( CompletionContext.DEFAULT_NAME, null, "" );
        
        pos= pos - i0;
        //i2= i2- i0;
	i2= pos;
        i1= i1- i0;
        i0= i0- i0;
        
        return getCompletionContext( line, pos, i0, i1, i2 );
    }
    
    /**
     * finish off strings so python can parse.  return python tokens
     * so that the line is parsable, possibly trimming off code after carot.
     * @param line
     * @return
     */
    private static String preProcess( String line, int pos ) {
        char squote= '\'';
        int i=line.indexOf(squote);
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
            return line.substring(0,pos);
        }
    }
    
    public static CompletionContext getCompletionContext( String line, int pos, int i0, int i1, int i2 ) {

        List<Token> tokens= new ArrayList<Token>(20);
        Token t;
        int myTokenIndex=-1;
        int thisTokenIndex=-1;
        int lastTokenEndPos=-1;
        
        String completable=null;
        
        line= preProcess( line, pos );
        PythonGrammar g= new PythonGrammar( new ReaderCharStream( new StringReader( line ) ) );
        
        do  {
            t= g.getNextToken();       
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
        
        if ( tokens.size()==0 ) {
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
                    if ( tokens.get(ti-1).kind== PythonGrammarConstants.DOT ) ti--;
                    if ( tokens.get(myTokenIndex).kind==PythonGrammarConstants.DOT ) completable= "";
                    result= new CompletionContext( CompletionContext.PACKAGE_NAME, join(tokens,1,ti), completable );
                }
            } else if ( tokens.get(0).kind==PythonGrammarConstants.IMPORT ) {
                result= new CompletionContext( CompletionContext.PACKAGE_NAME, join(tokens,1,myTokenIndex), completable );
                
            } else if ( tokens.get(myTokenIndex).kind==PythonGrammarConstants.DOT && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.NAME ) {
                return new CompletionContext( CompletionContext.METHOD_NAME, tokens.get(myTokenIndex-1).image, "" );
            } else if ( myTokenIndex>1 && tokens.get(myTokenIndex-1).kind==PythonGrammarConstants.DOT && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.NAME ) {
                return new CompletionContext( CompletionContext.METHOD_NAME, tokens.get(myTokenIndex-2).image, completable );
            } else if ( tokens.get(myTokenIndex).kind==PythonGrammarConstants.SINGLE_STRING ) {
                if ( myTokenIndex>1 && tokens.get(myTokenIndex-2).kind==PythonGrammarConstants.NAME ) {
                    return new CompletionContext( CompletionContext.STRING_LITERAL_ARGUMENT, tokens.get(myTokenIndex-2).image, tokens.get(myTokenIndex).image );
                } else {
                    return null;
                }
            } else if ( tokens.get(0).kind==PythonGrammarConstants.NAME ) {
                return new CompletionContext( CompletionContext.DEFAULT_NAME, null, completable );
            }
        }
        
        if ( result==null ) {
            return new CompletionContext( CompletionContext.DEFAULT_NAME, null, completable );
        }
        return result;
    }
}

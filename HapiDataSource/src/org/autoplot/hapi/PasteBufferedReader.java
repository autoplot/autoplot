
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * paste several BufferedReaders together to appear as 
 * one BufferedReader.
 * @author jbf
 */
public class PasteBufferedReader implements AbstractLineReader {

    List<AbstractLineReader> readers;
    
    char delim='\t';
    
    public PasteBufferedReader() {
        readers= new ArrayList<>();
    }
    
    public void setDelim( char delim ) {
        this.delim= delim;
    }
    
    public void pasteBufferedReader( AbstractLineReader r ) {
        readers.add(r);
    }
    
    
    boolean monotonicKludge= true;
    String greatestValue= null;
    
    /**
     * set the reader to make the stream monotonically increasing in time,
     * dropping records which are non-monotonic.
     * @param t 
     */
    public void setMonotonicKludge( boolean t ) {
        this.monotonicKludge= t;
    }
    
    @Override
    public String readLine() throws IOException {
        StringBuilder b= new StringBuilder();
        boolean done= true;
        int col=0;
        boolean skipNonMono= false;
        for ( AbstractLineReader r: readers ) {
            if ( col>0 ) b.append(delim);
            String s= r.readLine();
            if ( col==0 && s!=null ) {
                if ( monotonicKludge && greatestValue!=null && greatestValue.compareTo(s)>0 ) {
                    skipNonMono= true;
                } else {
                    greatestValue= s;
                } 
                
            }

            if ( s!=null && skipNonMono==false ) {
                b.append( s );
                done= false;
            }
            col++;
        }
        if ( done ) {
            return null;
        } else {
            return b.toString();
        }
    }

    @Override
    public void close() throws IOException {
        for ( AbstractLineReader r: readers ) {
            r.close();
        }
    }
    
    public static void main( String[] args ) throws IOException {
        StringReader r1= new StringReader("a\nb\nc\n");
        StringReader r2= new StringReader("x\ny\nz\n");
        PasteBufferedReader r= new PasteBufferedReader();
        r.pasteBufferedReader( new SingleFileBufferedReader(new BufferedReader(r1) ) );
        r.pasteBufferedReader( new SingleFileBufferedReader(new BufferedReader(r2) ) );
        
        String s= r.readLine();
        while ( s!=null ) {
            System.err.println(s);
            s= r.readLine();
        }
    }
    
}

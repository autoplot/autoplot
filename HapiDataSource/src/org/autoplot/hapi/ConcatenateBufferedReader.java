/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.hapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * concatenates multiple readers so that they appear as one reader.
 * @author jbf
 */
public class ConcatenateBufferedReader implements AbstractLineReader {

    List<AbstractLineReader> readers;
    int currentReader;
    
    public ConcatenateBufferedReader() {
        readers= new ArrayList<>();
        currentReader= 0;
    }
    
    /**
     * add the reader to the readers, so that this reader will be used after the
     * others are used.
     * @param r 
     */    
    public void concatenateBufferedReader( AbstractLineReader r ) {
        readers.add(r);
    }
    
    @Override
    public String readLine() throws IOException {
        if ( currentReader==readers.size() ) {
            return null;
        } else {
            String line= readers.get(currentReader).readLine();
            while ( line==null ) {
                readers.get(currentReader).close();
                currentReader++;
                if ( currentReader==readers.size() ) {
                    return null;
                } else {
                    line= readers.get(currentReader).readLine();
                }
            }
            return line;
        }
    }

    @Override
    public void close() {
        // nothing needs to be done.
    }

    public static void main( String[] args ) throws IOException {
        StringReader r1= new StringReader("a\nb\nc\n");
        StringReader r2= new StringReader("x\ny\nz\n");
        ConcatenateBufferedReader r= new ConcatenateBufferedReader();
        r.concatenateBufferedReader( new SingleFileBufferedReader(new BufferedReader(r1) ) );
        r.concatenateBufferedReader( new SingleFileBufferedReader(new BufferedReader(r2) ) );
        
        String s= r.readLine();
        while ( s!=null ) {
            System.err.println(s);
            s= r.readLine();
        }
    }    
}

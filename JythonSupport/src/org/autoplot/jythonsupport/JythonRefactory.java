/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.jythonsupport;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.virbo.datasource.DataSourceUtil;

/**
 * Provide one class that manages backwards compatibility as package names
 * are changed.  See https://sourceforge.net/p/autoplot/feature-requests/528/
 * @author jbf
 */
public class JythonRefactory {
    
    /**
     * map imports within file to the new names.
     * @param f jython script
     * @return new script.
     */
    public static File fixImports( File f ) throws IOException {
        FileInputStream fin= new FileInputStream(f);
        InputStream out= fixImports( fin );
        File fileout= File.createTempFile("fixImports", ".jy");
        FileOutputStream fout= new FileOutputStream(fileout);
        DataSourceUtil.transfer( out, fout );
        return fileout;
    }
    
    private static final Map<String,String> forwardMap= new HashMap<>();
    static {
        forwardMap.put( "org.das2.qds.examples", "org.virbo.dataset.examples" );
        forwardMap.put( "org.das2.qds.array", "org.virbo.dataset" );
    }
    
    
    
    private static final Pattern IMPORT_REGEX= Pattern.compile("(\\s*)from(\\s+)([a-zA-Z0-9.]+)(\\s+)import(\\s+)([a-zA-Z0-9 ,]+)(\\s*)");
    private static final Pattern IMPORT_AS_REGEX= Pattern.compile("(\\s*)import(\\s+)([a-zA-Z0-9.]+)(\\s+)(as(\\s+)([a-zA-Z0-9]+)(\\s*))?");
    
    /**
     * read in the stream, replacing import statements with new packages.
     * @param in the input stream containing Jython code.
     * @return new stream, approximately the same length and same number of lines.
     * @throws IOException 
     */
    public static InputStream fixImports( InputStream in ) throws IOException {
        
        BufferedReader reader= new BufferedReader( new InputStreamReader(in) );
        String line= reader.readLine(); 
        ByteArrayOutputStream baos= new ByteArrayOutputStream(10000);
        PrintStream writer= new PrintStream( baos );
        while ( line!=null ) {
            Matcher m;
            m= IMPORT_REGEX.matcher(line);
            if ( m.matches() ) {
                String p= m.group(3);
                String n= forwardMap.get(p);
                if ( n!=null ) {
                    writer.print( m.group(1) );
                    writer.print( "from" );
                    writer.print( m.group(2) );
                    writer.print( n );
                    writer.print( m.group(4) );
                    writer.print( "import" );
                    writer.print( m.group(5) );
                    writer.print( m.group(6) );
                    writer.print( m.group(7) );
                    writer.println();
                } else {
                    writer.println(line);
                }
            } else {
                m= IMPORT_AS_REGEX.matcher(line);
                if ( m.matches() ) {
                    // identify the path and class file by using the convention that path must start with lower case.
                    String p = m.group(3);
                    String[] pp= p.split("\\.",-2);
                    StringBuilder pathBuilder= new StringBuilder();
                    StringBuilder clasBuilder= new StringBuilder();
                    int iclass=-1;
                    for ( int i=0; i<pp.length; i++ ) { 
                        if ( iclass==-1 && pp[i].length()>0 && Character.isUpperCase(pp[i].charAt(0)) ) {
                            iclass=i;
                            clasBuilder.append(pp[i]);
                        } else if ( iclass>-1 ) {
                            clasBuilder.append(".");
                            clasBuilder.append(pp[i]);
                        } else {
                            if ( i>0 ) pathBuilder.append(".");
                            pathBuilder.append(pp[i]);
                        }
                    }
                    String path= pathBuilder.toString();
                    String srcPath= path;
                    String n= forwardMap.get(path);
                    if ( n!=null ) path= n;
                    String clas= clasBuilder.toString();
                    writer.print( m.group(1) );
                    writer.print( "import" );
                    writer.print( m.group(2) );
                    writer.print( path );
                    if ( clas.length()>0 ) {
                        writer.print( "." );
                        writer.print( clas );
                    }
                    writer.print( m.group(4) );
                    if ( m.group(5)!=null ) { // as clause
                        writer.print( m.group(5) ); 
                    } else {
                        writer.print( " as " );
                        writer.print( m.group(3) );
                    }
                    writer.println();

                } else {
                    writer.println(line);
                }
            }
            line= reader.readLine(); 
        }
        return new ByteArrayInputStream( baos.toByteArray() );
    }
    
    public static void main( String[] args ) throws IOException {
        File f= fixImports( new File( "/home/jbf/ct/autoplot/rfe/528/examples/rfe528.jy") );
        System.err.println(f);
    }
}

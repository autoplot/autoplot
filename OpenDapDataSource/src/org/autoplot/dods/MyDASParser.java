/*
 * MyDASParser.java
 *
 * Created on July 31, 2007, 11:11 AM
 */

package org.autoplot.dods;

import opendap.dap.BaseType;
import opendap.dap.DAS;
import opendap.dap.DASException;
import opendap.dap.parser.DASParser;
import opendap.dap.parser.ParseException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 *
 * @author jbf
 */
public class MyDASParser {
    
    /** Creates a new instance of MyDASParser */
    public MyDASParser() {
    }
    
    private DAS myDAS;
    
    /**
     * parse the stream.  The stream is left open.
     * @param in an InputStream 
     * @throws ParseException
     * @throws DASException 
     */
    public void parse( InputStream in ) throws ParseException, DASException {
        
        DASParser p= new DASParser(in);
        
        myDAS= new DAS();
        p.Attributes( myDAS );
        
    }
    
    
    String[] getVariableNames() {
        Enumeration en= myDAS.getNames();
        ArrayList<String> result= new ArrayList<>();
        while ( en.hasMoreElements() ) {
            result.add( ((BaseType)en.nextElement()).getName() );
        }
        return result.toArray( new String[ result.size() ] );
    }
    
    public DAS getDAS() {
        return myDAS;
    }
     
    public static void main( String[] args ) throws Exception {
        URL url= new URL( "http://acdisc.gsfc.nasa.gov/opendap/HDF-EOS5/Aura_OMI_Level3/OMAEROe.003/2005/OMI-Aura_L3-OMAEROe_2005m0101_v003-2011m1109t081947.he5.dds?TerrainReflectivity" );
        MyDASParser parser= new MyDASParser();
        try ( InputStream in= url.openStream() ) {
            parser.parse( in );
        }
    }
}

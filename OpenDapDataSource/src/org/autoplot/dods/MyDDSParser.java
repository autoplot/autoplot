/*
 * DDSParser.java
 *
 * Created on May 2, 2007, 11:45 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot.dods;

import opendap.dap.BaseType;
import opendap.dap.BaseTypeFactory;
import opendap.dap.DArray;
import opendap.dap.DDS;
import opendap.dap.DDSException;
import opendap.dap.DFloat64;
import opendap.dap.DSequence;
import opendap.dap.DefaultFactory;
import opendap.dap.NoSuchVariableException;
import opendap.dap.Server.InvalidParameterException;
import opendap.dap.parser.DDSParser;
import opendap.dap.parser.ParseException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import opendap.dap.DGrid;

/**
 *
 * @author jbf
 */
public class MyDDSParser {

    DDS myDDS;

    /** Creates a new instance of DDSParser */
    public MyDDSParser() {
    }

    public void parse(InputStream in) throws ParseException, DDSException {

        DDSParser p = new DDSParser(in);

        myDDS = new DDS();
        BaseTypeFactory factory = new DefaultFactory();
        p.Dataset(myDDS, factory);

    }

    /**
     * return the dimensions, or null for Sequences or Grids
     * @param variable
     * @return the dimensions, or null for Sequences or Grids
     * @throws opendap.dap.NoSuchVariableException
     */
    public int[] getRecDims(String variable) throws NoSuchVariableException {
        BaseType t = myDDS.getVariable(variable);
        int[] result;
        if (t instanceof DSequence) {
            return null;
            
        } else if ( t instanceof DGrid ) {
            //DGrid dgrid = (DGrid) t;
            return null;
            
        } else {
            DArray darray = (DArray) t;
            result = new int[darray.numDimensions()];
            for (int i = 0; i < result.length; i++) {
                try {
                    result[i] = darray.getDimension(i).getStop();
                } catch (InvalidParameterException ex) {
                    ex.printStackTrace();
                }
            }
        }

        return result;
    }

    String[] getVariableNames() {
        Enumeration en = myDDS.getVariables();
        ArrayList<String> result = new ArrayList<String>();
        while (en.hasMoreElements()) {
            BaseType bt= ((BaseType) en.nextElement());
            result.add( bt.getName());
        }
        return result.toArray(new String[result.size()]);
    }    
    
    /**
     * returns null or the names of the depend variables.
     * @param var the variable name (e.g. TerrainReflectivity)
     * @return the depend names (e.g. [lat,lon] )
     * @throws NoSuchVariableException
     * @throws InvalidParameterException 
     */
    String[] getDepends( String var ) throws NoSuchVariableException, InvalidParameterException {
        BaseType bt= myDDS.getVariable(var);
        ArrayList<String> result= new ArrayList();
        if ( bt instanceof DArray ) {
            DArray a= ((DArray) bt);
            for ( int i=0; i<a.numDimensions(); i++ ) {
                String n= a.getDimension(i).getName();
                result.add(n);
            }                
            if ( result.size()>0 && result.get(0).equals(var) ) {
                return null;
            } else {
                return result.toArray( new String[result.size()] );
            }
        } else if ( bt instanceof DGrid ) {
            DGrid g= ((DGrid) bt);
            Enumeration e= g.getVariables();
            while ( e.hasMoreElements() ) {
                Object o= e.nextElement();
                if ( o instanceof DArray ) {
                    DArray a= (DArray)o;
                    result.add( a.getName() );
                } else {
                    result.add( null );
                }
            }
            if ( result.size()>0 && result.get(0).equals(var) ) {
                return result.subList(1,result.size()).toArray( new String[result.size()-1] );
            } else {
                return result.toArray( new String[result.size()] );
            }
        } else {
            return null;
        }
        
    }
}

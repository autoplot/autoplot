/*
 * DDSParser.java
 *
 * Created on May 2, 2007, 11:45 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.dods;

import dods.dap.BaseType;
import dods.dap.BaseTypeFactory;
import dods.dap.DArray;
import dods.dap.DDS;
import dods.dap.DDSException;
import dods.dap.DFloat64;
import dods.dap.DSequence;
import dods.dap.DefaultFactory;
import dods.dap.NoSuchVariableException;
import dods.dap.Server.InvalidParameterException;
import dods.dap.parser.DDSParser;
import dods.dap.parser.ParseException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;

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
     * return the dimensions, or null for Sequences
     * @param variable
     * @return
     * @throws dods.dap.NoSuchVariableException
     */
    public int[] getRecDims(String variable) throws NoSuchVariableException {
        BaseType t = myDDS.getVariable(variable);
        int[] result;
        if (t instanceof DSequence) {
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
        } else {
            return null;
        }
        
    }
}

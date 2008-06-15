/*
 * CdfFileDataSource.java
 *
 * Created on July 23, 2007, 8:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.cdfdatasource;

import org.virbo.metatree.IstpMetadataModel;
import org.das2.util.monitor.ProgressMonitor;
import gsfc.nssdc.cdf.Attribute;
import gsfc.nssdc.cdf.CDF;
import gsfc.nssdc.cdf.CDFException;
import gsfc.nssdc.cdf.Entry;
import gsfc.nssdc.cdf.Variable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class CdfFileDataSource extends AbstractDataSource {
    
    HashMap properties;
    HashMap<String,Object> attributes;
    
    /** Creates a new instance of CdfFileDataSource */
    public CdfFileDataSource( URL url ) {
        super( url );
    }
    
    
    /* read all the variable attributes into a HashMap */
    private HashMap<String,Object> readAttributes( CDF cdf, Variable var, int depth ) {
        HashMap<String,Object> properties= new HashMap<String,Object>();
        Pattern p= Pattern.compile("DEPEND_[0-9]");
	
        Vector v= cdf.getAttributes();
        for ( int i=0; i<v.size(); i++ ) {
            Attribute attr= (Attribute)v.get(i);
            Entry entry= null;
            try {
                entry= attr.getEntry(var);
		
		
		if ( p.matcher(attr.getName()).matches() & depth==0 ) {
                    Object val= entry.getData();
		    String name= (String)val;
		    Map<String,Object> newVal= readAttributes( cdf, cdf.getVariable(name), depth+1 );
                    newVal.put( "NAME", name ); // tuck it away, we'll need it later.
		    properties.put( attr.getName(), newVal );
                    
		} else {
                    properties.put( attr.getName(), entry.getData() );
                }
            } catch ( CDFException e ) {
            }
        }
        
        return properties;
    }
    
    
    public org.virbo.dataset.QDataSet getDataSet( ProgressMonitor mon) throws IOException, CDFException {
        File cdfFile;
        cdfFile= getFile( mon );
        
        String fileName= cdfFile.toString();
        if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
        
        Map map= getParams();
        
        CDF cdf= CDF.open( fileName, CDF.READONLYon );
        String svariable= (String) map.get("id");
        if ( svariable==null ) svariable= (String) map.get("arg_0");
        
        Variable variable= cdf.getVariable( svariable );
        attributes= readAttributes( cdf, variable, 0 );
        
        WritableDataSet result= wrapDataSet(cdf, svariable, false);
        cdf.close();
        
        return result;
        
    }
    
    /**
     * @param reform for depend_1, we read the one and only rec, and the rank is decreased by 1.
     */
    private WritableDataSet wrapDataSet( final CDF cdf, final String svariable, boolean reform ) throws CDFException {
        Variable variable= cdf.getVariable( svariable );
        
        long varType= variable.getDataType();
        long numRec= variable.getNumWrittenRecords();
        
        WritableDataSet result;
        if ( reform ) {
            result= CdfUtil.wrapCdfHyperData( variable, 0, -1 );
        } else {
            result= CdfUtil.wrapCdfHyperData( variable, 0, numRec );
        }
        result.putProperty( QDataSet.NAME, svariable );
        HashMap thisAttributes= readAttributes( cdf, variable, 0 );
        
        Map dep0m= (Map) thisAttributes.get( "DEPEND_0" );
        if ( dep0m!=null ) {
            try {
                WritableDataSet depDs= wrapDataSet( cdf, (String)dep0m.get("NAME") , false);
                if ( DataSetUtil.isMonotonic(depDs) ) {
                    depDs.putProperty( QDataSet.MONOTONIC, Boolean.TRUE );
                }
                result.putProperty( QDataSet.DEPEND_0, depDs );
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }
        
        Map dep1m= (Map) thisAttributes.get( "DEPEND_1" );
        if ( dep1m!=null ) {
            try {
                WritableDataSet depDs= wrapDataSet( cdf, (String)dep1m.get("NAME"), true );
                if ( DataSetUtil.isMonotonic(depDs) ) {
                    depDs.putProperty( QDataSet.MONOTONIC, Boolean.TRUE );
                }
                result.putProperty( QDataSet.DEPEND_1, depDs );
            } catch ( Exception e ) {
                e.printStackTrace(); // to support lanl.
            }
        }
        
        return result;
    }
    
    @Override
    public boolean asynchronousLoad() {
        return true;
    }

    @Override
    public MetadataModel getMetadataModel() {
        return new IstpMetadataModel();
    }
        
    
    @Override
    public Map<String,Object> getMetaData( ProgressMonitor mon ) {
        if ( attributes==null ) return null; // transient state
        
        return attributes;
    }
    
}

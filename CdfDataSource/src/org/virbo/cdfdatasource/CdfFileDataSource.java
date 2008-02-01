/*
 * CdfFileDataSource.java
 *
 * Created on July 23, 2007, 8:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.cdfdatasource;

import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.util.DasProgressMonitor;
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
import javax.swing.tree.TreeModel;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.WritableDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.metatree.NameValueTreeModel;

/**
 *
 * @author jbf
 */
public class CdfFileDataSource extends AbstractDataSource {
    
    HashMap properties;
    HashMap attributes;
    
    /** Creates a new instance of CdfFileDataSource */
    public CdfFileDataSource( URL url ) {
        super( url );
    }
    
    
    /* read all the variable attributes into a HashMap */
    private HashMap readAttributes( CDF cdf, Variable var ) {
        HashMap properties= new HashMap();
        
        Vector v= cdf.getAttributes();
        for ( int i=0; i<v.size(); i++ ) {
            Attribute attr= (Attribute)v.get(i);
            Entry entry= null;
            try {
                entry= attr.getEntry(var);
                properties.put( attr.getName(), entry.getData() );
            } catch ( CDFException e ) {
            }
        }
        
        return properties;
    }
    
    /* convert the Attributes into properties the Renderers look for. */
    private HashMap readProperties( CDF cdf, Variable variable ) {
        HashMap properties= new HashMap();
        DatumRange range;
        
        HashMap attrs= readAttributes( cdf, variable );
        range= CdfUtil.getRange( attrs );
        properties.put( QDataSet.TYPICAL_RANGE, range );
        properties.put( QDataSet.SCALE_TYPE, CdfUtil.getScaleType(attrs) );
        if ( attrs.containsKey( "LABLAXIS" ) ) {
            properties.put( QDataSet.LABEL, attrs.get("LABLAXIS") );
        }
        
        return properties;
    }
    
    public org.virbo.dataset.QDataSet getDataSet( DasProgressMonitor mon) throws IOException, CDFException {
        File cdfFile;
        cdfFile= getFile( mon );
        
        String fileName= cdfFile.toString();
        if ( System.getProperty("os.name").startsWith("Windows") ) fileName= CdfUtil.win95Name( cdfFile );
        
        Map map= getParams();
        
        CDF cdf= CDF.open( fileName, CDF.READONLYon );
        String svariable= (String) map.get("id");
        if ( svariable==null ) svariable= (String) map.get("arg_0");
        
        Variable variable= cdf.getVariable( svariable );
        attributes= readAttributes( cdf, variable );
        
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
        HashMap thisAttributes= readAttributes( cdf, variable );
        
        String dep0= (String) thisAttributes.get( "DEPEND_0" );
        if ( dep0!=null ) {
            try {
                WritableDataSet depDs= wrapDataSet( cdf, dep0 , false);
                result.putProperty( QDataSet.DEPEND_0, depDs );
            } catch ( Exception e ) {
                e.printStackTrace();
            }
        }
        
        String dep1= (String) thisAttributes.get( "DEPEND_1" );
        if ( dep1!=null ) {
            try {
                WritableDataSet depDs= wrapDataSet( cdf, dep1, true );
                result.putProperty( QDataSet.DEPEND_1, depDs );
            } catch ( Exception e ) {
                e.printStackTrace(); // to support lanl.
            }
        }
        
        return result;
    }
    
    public boolean asynchronousLoad() {
        return true;
    }
    
    public TreeModel getMetaData( DasProgressMonitor mon ) {
        
        if ( attributes==null ) return null; // transient state
        
        return NameValueTreeModel.create( "metadata(CDF)", attributes );
    }
    
}

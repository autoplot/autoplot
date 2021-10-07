/*
 * NetCDFDataSource.java
 *
 * Created on April 4, 2007, 7:03 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.netCDF;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.dataset.NoDataInIntervalException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.das2.datum.LoggerManager;
import org.das2.datum.Units;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.qds.DataSetOps;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.AbstractDataSource;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceFactory;
import org.autoplot.datasource.MetadataModel;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.TransposeRankNDataSet;
import org.autoplot.metatree.IstpMetadataModel;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Structure;
import ucar.nc2.Variable;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.ncml.NcMLReader;

/**
 * Read Data from NetCDF and HDF5 files.
 * @author jbf
 */
public class NetCDFDataSource extends AbstractDataSource {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.netcdf");
    
    protected static final String PARAM_WHERE = "where";
    protected static final String PARAM_X = "X";
    protected static final String PARAM_Y = "Y";
    protected static final String PARAM_X_UNITS = "xunits";
        
    private Variable variable;
    
    private Map<String,Object> globalAttributes;
    
    /**
     * if non-null, the variable to use for the where filter.
     */
    private Variable whereVariable;  
    private Variable xVariable;
    private Variable yVariable;
    
    private String sMyUrl;
    private String svariable;
    private String swhereVariable;
    private String sxVariable;
    private String syVariable;
    
    private NetcdfDataset ncfile;
    private String constraint; // null, or string like [:,:,4,5]

/*    static {
        try {
            NetcdfFile.registerIOProvider("org.autoplot.netCDF.APIOServiceProvider");
        } catch (IllegalAccessException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
*/
    /** 
     * Creates a new instance of NetCDFDataSource
     * @param uri the URI to read.
     * @throws java.io.IOException
     */
    public NetCDFDataSource( URI uri ) throws IOException {
        super(uri);
        parseUrl();
        
    }
    
    private void parseUrl() {
        String surl= DataSetURI.fromUri(uri);
        int i= surl.lastIndexOf('?');
        if ( i>-1 ) {
            sMyUrl= surl.substring(0, i);
        } else {
            sMyUrl= surl;
        }
        
        if ( i==-1 ) {
            svariable= null;
            
        } else {
            // get the variable            
            Map<String,String> p= getParams();
            if ( p.containsKey( "id" ) ) {
                svariable= (String) p.get( "id" );
            } else {
                svariable= (String) p.get("arg_0"); 
            }

            swhereVariable= p.get( PARAM_WHERE );  // may be null, typically is null.
            sxVariable= p.get( PARAM_X ); // may be null, typically is null.
            if ( sxVariable==null ) sxVariable= p.get( "x" );
            syVariable= p.get( PARAM_Y ); // may be null, typically is null.
            if ( syVariable==null ) syVariable= p.get( "y" );

            if ( svariable==null && syVariable!=null ) {
                svariable= syVariable;
                syVariable= null;
            }
            
            if ( svariable!=null ) {
                svariable= svariable.replaceAll(" ","+");
                int ic= svariable.indexOf("[");
                if ( ic>-1 ) {
                    constraint= svariable.substring(ic);
                    svariable= svariable.substring(0,ic);
                } else {
                    constraint= null;
                }
            }
                        
        }
    }
    
    @Override
    public QDataSet getDataSet( ProgressMonitor mon) throws IOException, NoDataInIntervalException, ParseException {
        logger.entering("org.autoplot.netCDF.NetCDFDataSource", "getDataSet");
        mon.started();
        mon.setTaskSize(20);
        try { 
            readData( mon.getSubtaskMonitor(0,15,"read data") );
            
            QDataSet result= NetCdfVarDataSet.create( variable, constraint, ncfile, mon.getSubtaskMonitor(15,20,"copy over ") );
            
            if ( sxVariable!=null && sxVariable.length()>0 ) {
                NetCdfVarDataSet xds= NetCdfVarDataSet.create( xVariable, constraint, ncfile, new NullProgressMonitor() );
            
                String xunits= getParam( "xunits", "" );
                if ( !xunits.equals("") ) {
                    xds.putProperty( QDataSet.UNITS, Units.lookupUnits(xunits) );
                }
                result = Ops.link( xds, result );
            }

            if ( syVariable!=null && syVariable.length()>0 ) {
                NetCdfVarDataSet yds= NetCdfVarDataSet.create( yVariable, constraint, ncfile, new NullProgressMonitor() );
                result = Ops.link( result.property(QDataSet.DEPEND_0), yds, result );
            }
            
            String w= (String)getParam(PARAM_WHERE,"" );
            if ( w!=null && w.length()>0 ) {
                int ieq= w.indexOf(".");
                String sparm= w.substring(0,ieq);
                String constraint1;
                int k = sparm.indexOf("[");
                if (k != -1) {
                    constraint1 = sparm.substring(k);
                    sparm = sparm.substring(0, k);
                } else {
                    constraint1 = constraint;
                }  
                NetCdfVarDataSet whereParm= NetCdfVarDataSet.create( whereVariable, constraint1, ncfile, new NullProgressMonitor() );
                result = doWhereFilter( w, whereParm, DataSetOps.makePropertiesMutable(result) );
            }

            result= checkLatLon(result);
               
            String unitsString= getParam("units", null );
            if ( unitsString!=null ) {
                Units u = Units.lookupUnits(unitsString);
                result= Ops.putProperty( result, QDataSet.UNITS, u );
            }
            
            String svalidMin= getParam("validMin",null );
            if ( svalidMin!=null ) {
                Double validMin= Double.parseDouble(svalidMin);
                result= Ops.putProperty( result, QDataSet.VALID_MIN, validMin );
            }

            String svalidMax= getParam("validMax",null );
            if ( svalidMax!=null ) {
                Double validMax= Double.parseDouble(svalidMax);
                result= Ops.putProperty( result, QDataSet.VALID_MAX, validMax );
            }
            
            String sfillValue= getParam("fillValue",null );
            if ( sfillValue!=null ) {
                Double fillValue= Double.parseDouble(sfillValue);
                result= Ops.putProperty( result, QDataSet.FILL_VALUE, fillValue );
            }
            
            Map<String,Object> metadata= new LinkedHashMap<>();
            metadata.put( "GlobalAttributes", globalAttributes );
            
            result= Ops.putProperty( result, QDataSet.METADATA, metadata );

            logger.finer("ncfile.close()");
            ncfile.close();
            
            ncfile= null;
            return result;
            
        } finally {
            mon.finished();
            logger.exiting("org.autoplot.netCDF.NetCDFDataSource", "getDataSet");
        }
        
    }
    
    
    /**
     * check for lat and lon tags, transpose if lat come before lon.
     * @param v
     */
    private QDataSet checkLatLon( QDataSet v ) {
        int lat=-1;
        int lon=-1;
        for ( int i=0; i<v.rank();i++ ) {
            QDataSet dep= (QDataSet) v.property( "DEPEND_"+i );
            if ( dep!=null ) {
                String name= (String) dep.property("NAME");
                if ( "lon".equals(name) ) lon=i;
                if ( "lat".equals(name) ) lat=i;
            }
        }
        if ( lat>-1 && lon>-1 && lat<lon ) {
            int[] order= new int[v.rank()];
            for ( int i=0;i<v.rank(); i++) order[i]= i;
            int t= order[lat];
            order[lat]= order[lon];
            order[lon]= t;
            QDataSet transpose= new TransposeRankNDataSet( v, order );
            return transpose;
        } else {
            return v;
        }
    }
    
    /**
     * this is sloppy in that it opens the file and then relies on someone else to close it.
     * @param mon
     * @throws IOException
     */
    private void readData( ProgressMonitor mon ) throws IOException {

        File file= getFile(mon.getSubtaskMonitor("getFile"));

        NetcdfDataset dataset;

        mon.started();
        try {
            if ( sMyUrl.endsWith(".ncml" ) ) {
                dataset= NcMLReader.readNcML( file.toURI().toURL().toString(), null ); // bug 1958: this handles %20 as escapes
            } else {
                String location= file.toString(); // bug 1958: but this doesn't
                NetCDFDataSourceFactory.checkMatlab(location);
                logger.log(Level.FINE, "NetcdfFile.open( {0} )", location);
                NetcdfFile f= NetcdfFile.open( location );
                dataset= new NetcdfDataset( f );
            }

            ncfile= dataset;

            logger.log(Level.FINER, "dataset.getVariables()" );
            List<Variable> variables= (List<Variable>)dataset.getVariables();
            
            List<Attribute> globalAttributes= dataset.getGlobalAttributes();
            
            Map<String,Object> metadata= new LinkedHashMap<>();
            
            for ( Attribute a: globalAttributes ) {
                if ( a.isArray() ) {
                    metadata.put( a.getName(), a.getValues() );
                } else {
                    metadata.put( a.getName(), a.getStringValue() );
                }
            }
            this.globalAttributes= metadata;
            
            if ( svariable==null ) {
                for (Variable v : variables) {
                    if ( !v.getDimension(0).getName().equals(v.getName()) ) { // search for dependent variable
                        variable= v;
                        break;
                    }
                }
                if ( variable==null ) throw new IllegalArgumentException("Unable to identify dependent variable");
            } else {
                for (Variable v : variables) {
                    if ( v instanceof Structure ) {
                        for ( Variable v2: ((Structure) v).getVariables() ) {
                            if ( !v2.getDataType().isNumeric() ) continue;
                            if ( v2.getName().replaceAll(" ","+").equals( svariable) ) {
                                variable= v2;
                            }
                        }
                    } else {
                        if ( v.getName().replaceAll(" ", "+").equals( svariable ) ) { //TODO: verify this, it's probably going to cause problems now.
                            variable= v;
                        }
                    }
                }
                if ( variable==null ) throw new IllegalArgumentException("No such variable: "+svariable);
            }
            
            if ( swhereVariable!=null ) {
                int i= swhereVariable.lastIndexOf("(");
                i= swhereVariable.lastIndexOf(".",i);
                String swv= swhereVariable.substring(0,i);
                i= swv.indexOf("[");
                if ( i>-1 ) {
                    swv= swv.substring(0,i);
                }
                for (Variable v : variables) {
                    if ( v instanceof Structure ) {
                        for ( Variable v2: ((Structure) v).getVariables() ) {
                            if ( !v2.getDataType().isNumeric() ) continue;
                            if ( v2.getName().replaceAll(" ","+").equals( swv ) ) {
                                whereVariable= v2;
                            }
                        }
                    } else {
                        if ( v.getName().replaceAll(" ", "+").equals( swv ) ) { //TODO: verify this, it's probably going to cause problems now.
                            whereVariable= v;
                        }
                    }
                }
                if ( whereVariable==null ) throw new IllegalArgumentException("where refers to unresolved variable: "+swv );
            }
            
            if ( sxVariable!=null ) {
                for (Variable v : variables) {
                    if ( v instanceof Structure ) {
                        for ( Variable v2: ((Structure) v).getVariables() ) {
                            if ( !v2.getDataType().isNumeric() ) continue;
                            if ( v2.getName().replaceAll(" ","+").equals(sxVariable ) ) {
                                xVariable= v2;
                            }
                        }
                    } else {
                        if ( v.getName().replaceAll(" ", "+").equals(sxVariable ) ) { //TODO: verify this, it's probably going to cause problems now.
                            xVariable= v;
                        }
                    }
                }
                if ( xVariable==null ) throw new IllegalArgumentException("x refers to unresolved variable: "+sxVariable );
            }
            
            if ( syVariable!=null ) {
                for (Variable v : variables) {
                    if ( v instanceof Structure ) {
                        for ( Variable v2: ((Structure) v).getVariables() ) {
                            if ( !v2.getDataType().isNumeric() ) continue;
                            if ( v2.getName().replaceAll(" ","+").equals( syVariable ) ) {
                                yVariable= v2;
                            }
                        }
                    } else {
                        if ( v.getName().replaceAll(" ", "+").equals( syVariable ) ) { //TODO: verify this, it's probably going to cause problems now.
                            yVariable= v;
                        }
                    }
                }
                if ( yVariable==null ) {
                    throw new IllegalArgumentException("Y refers to unresolved variable: "+syVariable );
                }
            }            
        } finally {
            mon.finished();
        }
    }
   
    
    public static DataSourceFactory getFactory() {
        return new NetCDFDataSourceFactory();
    }
    
    @Override
    public Map<String,Object> getMetadata( ProgressMonitor mon ) throws Exception {
        logger.entering("org.autoplot.netCDF.NetCDFDataSource", "getMetadata");
        mon.started();
        try {
            mon.setProgressMessage("reading metadata");
            readData( mon.getSubtaskMonitor("readData") );
            List attr;
            
            logger.finer("variable.getAttributes()");
            attr= variable.getAttributes();

            if ( attr==null ) {
                logger.finer("attr was null");
                return null;
            } // transient state

            Map<String,Object> result= new LinkedHashMap<>();
            for (Object attr1 : attr) {
                Attribute at = (Attribute) attr1;
                if ( at.getLength()==1 && ( at.getName().equals("valid_min") || at.getName().equals("valid_max") || at.getName().equals("missing_value") ) ) {
                    try {
                        Object o= at.getValue(0);
                        if ( o!=null ) result.put( at.getName(), o );
                    } catch ( Exception e ) {
                        result.put( at.getName(), at.getStringValue() );
                    }
                } else {
                    result.put( at.getName(), at.getStringValue() );
                }
            }

            if ( globalAttributes!=null ) {
                result.put( "GlobalAttributes", globalAttributes );
            }
            
            try {
                if ( ncfile!=null ) {
                    logger.finer("ncfile.close()");
                    ncfile.close();
                    ncfile= null;
                }
            } catch ( IOException ex ) {
                logger.log( Level.WARNING, null, ex );
            }
            return result;
        } finally {
            mon.finished();
            logger.exiting("org.autoplot.netCDF.NetCDFDataSource", "getMetadata");
        }
        
        
    }

    @Override
    public MetadataModel getMetadataModel() {
        if ( true ) {
            return MetadataModel.createNullModel();
        } else {
            if ( variable==null ) {
                try {
                    readData(new NullProgressMonitor()); // sometimes we come in here from MetadataPanel.updateProperties before reading the data
                } catch (IOException ex) {
                    logger.info("exception when trying to readData to test for ISTP props, returning null model");
                    return MetadataModel.createNullModel();
                }
            }
            MetadataModel result= MetadataModel.createNullModel();

            logger.finer("getVariable().getAttributes()");
            List attr= variable.getAttributes();
            if ( attr==null ) return null; // transient state
            for (Object attr1 : attr) {
                Attribute at = (Attribute) attr1;
                if ( at.getName().equals("VAR_TYPE") ) {
                    result= new IstpMetadataModel();
                }
            }
            try {
                if ( ncfile!=null ) {
                    logger.finer("ncfile.close()");
                    ncfile.close();
                    ncfile= null;
                }
            } catch ( IOException ex ) {
                logger.log(Level.WARNING,null,ex);
            }
            return result;
        }
    }


    
}

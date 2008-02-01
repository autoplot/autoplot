/*
 * DodsAdapter.java
 *
 * Created on January 29, 2007, 5:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.dods;

import dods.dap.BaseType;
import dods.dap.DArray;
import dods.dap.DArrayDimension;
import dods.dap.DConnect;
import dods.dap.DDS;
import dods.dap.DDSException;
import dods.dap.DGrid;
import dods.dap.DODSException;
import dods.dap.Float32PrimitiveVector;
import dods.dap.NoSuchVariableException;
import dods.dap.PrimitiveVector;
import dods.dap.StatusUI;
import dods.dap.parser.ParseException;
import edu.uiowa.physics.pw.das.datum.Units;
import edu.uiowa.physics.pw.das.util.DasProgressMonitor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.TableDataSetAdapter;
import org.virbo.dataset.VectorDataSetAdapter;
import org.virbo.dods.DodsVarDataSet;

/**
 *
 * @author jbf
 */
public class DodsAdapter {
    
    /**
     * http://www.cdc.noaa.gov/cgi-bin/nph-nc/Datasets/kaplan_sst/sst.mean.anom.nc
     */
    private URL source;
    
    /**
     * sst
     */
    private String variable;
    
    /**
     *?sst[0:100:1811][0:10:35][0:10:71]
     */
    private String constraint;
    
    private DDS dds;
    
    private HashMap properties;
    
    /** Creates a new instance of DodsAdapter */
    public DodsAdapter( URL source, String variable ) {
        this.source= source;
        this.variable= doEscapes(variable);
        properties= new HashMap();
    }
    
    private String doEscapes( String s ) {
        StringBuffer result= new StringBuffer();
        for ( int i=0; i<s.length(); i++ ) {
            char ch= s.charAt(i);
            if ( Character.isJavaIdentifierPart(ch) || ch=='%' ) {
                result.append(ch);
            } else {
                String s2= Integer.toHexString(ch);
                result.append( "%"+s2.substring(s2.length()-2) );
            }
        }
        return result.toString();
    }
    
    public void setConstraint( String c ) {
        if ( !c.startsWith("?") ) throw new IllegalArgumentException("constraint must start with question mark(?)");
        this.constraint= c;
    }
    
    private long getSizeForType( DArray v ) {
        PrimitiveVector pv= v.getPrimitiveVector();
        if ( pv instanceof Float32PrimitiveVector ) {
            return 4;
        } else {
            return 1;
        }
    }
    
    
    private long calcSize( ) throws MalformedURLException, IOException, ParseException {
        try {
            DDS dds= new DDS();
            dds.parse( new URL( this.getSource().toString()+".dds" + constraint ).openStream() );
            
            // calculate size
            Enumeration variables= dds.getVariables();
            long size= -1;
            while ( variables.hasMoreElements() ) {
                DArray v= (DArray) variables.nextElement();                
                Enumeration dimensions= v.getDimensions();
                long s1= getSizeForType(v);
                s1*= 2;   // not sure why
                while ( dimensions.hasMoreElements() ) {
                    DArrayDimension d= (DArrayDimension) dimensions.nextElement();
                    s1*= d.getSize();
                }
                size+= s1;
            }
            return size;
        } catch ( DDSException e ) {
            throw new RuntimeException(e);
        }
    }
    
    public void loadDataset( final DasProgressMonitor mon ) throws FileNotFoundException, MalformedURLException, IOException, ParseException, DDSException, DODSException {
        if ( constraint==null ) {
            throw new IllegalArgumentException("constraint not set");
        }
        
        long size= calcSize();
        mon.setTaskSize( size );
        
        DConnect url= new DConnect( source.toString(), true );
        StatusUI sui= new StatusUI() {
            long byteCount= 0;
            
            public void incrementByteCount(int bytes) {
                byteCount+= bytes;
                mon.setTaskProgress( byteCount );
            }
            
            public boolean userCancelled() {
                return mon.isCancelled();
            }
            
            public void finished() {
                mon.finished();
            }
            
        };
        
        mon.started();
        dds= url.getData( constraint, sui );
        
    }
    
    int sliceIndex=0;
    
    public void setSliceIndex( int index ) {
        this.sliceIndex= index;
    }
    
    public QDataSet getDataSet() {
        DodsVarDataSet zds;
        
        BaseType btvar;
        try {
            btvar = dds.getVariable(variable);
            String type= btvar.getTypeName();
            if ( type.equals("Grid" ) ) {
                DGrid zgrid= (DGrid)btvar;
                DArray z= (DArray) zgrid.getVar(0);
                
                zds= DodsVarDataSet.newDataSet( z, properties );
                zds.putProperty( QDataSet.UNITS, units );
                
                for ( int idim= 0; idim<z.numDimensions(); idim++ ) {
                    DArray t=  (DArray) zgrid.getVar(idim+1);
                    HashMap tprops= new HashMap();
                    tprops.put(QDataSet.UNITS, dimUnits[idim] );
                    if ( dimProperties[idim]!=null ) tprops.putAll( dimProperties[idim] );
                    DodsVarDataSet tds= DodsVarDataSet.newDataSet( t, tprops );
                    zds.putProperty( "DEPEND_"+idim, tds );
                }
                
            } else if ( type.equals("Array") ) {
                DArray z= (DArray) btvar;
                
                zds= DodsVarDataSet.newDataSet( z, properties );
                zds.putProperty( QDataSet.UNITS, units );
                
                for ( int idim= 0; idim<z.numDimensions(); idim++ ) {
                    if ( dependName[idim]!=null ) {
                        DArray t=  (DArray) dds.getVariable(dependName[idim]);
                        HashMap tprops= new HashMap();
                        tprops.put(QDataSet.UNITS, dimUnits[idim] );
                        if ( dimProperties[idim]!=null ) tprops.putAll( dimProperties[idim] );
                        DodsVarDataSet tds= DodsVarDataSet.newDataSet( t, tprops );
                        zds.putProperty( "DEPEND_"+idim, tds );
                    }
                }
                
            } else {
                
                throw new IllegalStateException("not supported dds type:"+type);
            }
        } catch (NoSuchVariableException ex) {
            throw new RuntimeException(ex);
        }
        
        QDataSet ds= zds;
        if ( zds.rank()==3 ) {
            DDataSet reduce= DDataSet.createRank2( zds.length(), zds.length(0) );
            for ( int i=0; i<zds.length(); i++ ) {
                for ( int j=0; j<zds.length(0); j++ ) {
                    reduce.putValue( i,j,zds.value(i,j,0) );
                }
            }
            ds= reduce;
        } else if ( zds.rank()==4 ) {
            DDataSet reduce= DDataSet.createRank2( zds.length(), zds.length(0) );
            for ( int i=0; i<zds.length(); i++ ) {
                for ( int j=0; j<zds.length(0); j++ ) {
                    reduce.putValue( i,j,zds.value(i,j,0,0) );
                }
            }
            ds= reduce;
        }

        
        return ds;
    }
    
    
    public edu.uiowa.physics.pw.das.dataset.DataSet getDas2DataSet() {
        QDataSet ds= getDataSet();
        if ( ds.rank()==3 ) {
            QDataSet sliceDs= DataSetOps.slice0( ds, sliceIndex );
            return TableDataSetAdapter.create(sliceDs);
        } else if ( ds.rank()==2 ) {
            return TableDataSetAdapter.create(ds);
        } else {
            return VectorDataSetAdapter.create(ds);
        }
        
    }
    
    /**
     * Holds value of property depend0Name.
     */
    private String depend0Name;
    
    /**
     * Getter for property depend0Name.
     * @return Value of property depend0Name.
     */
    public String getDepend0Name() {
        return this.depend0Name;
    }
    
    /**
     * Setter for property depend0Name.
     * @param depend0Name New value of property depend0Name.
     */
    public void setDepend0Name(String depend0Name) {
        this.depend0Name = depend0Name;
    }
    
    /**
     * Holds value of property depend1Name.
     */
    private String depend1Name;
    
    /**
     * Getter for property depend1Name.
     * @return Value of property depend1Name.
     */
    public String getDepend1Name() {
        return this.depend1Name;
    }
    
    /**
     * Setter for property depend1Name.
     * @param depend1Name New value of property depend1Name.
     */
    public void setDepend1Name(String depend1Name) {
        this.depend1Name = depend1Name;
    }
    
    /**
     * Holds value of property addOffset.
     */
    private double addOffset=0.0;
    
    /**
     * Getter for property addOffset.
     * @return Value of property addOffset.
     */
    public double getAddOffset() {
        return this.addOffset;
    }
    
    /**
     * Setter for property addOffset.
     * @param addOffset New value of property addOffset.
     */
    public void setAddOffset(double addOffset) {
        this.addOffset = addOffset;
        properties.put( "add_offset", new Double(addOffset) );
    }
    
    /**
     * Holds value of property scaleFactor.
     */
    private double scaleFactor=1.0;
    
    /**
     * Getter for property scaleFactor.
     * @return Value of property scaleFactor.
     */
    public double getScaleFactor() {
        return this.scaleFactor;
    }
    
    /**
     * Setter for property scaleFactor.
     * @param scaleFactor New value of property scaleFactor.
     */
    public void setScaleFactor(double scaleFactor) {
        this.scaleFactor = scaleFactor;
        properties.put( "scale_factor", new Double(scaleFactor) );
    }
    
    public void setValidRange( double min, double max ) {
        properties.put( "valid_range", ""+min+","+max );
    }
    
    /**
     * Holds value of property dimUnits.
     */
    private Units[] dimUnits= new Units[8];
    
    /**
     * Indexed getter for property dimUnits, which specifies the units of a dimension tag.
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
     */
    public Units getDimUnits(int index) {
        return this.dimUnits[index];
    }
    
    /**
     * Specifies the units of a dimension tag.
     * @param index Index of the property.
     * @param dimUnits New value of the property at <CODE>index</CODE>.
     */
    public void setDimUnits(int index, Units dimUnits) {
        this.dimUnits[index] = dimUnits;
    }
    
    public void putAllProperties( Map p ) {
        properties.putAll(p);
    }
    
    private HashMap[] dimProperties= new HashMap[8];
    
    public void setDimProperties( int dim, Map p ) {
        dimProperties[dim]= new HashMap(p);
    }
    
    public HashMap getDimProperties( int i ) {
        return dimProperties[i];
    }
    
    /**
     * das2 Unit object for the dataset.
     */
    private Units units;
    
    /**
     * Getter for property units.
     * @return Value of property units.
     */
    public Units getUnits() {
        return this.units;
    }
    
    /**
     * Setter for property units.
     * @param units New value of property units.
     */
    public void setUnits(Units units) {
        this.units = units;
    }
    
    /**
     * Holds value of property dependName.
     */
    private String[] dependName= new String[8];
    
    /**
     * Indexed getter for property dependName.
     * @param index Index of the property.
     * @return Value of the property at <CODE>index</CODE>.
     */
    public String getDependName(int index) {
        return this.dependName[index];
    }
    
    /**
     * Indexed setter for property dependName.
     * @param index Index of the property.
     * @param dependName New value of the property at <CODE>index</CODE>.
     */
    public void setDependName(int index, String dependName) {
        this.dependName[index] = dependName;
    }
    
    public URL getSource() {
        return this.source;
    }
    
    public String getVariable() {
        return this.variable;
    }
    
}

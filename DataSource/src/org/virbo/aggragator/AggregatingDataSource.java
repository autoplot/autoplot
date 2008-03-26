/*
 * AggregatingDataSource.java
 *
 * Created on October 25, 2007, 10:29 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.aggragator;

import edu.uiowa.physics.pw.das.dataset.CacheTag;
import edu.uiowa.physics.pw.das.datum.Datum;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.DatumRangeUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.SubTaskMonitor;
import org.das2.util.filesystem.FileStorageModel;
import org.das2.util.filesystem.FileSystem;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;
import org.virbo.datasource.capability.TimeSeriesBrowse;

/**
 *
 * http://www.papco.org:8080/opendap/cdf/polar/hyd_h0/%Y/po_h0_hyd_%Y%m%d_v...cdf.dds?ELECTRON_DIFFERENTIAL_ENERGY_FLUX
 * @author jbf
 */
public class AggregatingDataSource extends AbstractDataSource {
    
    private FileStorageModel fsm;
    DataSourceFactory delegateDataSourceFactory;
    
    /**
     * metadata from the last read.
     */
    TreeModel metadata;
    
    /** Creates a new instance of AggregatingDataSource */
    public AggregatingDataSource( URL url ) throws MalformedURLException, FileSystem.FileSystemOfflineException, IOException, ParseException {
        super(url);
        String surl= url.toString();
        delegateDataSourceFactory= AggregatingDataSourceFactory.getDelegateDataSourceFactory(surl);
        addCability( TimeSeriesBrowse.class, new TimeSeriesBrowse() {
            public void setTimeRange(DatumRange dr) {
                viewRange= dr;
            }
            
            public void setTimeResolution(Datum d) {
            }
            
        } );
    }
    
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        String[] ss= getFsm().getNamesFor(viewRange);
        
        DDataSet result=null;
        
        if ( ss.length>1 ) {
            mon.setTaskSize(ss.length*10);
            mon.started();
        }
        
        DatumRange cacheRange=null;
        
        for ( int i=0; i<ss.length; i++ ) {
            String scompUrl= getFsm().getFileSystem().getRootURL() + ss[i];
            if ( !params.equals("") ) scompUrl+= "?"+params;
            URL compUrl= new URL(  scompUrl );
            
            DataSource delegateDataSource= delegateDataSourceFactory.getDataSource(compUrl);
            
            ProgressMonitor mon1;
            if ( ss.length>1 ) {
                mon.setProgressMessage( "getting "+ss[i] );
                mon1= SubTaskMonitor.create(mon,i*10,10*(i+1));
            } else {
                mon1= mon;
            }
            
            QDataSet ds1= delegateDataSource.getDataSet( mon1 );
            
            DatumRange dr1= getFsm().getRangeFor( ss[i] );
            
            if ( result==null ) {
                result= DDataSet.copy( ds1 );
                this.metadata= delegateDataSource.getMetaData( new NullProgressMonitor() );
                cacheRange= dr1;
            } else {
                result.join( DDataSet.copy(ds1) );
                cacheRange= new DatumRange( cacheRange.min(), dr1.max() );
            }
            if ( ss.length>1 ) if ( mon.isCancelled() ) break;
        }
        
        if ( ss.length>1 ) mon.finished();
        
        DDataSet dep0= result==null ? null : (DDataSet) result.property(DDataSet.DEPEND_0);
        if ( dep0!=null ) {
            dep0.putProperty( DDataSet.CACHE_TAG, new CacheTag( cacheRange, null ) );
        }
                    
        return result;
        
    }
    
    /**
     * returns the metadata provided by the first delegate dataset.
     */
    public TreeModel getMetaData(ProgressMonitor mon) throws Exception {
        if ( metadata==null ) {
            TreeModel retValue;
            retValue = super.getMetaData(mon);
            return retValue;
        } else {
            return metadata;
        }
        
    }
    
    /**
     * Holds value of property viewRange.
     */
    private DatumRange viewRange= DatumRangeUtil.parseTimeRangeValid("2006-07-03 to 2006-07-05");
    
    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport =  new java.beans.PropertyChangeSupport(this);
    
    /**
     * Adds a PropertyChangeListener to the listener list.
     * @param l The listener to add.
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }
    
    /**
     * Removes a PropertyChangeListener from the listener list.
     * @param l The listener to remove.
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }
    
    /**
     * Getter for property viewRange.
     * @return Value of property viewRange.
     */
    public DatumRange getViewRange() {
        return this.viewRange;
    }
    
    /**
     * Setter for property viewRange.
     * @param viewRange New value of property viewRange.
     */
    public void setViewRange(DatumRange viewRange) {
        DatumRange oldViewRange = this.viewRange;
        this.viewRange = viewRange;
        propertyChangeSupport.firePropertyChange("viewRange", oldViewRange, viewRange);
    }
    
    public FileStorageModel getFsm() {
        return fsm;
    }
    
    public void setFsm(FileStorageModel fsm) {
        this.fsm = fsm;
    }
    
    /**
     * Holds value of property params.
     */
    private String params="";
    
    
    /**
     * Setter for property args.
     * @param args New value of property args.
     */
    public void setParams(String params) {
        String oldParams = this.params;
        this.params = params;
        propertyChangeSupport.firePropertyChange("args", oldParams, params);
    }

    public String getURL() {
        return super.getURL();
    }
 
    
    
}

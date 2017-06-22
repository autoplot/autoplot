/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.idlsupport;

import java.io.File;
import java.io.FileOutputStream;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSourceFormat;
import org.das2.qds.ops.Ops;

/**
 * Export to idlsav support.  rank 1 datasets, and rank 2 bundles are supported.
 * @author jbf
 */
public class IdlsavDataSourceFormat extends AbstractDataSourceFormat {

    private void doOne( WriteIDLSav write, QDataSet data, String guessName ) {

        String su= getParam( "tunits", "t1970" );

        QDataSet wds= Ops.valid(data);

        double[] dd= new double[data.length()];
        for ( int i=0; i<dd.length; i++ ) {
            dd[i]= wds.value(i)==0 ? Double.NaN : data.value(i);
        }

        Units dep0u= SemanticOps.getUnits(data);
        
        if ( UnitsUtil.isTimeLocation( dep0u ) ) {
            Units targetUnits= Units.lookupUnits(su.replaceAll("_"," ").replaceAll("\\+"," "));
            UnitsConverter uc= UnitsConverter.IDENTITY;
            if ( UnitsUtil.isTimeLocation(dep0u) ) {
                uc= UnitsConverter.getConverter(dep0u,targetUnits);
            }
            for ( int i=0; i<dd.length; i++ ) {
                dd[i]= uc.convert( data.value(i) );
            }
        }
        
        write.addVariable( Ops.guessName(data,guessName), dd );
         
    }
    private void formatRank2Bundle(  String uri, QDataSet data, ProgressMonitor mon ) throws Exception {
        setUri(uri);

        WriteIDLSav write= new WriteIDLSav();
        
        QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
        if ( dep0!=null ) {
            doOne( write,dep0,"dep0" );
        }
        
        for ( int i=0; i<data.length(0); i++ ) {
            QDataSet ds1= Ops.unbundle( data, i );
            doOne( write,ds1,"data"+i );
        }
        
        setUri(uri);

        File f= new File( getResourceURI().toURL().getFile() );
        FileOutputStream fos= new FileOutputStream(f);
        try {
            write.write( fos );
        } finally {
            fos.close();
        }        
        
    }
    
    public void formatData( String uri, QDataSet data, ProgressMonitor mon ) throws Exception {

        setUri(uri);

        if ( data.rank()!=1 ) {
            if ( SemanticOps.isBundle(data) ) {
                formatRank2Bundle( uri, data, mon );
                return;
            } else {
                throw new IllegalArgumentException("not supported, rank "+data.rank() );
            }
        }

        WriteIDLSav write= new WriteIDLSav();
        
        QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
        if ( dep0!=null ) {
            doOne( write,dep0,"dep0" );
        }
        
        doOne( write,data,"data" );
        
        QDataSet dep1= (QDataSet) data.property(QDataSet.DEPEND_1);
        if ( dep1!=null ) {
            doOne( write,dep1,"dep1" );
        }
        
        setUri(uri);

        File f= new File( getResourceURI().toURL().getFile() );
        FileOutputStream fos= new FileOutputStream(f);
        try {
            write.write( fos );
        } finally {
            fos.close();
        }

    }

    public boolean canFormat(QDataSet ds) {
        return ds.rank()==1 || ( ds.rank()==2 && SemanticOps.isBundle(ds) );
    }

    public String getDescription() {
        return "IDL Saveset";
    }

}

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
    
    private void formatRank2( WriteIDLSav write, QDataSet data, String guessName ) {

        String su= getParam( "tunits", "t1970" );

        QDataSet wds= Ops.valid(data);

        double[][] dd= new double[data.length()][];
        for ( int i=0; i<dd.length; i++ ) {
            dd[i]= new double[data.length(i)];
            for ( int j=0; j<data.length(i); j++ ) {
                dd[i][j]= wds.value(i,j)==0 ? Double.NaN : data.value(i,j);
            }
        }

        Units dep0u= SemanticOps.getUnits(data);
        
        if ( UnitsUtil.isTimeLocation( dep0u ) ) {
            Units targetUnits= Units.lookupUnits(su.replaceAll("_"," ").replaceAll("\\+"," "));
            UnitsConverter uc= UnitsConverter.IDENTITY;
            if ( UnitsUtil.isTimeLocation(dep0u) ) {
                uc= UnitsConverter.getConverter(dep0u,targetUnits);
            }
            for ( int i=0; i<dd.length; i++ ) {
                for ( int j=0; j<dd[i].length; j++ ) {
                    dd[i][j]= uc.convert( dd[i][j] );
                }
            }
        }
        
        write.addVariable( Ops.guessName(data,guessName), dd );
         
    }
    
    private void formatRank3( WriteIDLSav write, QDataSet data, String guessName ) {

        String su= getParam( "tunits", "t1970" );

        QDataSet wds= Ops.valid(data);

        double[][][] dd= new double[data.length()][][];
        for ( int i=0; i<dd.length; i++ ) {
            dd[i]= new double[data.length(i)][];
            for ( int j=0; j<data.length(i); j++ ) {
                dd[i][j]= new double[data.length(i,j)];
                for ( int k=0; k<data.length(i,j); k++ ) {
                    dd[i][j][k]= wds.value(i,j,k)==0 ? Double.NaN : data.value(i,j,k);
                }
            }
        }

        Units dep0u= SemanticOps.getUnits(data);
        
        if ( UnitsUtil.isTimeLocation( dep0u ) ) {
            Units targetUnits= Units.lookupUnits(su.replaceAll("_"," ").replaceAll("\\+"," "));
            UnitsConverter uc= UnitsConverter.IDENTITY;
            if ( UnitsUtil.isTimeLocation(dep0u) ) {
                uc= UnitsConverter.getConverter(dep0u,targetUnits);
            }
            for ( int i=0; i<dd.length; i++ ) {
                for ( int j=0; j<dd[i].length; j++ ) {
                    for ( int k=0; k<dd[i][j].length; k++ ) {
                        dd[i][j][k]= uc.convert( dd[i][j][k] );
                    }
                }
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

        if ( data.rank()!=1 && data.rank()!=2 && data.rank()!=3 ) {
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
        
        if ( data.rank()==2 ) {
            formatRank2(write, data, "data");
        } else if ( data.rank()==3 ) {
            formatRank3(write, data, "data");
        } else {
            doOne( write,data,"data" );
        }
        
        QDataSet dep1= (QDataSet) data.property(QDataSet.DEPEND_1);
        if ( dep1!=null ) {
            if ( dep1.rank()==2 ) {
                formatRank2(write, dep1, "dep1");
            } else {
                doOne( write,dep1,"dep1" );
            }
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
        return ds.rank()==1 || ds.rank()==2 || ds.rank()==3;
    }

    public String getDescription() {
        return "IDL Saveset";
    }

}

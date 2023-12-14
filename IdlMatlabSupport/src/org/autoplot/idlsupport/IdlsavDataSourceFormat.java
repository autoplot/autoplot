
package org.autoplot.idlsupport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSourceFormat;
import org.autoplot.datasource.URISplit;
import org.das2.qds.DataSetUtil;
import org.das2.qds.ops.Ops;

/**
 * Export to idlsav support.  rank 1 datasets, rank 2 datasets, rank 3 datasets, and rank 2 bundles are supported.
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
            for (double[] dd1 : dd) {
                for (int j = 0; j < dd1.length; j++) {
                    dd1[j] = uc.convert(dd1[j]);
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
            for (double[][] dd1 : dd) {
                for (double[] dd11 : dd1) {
                    for (int k = 0; k < dd11.length; k++) {
                        dd11[k] = uc.convert(dd11[k]);
                    }
                }
            }
        }
        
        write.addVariable( Ops.guessName(data,guessName), dd );
         
    }
    
    private void formatRank2Bundle( String uri, QDataSet data, WriteIDLSav write, ProgressMonitor mon ) throws Exception {
        setUri(uri);

        QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
        if ( dep0!=null ) {
            doOne( write,dep0,"dep0" );
        }
        
        for ( int i=0; i<data.length(0); i++ ) {
            QDataSet ds1= Ops.unbundle( data, i );
            doOne( write,ds1,"data"+i );
        }  
        
    }
    
    @Override
    public void formatData( String uri, QDataSet data, ProgressMonitor mon ) throws Exception {

        setUri(uri);
        maybeMkdirs();

        String append = getParam( "append", "F" );
        WriteIDLSav write= new WriteIDLSav();

        if ( append.equals("T") ) { 
            ReadIDLSav reader= new ReadIDLSav();
            File f= new File( getResourceURI().getPath() );
            if ( f.length()>Integer.MAX_VALUE ) {
                throw new IllegalArgumentException("Unable to read large IDLSav files");
            }
            ByteBuffer byteBuffer;
            try (FileChannel fc = new RandomAccessFile( f, "r" ).getChannel()) {
                byteBuffer = ByteBuffer.allocate((int) f.length());
                fc.read(byteBuffer);
            }
            String[] names= reader.readVarNames( byteBuffer );
            for ( String n: names ) {
                QDataSet v= IdlsavDataSource.getArray( reader, byteBuffer, n );
                doOne( write, v, n );
            }
        }
                
        if ( data.rank()!=1 && data.rank()!=2 && data.rank()!=3 ) {
            if ( append.equals("T") ) {
                throw new IllegalArgumentException("append does not work with bundle");
            }
            if ( SemanticOps.isBundle(data) ) {        
                formatRank2Bundle( uri, data, write, mon );
                File f= new File( getResourceURI().toURL().getFile() );
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    write.write( fos );
                }
            } else {
                throw new IllegalArgumentException("not supported, rank "+data.rank() );
            }
        } else {

            QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
            if ( dep0!=null ) {
                doOne( write,dep0,"dep0" );
            }

            switch (data.rank()) {
                case 2:
                    formatRank2(write, data, "data");
                    break;
                case 3:
                    formatRank3(write, data, "data");
                    break;
                default:
                    doOne( write,data,"data" );
                    break;
            }

            QDataSet dep1= (QDataSet) data.property(QDataSet.DEPEND_1);
            if ( dep1!=null ) {
                if ( dep1.rank()==2 ) {
                    formatRank2(write, dep1, "dep1");
                } else {
                    doOne( write,dep1,"dep1" );
                }
            }
        }
        
        setUri(uri);

        File f= new File( getResourceURI().toURL().getFile() );
        try (FileOutputStream fos = new FileOutputStream(f)) {
            write.write( fos );
        }

    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return DataSetUtil.isQube(ds) && ( ds.rank()==1 || ds.rank()==2 || ds.rank()==3 );
    }

    @Override
    public String getDescription() {
        return "IDL Saveset";
    }

}

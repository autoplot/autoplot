
package org.autoplot.idlsupport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSourceFormat;
import org.das2.qds.DataSetUtil;
import org.das2.qds.ops.Ops;

/**
 * Export to idlsav support.  rank 0, rank 1 datasets, rank 2 datasets, rank 3 datasets, and rank 2 bundles are supported.
 * @author jbf
 */
public class IdlsavDataSourceFormat extends AbstractDataSourceFormat {

    /**
     * Add the data to the container which will be written to an IDLSave file.
     * @param write the container
     * @param data the data
     * @param guessName the name to use, if name is not found within the data.
     */
    private void doOne( WriteIDLSav write, QDataSet data, String guessName ) {

        String su= getParam( "tunits", "t1970" );

        QDataSet wds= Ops.valid(data);

        Object odd;
        if ( data.rank()==0 ) {
            odd= data.value();
        } else if ( data.rank()==1 ) {
            double[] dd= new double[data.length()];
            for ( int i=0; i<dd.length; i++ ) {
                dd[i]= wds.value(i)==0 ? Double.NaN : data.value(i);
            }
            odd= dd;
        } else if ( data.rank()==2 ) {
            double[][] dd= new double[data.length()][];
            for ( int i=0; i<dd.length; i++ ) {
                dd[i]= new double[data.length(i)];
                for ( int j=0; j<data.length(i); j++ ) {
                    dd[i][j]= wds.value(i,j)==0 ? Double.NaN : data.value(i,j);
                }
            }
            odd= dd;
        } else if ( data.rank()==3 ) {
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
            odd= dd;
        } else if ( data.rank()==4 ) {
            double[][][][] dd= new double[data.length()][][][];
            for ( int i=0; i<dd.length; i++ ) {
                dd[i]= new double[data.length(i)][][];
                for ( int j=0; j<data.length(i); j++ ) {
                    dd[i][j]= new double[data.length(i,j)][];
                    for ( int k=0; k<data.length(i,j); k++ ) {
                        dd[i][j][k]= new double[data.length(i,j)];
                        for ( int l=0; l<data.length(i,j,k); l++ ) {
                            dd[i][j][k][l]= wds.value(i,j,k,l)==0 ? Double.NaN : data.value(i,j,k,l);
                        }
                    }
                }
            }
            odd= dd;        
        } else {
            throw new IllegalArgumentException("rank not supported");
        }

        Units units= SemanticOps.getUnits(data);
        
        if ( UnitsUtil.isTimeLocation( units ) ) {
            Units targetUnits= Units.lookupUnits(su.replaceAll("_"," ").replaceAll("\\+"," "));
            UnitsConverter uc= UnitsConverter.IDENTITY;
            if ( UnitsUtil.isTimeLocation(units) ) {
                uc= UnitsConverter.getConverter(units,targetUnits);
            }
            if ( data.rank()==0 ) {
                double d= (double)odd;
                odd= uc.convert( d );
            } else if ( data.rank()==1 ) {
                double[] dd= (double[])odd;
                for ( int i=0; i<dd.length; i++ ) {
                    dd[i]= uc.convert( data.value(i) );
                }
            } else {
                throw new IllegalArgumentException("Unable to format times which are not rank 0 or rank 1");
            }
        }
        
        write.addVariable( Ops.guessName(data,guessName), odd );
         
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
    
    private String incrementName( String n ) {
        if ( Character.isDigit( n.charAt(n.length()-1) ) ) {
            Pattern p= Pattern.compile("([a-zA-Z_])(d+)");
            Matcher m= p.matcher(n);
            if ( m.matches() ) {
                int d= Integer.parseInt( m.group(2) );
                return m.group(1)+String.valueOf(d);
            }
        }
        return n+"1";
    }
    
    @Override
    public void formatData( String uri, QDataSet data, ProgressMonitor mon ) throws Exception {

        setUri(uri);
        maybeMkdirs();

        String append = getParam( "append", "F" );
        WriteIDLSav write= new WriteIDLSav();

        String guessName= "data";
        
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
            while ( Arrays.asList(names).contains(guessName.toUpperCase()) ) {
                guessName= incrementName(guessName);
            }
            for ( String n: names ) {
                QDataSet v= IdlsavDataSource.getArray( reader, byteBuffer, n );
                doOne( write, v, n );
            }
        }
                
        if ( data.rank()!=1 && data.rank()!=2 && data.rank()!=3 ) {
            //TODO: I don't think this code is ever used.
            if ( SemanticOps.isBundle(data) ) {        
                formatRank2Bundle( uri, data, write, mon );
            } else {
                throw new IllegalArgumentException("not supported, rank "+data.rank() );
            }
        } else {

            QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
            if ( dep0!=null ) {
                doOne( write,dep0,"dep0" );
            }

            doOne( write,data,guessName );

            QDataSet dep1= (QDataSet) data.property(QDataSet.DEPEND_1);
            if ( dep1!=null ) {
                doOne( write,dep1,"dep1" );
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

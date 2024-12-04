
package org.autoplot.idlsupport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
        boolean isString = UnitsUtil.isNominalMeasurement( SemanticOps.getUnits(data) );
        
        if ( isString ) {
            throw new IllegalArgumentException("Nominal data is currently not supported");
        }
        
        if ( UnitsUtil.isNominalMeasurement( SemanticOps.getUnits(data) ) && data.rank()>1 ) {
            throw new IllegalArgumentException("Nominal data of rank greater than 1 is not supported");
        }
        Object odd;
        if ( data.rank()==0 ) {
            if ( isString ) {
                odd= data.svalue();
            } else {
                odd= data.value();
            }
        } else if ( data.rank()==1 ) {
            if ( isString ) {
                String[] ss= new String[data.length()];
                for ( int i=0; i<ss.length; i++ ) {
                    ss[i]= wds.value(i)==0 ? "" : data.slice(i).svalue();
                }
                odd= ss;
                
            } else {
                double[] dd= new double[data.length()];
                for ( int i=0; i<dd.length; i++ ) {
                    dd[i]= wds.value(i)==0 ? Double.NaN : data.value(i);
                }
                odd= dd;
            }
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
        
        String name = Ops.guessName(data,guessName);
        write.addVariable( name, odd );
         
    }
    
    
    private void formatRank2Bundle( String uri, QDataSet data, WriteIDLSav write, String[] names, ProgressMonitor mon ) throws Exception {
        setUri(uri);

        QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
        if ( dep0!=null ) {
            doOne( write,dep0,"dep0" );
        }
        
        for ( int i=0; i<data.length(0); i++ ) {
            QDataSet ds1= Ops.unbundle( data, i );
            String guessName= maybeIncrementName("data"+i,names);
            doOne( write,ds1,guessName );
        }  
        
    }
    
    /**
     * return a name which is unique from names.
     * @param n
     * @param names
     * @return 
     */
    private String maybeIncrementName( String n, String[] names ) {
        Set<String> nnames;
        if ( names==null ) {
            nnames= Collections.emptySet();
        } else {
            nnames= new HashSet<>(Arrays.asList(names));
        }
        if ( nnames.contains(n) ) {
            if ( Character.isDigit( n.charAt(n.length()-1) ) ) {
                Pattern p= Pattern.compile("([a-zA-Z_])(d+)");
                Matcher m= p.matcher(n);
                if ( m.matches() ) {
                    int d= Integer.parseInt( m.group(2) );
                    return m.group(1)+String.valueOf(d);
                }
            }
            return n+"1";
        } else {
            return n;
        }
    }
    
    @Override
    public void formatData( String uri, QDataSet data, ProgressMonitor mon ) throws Exception {

        setUri(uri);
        maybeMkdirs();

        String append = getParam( "append", "F" );
        WriteIDLSav write= new WriteIDLSav();

        String guessName= getParam( "arg_0", "DATA" );
        String[] names= new String[0];
        
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
            names= reader.readVarNames( byteBuffer );
            guessName= maybeIncrementName(guessName,names);
            for ( String n: names ) {
                QDataSet v= IdlsavDataSource.getArray( reader, byteBuffer, n );
                doOne( write, v, n );
            }
        }
                
        if ( data.rank()==2 &&  SemanticOps.isBundle(data) ) {
            formatRank2Bundle( uri, data, write, names, mon );

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

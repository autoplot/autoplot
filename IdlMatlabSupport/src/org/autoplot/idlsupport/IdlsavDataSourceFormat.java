
package org.autoplot.idlsupport;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import org.das2.datum.Units;
import org.das2.datum.UnitsConverter;
import org.das2.datum.UnitsUtil;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.AbstractDataSourceFormat;
import org.autoplot.datasource.DataSourceUtil;
import org.das2.qds.DataSetUtil;
import org.das2.qds.ops.Ops;
import org.das2.util.LoggerManager;

/**
 * Export to idlsav support.  rank 0, rank 1 datasets, rank 2 datasets, rank 3 datasets, and rank 2 bundles are supported.
 * @author jbf
 */
public class IdlsavDataSourceFormat extends AbstractDataSourceFormat {

    private static final Logger logger= LoggerManager.getLogger("apdss.format.idlsav");
    
    Map<String,QDataSet> namesFwd= new HashMap<>();
    Map<QDataSet,String> namesRev= new HashMap<>();

    
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
            doOne( write,dep0,DataSourceUtil.guessNameFor(namesRev, namesFwd, dep0) );
        }
        
        for ( int i=0; i<data.length(0); i++ ) {
            QDataSet ds1= Ops.unbundle( data, i );
            doOne( write,ds1,DataSourceUtil.guessNameFor(namesRev, namesFwd, data)  );
        }  
        
    }
    
    /**
     * return true if these timetags are already in the file as t1970 or cdf_tt2000.  Note that since
     * the timetags come off an existing idlsav file, we may have lost the units.
     * @return true if these timetags are already in the file as t1970 or cdf_tt2000.
     */
    private boolean haveTimeTags( QDataSet dep0 ) {
        boolean haveDep0=false;
        for ( Entry<QDataSet,String> e: namesRev.entrySet() ) {
            if ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(dep0) ) ) {
                QDataSet timeDs= Ops.putProperty( e.getKey(), QDataSet.UNITS, Units.t1970 );
                if ( Ops.equivalent( timeDs, Ops.convertUnitsTo( dep0, Units.t1970 ) ) ) {
                    haveDep0=true;
                    break;
                }
                if ( haveDep0==false ) {
                    timeDs= Ops.putProperty( e.getKey(), QDataSet.UNITS, Units.cdfTT2000 );
                    if ( Ops.equivalent( timeDs, Ops.convertUnitsTo( dep0, Units.cdfTT2000 ) ) ) {
                        haveDep0=true;
                        break;
                    }
                }
            }
        }
        return haveDep0;
    }
    
    @Override
    public void formatData( String uri, QDataSet data, ProgressMonitor mon ) throws Exception {

        setUri(uri);
        maybeMkdirs();

        String append = getParam( "append", "F" );
        WriteIDLSav write= new WriteIDLSav();

        String explicitName= getParam("name",getParam( "arg_0", "" ));
        
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
            for ( String n:names ) {
                //guessName= maybeIncrementName(guessName,names);
                QDataSet v= IdlsavDataSource.getArray( reader, byteBuffer, n );
                String nn= DataSourceUtil.guessNameFor( namesRev, namesFwd, v ); // n should not change
                doOne( write, v, nn );
            }
        }
                
        if ( data.rank()==2 &&  SemanticOps.isBundle(data) ) {
            formatRank2Bundle( uri, data, write, names, mon );

        } else {

            QDataSet dep0= (QDataSet) data.property(QDataSet.DEPEND_0);
            if ( dep0!=null ) {
                boolean haveDep0=false;
                if ( UnitsUtil.isTimeLocation( SemanticOps.getUnits(dep0) ) ) {
                    haveDep0= haveTimeTags(dep0);
                } else {
                    for ( Entry<QDataSet,String> e: namesRev.entrySet() ) {
                        if ( Ops.equivalent( e.getKey(), Ops.putProperty( dep0, QDataSet.UNITS, Units.dimensionless ) ) ) {
                            haveDep0=true;
                            break;
                        }
                    }
                }
                if ( haveDep0 ) {
                    logger.fine("assuming timetags variable exists already");
                } else {
                    String name= DataSourceUtil.guessNameFor( namesRev, namesFwd, dep0 );
                    doOne( write,dep0,name );
                }
            }
            String name= DataSourceUtil.guessNameFor( namesRev, namesFwd, data );
            if ( explicitName.length()>0 ) {
                name= explicitName;
            }
            doOne( write,data,name );

            QDataSet dep1= (QDataSet) data.property(QDataSet.DEPEND_1);
            if ( dep1!=null ) {
                boolean haveDep1=false;
                for ( Entry<QDataSet,String> e: namesRev.entrySet() ) {
                    if ( Ops.equivalent( e.getKey(), dep0 ) ) {
                        haveDep1=true;
                        break;
                    }
                }
                if ( haveDep1 ) {
                    String dep1Name= DataSourceUtil.guessNameFor( namesRev, namesFwd, dep1 );
                    doOne( write,dep1,dep1Name );
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

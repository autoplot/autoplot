
package org.autoplot.idlsupport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.datum.Units;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.IDataSet;
import org.das2.qds.LDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SDataSet;
import org.das2.qds.ops.Ops;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Read a variable from an IDLSav file.
 * @author jbf
 */
public class IdlsavDataSource extends AbstractDataSource {

    public IdlsavDataSource(URI uri) {
        super(uri);
    }

    public Object getFromStructure( Map v, String t ) {
        int i= t.indexOf('.');
        if ( i==-1 ) {
            return ((Map)v).get(t);
        } else {
            Map vc= (Map)(((Map)v).get(t.substring(0,i)));  // child of v
            return getFromStructure( vc, t.substring(i+1) );
        }
    }
    
    private QDataSet getArray( ReadIDLSav reader, ByteBuffer buffer, String arg ) throws IOException {
        Object v;
        
        int i= arg.indexOf('.');
        String t=arg;
        if ( i>-1 ) { // structure
            String h= t.substring(0,i);
            t= t.substring(i+1);
            v= reader.readVar( buffer, h );
            if ( !( v instanceof Map ) ) {
                throw new IllegalArgumentException("expected map for '"+h+"'");
            } else {
                v= getFromStructure( ((Map)v), t );
                if ( v==null ) throw new IllegalArgumentException("unable to find variable: "+arg);
            }
        } else {
            v= reader.readVar( buffer, arg );
        }
        
        if ( v==null ) {
            throw new IllegalArgumentException("unable to find variable or not supported: "+arg);
        }
        
        if ( v instanceof ReadIDLSav.ArrayData ) {
            ReadIDLSav.ArrayData arrayData= (ReadIDLSav.ArrayData)v;
            ArrayDataSet result= ArrayDataSet.wrap( arrayData.array, arrayData.dims, false );
            if ( result instanceof SDataSet || result instanceof IDataSet || result instanceof LDataSet ) {
                result.putProperty( QDataSet.FORMAT, "%d" );
            }
            return result;
        } else if ( v instanceof Map ) { 
            throw new IllegalArgumentException("Map is not supported, select one of its tags");
        } else {
            return Ops.dataset(v);
        }
    }
    
    private QDataSet handleDs( QDataSet array, String name ) {
        int[] qube= DataSetUtil.qubeDims(array);

        QDataSet result;
        switch (qube.length) {
            case 2:
                int t= qube[0];
                qube[0]= qube[1];
                qube[1]= t;

                result= array;
                if ( result.length(0)==6 && result.length()>0 ) {
                    double yr= result.value(0,0);
                    if ( Math.floor(yr)==yr && yr>1900 && yr<2200 ) {
                        result= Ops.toTimeDataSet( Ops.slice1(result,0),
                                Ops.slice1(result,1), 
                                Ops.slice1(result,2),
                                Ops.slice1(result,3),
                                Ops.slice1(result,4),
                                Ops.slice1(result,5), null );
                    }
                }
                break;
            default:
                result= array;
                break;
        }
        result= Ops.putProperty( result, QDataSet.NAME, name.replaceAll("\\.","_") );
        result= Ops.putProperty( result, QDataSet.LABEL, name );
        return result;
    }
    
    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        File f= getFile( uri, mon );
        ReadIDLSav reader= new ReadIDLSav();
        
        RandomAccessFile aFile = new RandomAccessFile(f,"r");
        FileChannel inChannel = aFile.getChannel();
        long fileSize = inChannel.size();
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        int bytesRead= 0;
        while ( bytesRead<fileSize ) {
            bytesRead+= inChannel.read(buffer);
        }
        
        String x= getParam("X","");
        String y= getParam("Y","");
        String z= getParam("Z","");
        
        String name = getParam( "arg_0", "" );
        if ( name.length()==0 ) {
            if ( x.length()==0 && y.length()==0 && z.length()==0 ) {
                throw new IllegalArgumentException("name or X must be set");
            }
        }
        
        QDataSet[] datas=null;

        String[] names;
        
        if ( name.length()==0 ) {
            if ( z.length()>0 ) {
                datas = new QDataSet[3];
                QDataSet array= getArray( reader, buffer, z );
                array= handleDs( array, z );
                datas[2]= array;
            }
            if ( y.length()>0 ) {
                if ( datas==null ) datas= new QDataSet[2];
                QDataSet array= getArray( reader, buffer, y );
                array= handleDs( array, y );
                datas[1]= array;
            }
            if ( x.length()>0 ) {
                if ( datas==null ) datas= new QDataSet[1];
                QDataSet array= getArray( reader, buffer, x );
                array= handleDs( array, x );
                datas[0]= array;
            }
            
        } else {
            names = name.split(",");
            datas = new QDataSet[names.length];
            if ( names.length>4 ) {
                throw new IllegalArgumentException("first argument can only"
                        + " contain four comma-separated names." );
            }

            for ( int i=0; i<names.length; i++ ) {
                QDataSet array= getArray( reader, buffer, names[i]);
                array= handleDs( array, names[i] );
                datas[i]= array;

            }
        }
        
        String sxunits= getParam("xunits","");
        if ( sxunits.length()>0 ) {
            Units xunits= Units.lookupUnits(sxunits);
            if ( datas.length>0 ) {
                datas[0]= Ops.putProperty( datas[0], QDataSet.UNITS, xunits );
            }
        }

        String syunits= getParam("yunits", "" );
        if ( syunits.length()>0 ) {
            Units yunits= Units.lookupUnits(syunits);
            int ids= 1;
            datas[ids]= Ops.putProperty( datas[ids], QDataSet.UNITS, yunits );
        }
        
        String sunits= getParam("units", "" );
        if ( sunits.length()>0 ) {
            Units units= Units.lookupUnits(sunits);
            int ids= datas.length-1;
            datas[ids]= Ops.putProperty( datas[ids], QDataSet.UNITS, units );
        }
                
        switch( datas.length ) {
            case 1: {
                return datas[0];
            }
            case 2: {
                return Ops.link(datas[0],datas[1]);
            }
            case 3: {
                if ( datas[2].length()!=datas[0].length() ) { // automatically transpose, since this happens with IDL often.
                    if ( datas[2].rank()==2 ) {
                        if ( datas[2].length(0)==datas[0].length() ) {
                            datas[2]= Ops.transpose(datas[2]);
                        }
                    }
                }
                return Ops.link(datas[0],datas[1],datas[2]);
            }
            case 4: {
                return Ops.link(datas[0],datas[1],datas[2],datas[3]);
            }
            default: throw new IllegalArgumentException("not supported");
        }
    }
    
}

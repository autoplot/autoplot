
package org.autoplot.idlsupport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.QDataSet;
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
            return ArrayDataSet.wrap( arrayData.array, arrayData.dims, false );
        } else if ( v instanceof Map ) { 
            throw new IllegalArgumentException("Map is not supported, select one of its tags");
        } else {
            return Ops.dataset(v);
        }
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
        
        String name = getParam( "arg_0", "" );
        if ( name.length()==0 ) {
            throw new IllegalArgumentException("name must be set");
        }
        
        String[] names= name.split(",");
        QDataSet[] datas= new QDataSet[names.length];
        if ( names.length>4 ) {
            throw new IllegalArgumentException("first argument can only"
                    + " contain four comma-separated names." );
        }
        
        for ( int i=0; i<names.length; i++ ) {
            QDataSet array= getArray( reader, buffer, names[i]);

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
            result= Ops.putProperty( result, QDataSet.NAME, names[i].replaceAll("\\.","_") );
            result= Ops.putProperty( result, QDataSet.LABEL, names[i] );
            datas[i]= result;
            
        }
        
        switch( names.length ) {
            case 1: return datas[0];
            case 2: return Ops.link(datas[0],datas[1]);
            case 3: return Ops.link(datas[0],datas[1],datas[2]);
            case 4: return Ops.link(datas[0],datas[1],datas[2],datas[3]);
            default: throw new IllegalArgumentException("not supported");
        }
    }
    
}

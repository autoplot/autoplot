/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.idlsupport;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.qds.ArrayDataSet;
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

    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        File f= getFile(mon);
        
        RandomAccessFile aFile = new RandomAccessFile(f,"r");
        FileChannel inChannel = aFile.getChannel();
        long fileSize = inChannel.size();
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        int bytesRead= 0;
        while ( bytesRead<fileSize ) {
            bytesRead+= inChannel.read(buffer);
        }
       
        String arg= getParam( "arg_0", "" );
        if ( arg.length()==0 ) {
            throw new IllegalArgumentException("specify a variable");
        }

        ReadIDLSav reader= new ReadIDLSav();
        Object v= reader.readVar( buffer, arg );        
        
        if ( v==null ) {
            throw new IllegalArgumentException("unable to find variable: "+arg);
        }
        
        if ( v.getClass().isArray() ) {
            ReadIDLSav.ArrayDesc meta= reader.readArrayDesc( buffer, arg );
            return ArrayDataSet.wrap( v, meta.dims, false );
        } else {
            return Ops.dataset(v);
        }
        
    }
    
}

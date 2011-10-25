/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.binarydatasource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Map;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;

/**
 * Format data to binary file.
 * @author jbf
 */
public class BinaryDataSourceFormat implements DataSourceFormat {

    /**
     * copy the dataset into a DoubleBuffer by wrapping a DoubleBuffer with
     * a DoubleBufferDataSet, then copy the data into the dataset.
     * @param data
     * @param mon
     * @return byteBuffer view of the dataset.
     */
    private ByteBuffer formatRank2( QDataSet data, ProgressMonitor mon, Map<String,String> params ) {

        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
        
        //dep1 is ignored.
        //QDataSet dep1 = (QDataSet) data.property(QDataSet.DEPEND_1);
        
        String type= params.get("type");
        if ( type==null ) type= "double";
        
        int dep0Len= ( dep0==null ? 0 : 1 );
        int typeSize= BufferDataSet.byteCount(type);
        
        int recSize=  typeSize * ( dep0Len + data.length(0) );
        int size= data.length() * recSize;
        
        ByteBuffer result= ByteBuffer.allocate(size);
        result.order( "big".equals( params.get("byteOrder") ) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN );
        
        BufferDataSet ddata= BufferDataSet.makeDataSet( 2, recSize, dep0Len * typeSize, 
                data.length(), data.length(0), 1, 1,
                result, type );
        /*Double ddata= new Double( 2, 
                recSize, dep0Len * typeSize, 
                data.length(), data.length(0), 1, 
                result );*/
        
        QubeDataSetIterator it= new QubeDataSetIterator(data);
        
        while ( it.hasNext() ) {
            it.next();
            it.putValue( ddata, it.getValue(data) );
        }

        if ( dep0!=null ) {
            BufferDataSet ddep0= BufferDataSet.makeDataSet( 1,
                recSize, 0 * typeSize, 
                data.length(), data.length(0), 1, 1,
                result, type );
          /*  Double ddep0= new Double( 1,
                recSize, 0 * typeSize, 
                data.length(), data.length(0), 1,
                result ); */
            it= new QubeDataSetIterator(dep0);
        
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ddep0, it.getValue(dep0) );
            }
        }
        
        return result;
        
    }

    private ByteBuffer formatRank1( QDataSet data, ProgressMonitor mon, Map<String,String> params ) {
        
        QDataSet dep0 = (QDataSet) data.property(QDataSet.DEPEND_0);
                
        String type= params.get("type");
        if ( type==null ) type= "double";
                
        int dep0Len= ( dep0==null ? 0 : 1 );
        int typeSize= BufferDataSet.byteCount(type);
        int recSize=  typeSize * ( dep0Len + 1 );
        int size= data.length() * recSize ;
        
        ByteBuffer result= ByteBuffer.allocate(size);
        result.order( "big".equals( params.get("byteOrder") ) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN );
        
        BufferDataSet ddata= BufferDataSet.makeDataSet( 1, 
                recSize, dep0Len*typeSize, 
                data.length(), 1, 1, 1,
                result, type );
        
        QubeDataSetIterator it= new QubeDataSetIterator(data);
        
        while ( it.hasNext() ) {
            it.next();
            it.putValue( ddata, it.getValue(data) );
        }

        if ( dep0!=null ) {
            BufferDataSet ddep0= BufferDataSet.makeDataSet( 1,
                recSize, 0*typeSize, 
                data.length(), 1, 1, 1, 
                result, type );
            it= new QubeDataSetIterator(dep0);
        
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ddep0, it.getValue(dep0) );
            }
        }
        
        return result;
    }

    public void formatData( String uri, QDataSet data, ProgressMonitor mon) throws IOException {
        
        URISplit split= URISplit.parse(uri);
        java.util.Map<String,String> params= URISplit.parseParams(split.params);

        ByteBuffer result;
        if (data.rank() == 2) {
            result= formatRank2( data, mon, params );
        } else if (data.rank() == 1) {
            result= formatRank1( data, mon, params );
        } else {
            throw new IllegalArgumentException("rank not supported");
        }
        
        WritableByteChannel channel= Channels.newChannel( new FileOutputStream( new File( split.resourceUri ) ) );
        channel.write(result);
        
        channel.close();
        
    }


}

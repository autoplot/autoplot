/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.binarydatasource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Map;
import org.das2.qds.buffer.BufferDataSet;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.das2.qds.SemanticOps;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.DataSourceFormat;

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
        
        String dep0Type= params.get("depend0Type" );
        if ( dep0Type==null ) dep0Type= "double";
        
        int dep0Len= ( dep0==null ? 0 : 1 );
        int typeSize= BufferDataSet.byteCount(type);
        
        int dep0TypeSize= BufferDataSet.byteCount(dep0Type);
        int recSize=  dep0Len*dep0TypeSize + data.length(0) * typeSize;
        
        int size= data.length() * recSize;
        
        ByteBuffer result= ByteBuffer.allocate(size);
        result.order( "big".equals( params.get("byteOrder") ) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN );
        
        BufferDataSet ddata= BufferDataSet.makeDataSet( 2, recSize, dep0Len * dep0TypeSize, 
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
                recSize, 0, 
                data.length(), data.length(0), 1, 1,
                result, dep0Type );

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
                
        String dep0Type= params.get("depend0Type" );
        if ( dep0Type==null ) dep0Type= "double";
        
        int dep0Len= ( dep0==null ? 0 : 1 );
        int typeSize= BufferDataSet.byteCount(type);
        int dep0TypeSize= BufferDataSet.byteCount(dep0Type);
        int recSize=  dep0Len*dep0TypeSize + typeSize;
        int size= data.length() * recSize ;
        
        ByteBuffer result= ByteBuffer.allocate(size);
        result.order( "big".equals( params.get("byteOrder") ) ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN );
        
        BufferDataSet ddata= BufferDataSet.makeDataSet( 1, 
                recSize, dep0Len*dep0TypeSize, 
                data.length(), 1, 1, 1,
                result, type );
        
        QubeDataSetIterator it= new QubeDataSetIterator(data);
        
        while ( it.hasNext() ) {
            it.next();
            it.putValue( ddata, it.getValue(data) );
        }

        if ( dep0!=null ) {
            BufferDataSet ddep0= BufferDataSet.makeDataSet( 1,
                recSize, 0, 
                data.length(), 1, 1, 1, 
                result, dep0Type );
            it= new QubeDataSetIterator(dep0);
        
            while ( it.hasNext() ) {
                it.next();
                it.putValue( ddep0, it.getValue(dep0) );
            }
        }
        
        return result;
    }

    @Override
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

    @Override
    public boolean canFormat(QDataSet ds) {
        return ! ( ds.rank()==0  || SemanticOps.isJoin(ds) );
    }

    @Override
    public String getDescription() {
        return "Binary Table";
    }

}
